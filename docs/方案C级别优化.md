# 方案 C 级别优化（C2 / C3 / C4 / C5）

| 项 | 内容 |
|----|------|
| 来源 | `docs/mq/整体优化方案2.md` §7 瓶颈排序、§8 PR-C 路线；承接已落地的 **C1** |
| 范围 | 一次规划并落地 **C2～C5**（calc 有效吞吐 + 稳定性） |
| 模块 | `pay-calc` / `pay-domain` / `pay-mq` / `pay-split` / 压测脚本 |
| 原则 | 短事务保留；强分片键（merchantId）；终态早 ACK；锁顺序统一；单消息内减 SQL |
| 前置 | **C1 已落地**：finalize / fail 走 `ClearanceClaimContext` + merchantId CAS，无 `No value present` |

---

## 0. 总览

| 编号 | 主题 | 优先级 | 现状（`20260808bugfix`） | 预期收益 |
|------|------|--------|-------------------------|----------|
| **C2** | 脏 MQ / DEAD 隔离 | P0 | ✅ 已落地 | skip 空转 ↓90%+，有效 TPS 可读 |
| **C3** | finalize 单 SQL 收敛 | P1 | ✅ 已落地 | finalize/fail −10～30ms，路径更纯 |
| **C4** | 死锁治理（锁序 + 重试） | P1 | ✅ 已落地 | 死锁告警 ↓，偶发 fail 不放大 |
| **C5** | fee_split 耗时压缩 | P1 | ✅ 已落地 | fee_split −20～40%，理论上限抬升 |

```text
有效清算 TPS ≈ (calc 线程 × 连接利用率) / (claim + fee_split + finalize)
                 × (1 − skip/fail 占比)

C1 修正确性；C2 压 skip；C3/C5 压 RT；C4 压偶发死锁 → 目标有效 20~40 单/s（方案 2 量级）
```

**非目标（本方案不展开）：** 跨消息 Batch Listener、盲目加 calc 线程（属 PR-D3，放在 C2～C5 验收后再压）、连接池再扩（方案 1/PR-B 已对齐）。

---

## 1. C2 — 脏 MQ / DEAD 隔离

### 1.1 问题解释

| 现象 | 说明 |
|------|------|
| 监控 | 方案 2：MQ ≈53 msg/s，成功 ≈2 单/s；skip+fail 占 consume **96%+** |
| 根因 | 历史重试 / 重复投递消息对应任务已 **SUCCESS/DEAD**，消费线程仍进 claim 或空转 |
| 叠加 | 与 C1 未修时叠加 →「消费很快、成功很少」；C1 修好后脏消息仍淹没指标 |

### 1.2 优化目标

1. 压测 / drain **前** 可一键 `resetOffsetByTime`，丢掉历史脏积压（可配置、默认可关）
2. 消费端：SUCCESS/DEAD **claim 前** 快速 skip + ACK（已有雏形，需补齐 DEAD→不可重试语义）
3. DEAD 任务：Listener 侧对明确终态抛 **`NonRetryableException`**（或等价 ACK），避免 RocketMQ L1 再空转 16 次
4. DEAD 存量：提供隔离/归档脚本或运营重放入口说明，避免 Retry Job 与 MQ 双通道重复

### 1.3 推荐方案

```text
压测准备：
  mqadmin resetOffsetByTime -g pay-calc-consumer -t clearance_task_topic -s now
  （access / settle 同源，见 scripts/run-c1-loadtest.sh）

消费热路径：
  resolveMerchantId
  → findStatus(billNo, merchantId)
       SUCCESS → skip + ACK
       DEAD    → skip + ACK（或 NonRetryable，禁止再投递）
  → claim …

存量 DEAD：
  运维脚本：按分片统计 / 可选归档；人工重放走 exception_record / 运营台
```

| 步骤 | 做法 |
|------|------|
| C2-a | 抽离 `scripts/reset-mq-offsets.sh`；C1/联合压测脚本统一调用 |
| C2-b | 强化 `shouldSkipTerminalTask`：指标 `pay_calc_clearance_total{skip,reason=terminal}` |
| C2-c | `handleUnclaimed`：DEAD 明确走 NonRetryable 或 ACK 策略文档化；禁止再进 L1 |
| C2-d | DEAD 存量巡检 SQL + 文档；与 `ClearanceRetryJob` 边界写清（只扫 FAILED） |

### 1.4 验收

| 项 | 通过条件 |
|----|----------|
| 干净压测 | reset 后 skip 占比显著下降；`DELTA_SUCCESS` ≈ producer 量级（费率种子齐全时） |
| 日志 | DEAD/SUCCESS 重投不触发 claim 失败风暴 |
| MQ | DEAD 消息不再消耗满额 L1 重试 |

---

## 2. C3 — finalize / fail 单 SQL 收敛

### 2.1 问题解释

方案 2 P1：`finalize` 二次 `findByBillNo` + 两次 save 拉长持连。  
**C1 已消除 finalize 主路径的 find+save**，改为：

- `updateStatusByBillNoAndMerchantId`（bill CLEARING→CLEARED）
- `markSuccess(..., merchantId)`（task RUNNING→SUCCESS）

余量仍在：

| 位置 | 现状 | 问题 |
|------|------|------|
| `failRunningTask` | 先 `findByBillNoAndMerchantId` 取 `retryCount` 再 `markFailed` | 多一次读；可并入 markFailed 返回值或 SQL 内增 retry |
| `claimAndMarkClearing` | claim 后 `find` bill 再 CAS 状态 | 必要读 1 次可接受；避免 bill CAS miss 后再 find |
| 其它旁路 | watchdog / activateWaitingRefunds | 审计是否残留 find+改内存+save |

### 2.2 优化目标

1. fail / watchdog：**零「为改状态而 find 整行」**（retry 计数由 CAS SQL 原子 +1）
2. finalize 保持 C1 双 CAS，不再引入实体 save
3. 静态检查：清算成功/失败主路径无 `save(entity)` 改状态

### 2.3 推荐方案

```text
markFailed SQL（示意）：
  UPDATE clearance_task
     SET retry_count = retry_count + 1,
         status = IF(retry_count + 1 >= #{maxRetry}, DEAD, FAILED),
         ...
   WHERE bill_no=? AND merchant_id=? AND status=RUNNING

返回：updated + 是否 enteredDead（可用 CASE 或二次轻量查询仅当 updated=1）
```

| 步骤 | 做法 |
|------|------|
| C3-a | `markFailed` 原子递增 retry；去掉 fail 前 find |
| C3-b | claim：保留「claim → 读 bill → CAS」；CAS miss 且已 CLEARING 时复用已读实体，禁止第三跳 |
| C3-c | 单测扩展：fail 路径不调用 `findByBillNo*`（或仅允许 markFailed 内部） |

### 2.4 验收

| 项 | 通过条件 |
|----|----------|
| 耗时 | Micrometer `finalize` / `fail` 均值较基线下降（目标 finalize 再 −10～30ms） |
| 代码 | 主路径无「find → setStatus → save」 |
| 回归 | C1 验收集仍绿；CAS miss 语义不变 |

---

## 3. C4 — 死锁治理

### 3.1 问题解释

方案 2：MySQL 死锁约 **64 次**，集中在 `claimAndMarkClearing`（`clearance_task` + `trade_bill` 并发更新）。

当前锁序（已较好）：

```text
1) claimTask：task PENDING→RUNNING
2) find bill
3) update bill PENDING→CLEARING
```

死锁仍可能来自：对侧路径先锁 bill 后锁 task、索引间隙锁、或 fail/finalize 与 claim 交叉。

### 3.2 优化目标

1. **全库清算写路径统一锁序：先 task，后 bill**（claim / fail / finalize / watchdog 一致）
2. 捕获死锁（`DeadlockLoserDataAccessException` / MySQL 1213）→ **有限次退避重试**（仅 claim 等幂等段）
3. 指标：`pay_calc_deadlock_total` + 日志采样，便于与业务 fail 区分

### 3.3 推荐方案

| 步骤 | 做法 |
|------|------|
| C4-a | 审计：`failRunningTask` 已是先 task `markFailed` 后 bill；finalize 先 bill 后 task → **改为先 task 后 bill**，与 claim 对齐 |
| C4-b | claim 外包 1～2 次死锁重试（指数退避 5～20ms）；耗尽再记 fail |
| C4-c | 确认索引：`claim` / `markSuccess` / `markFailed` 均走 `(merchant_id, bill_no)` 或等价唯一键，避免宽锁 |
| C4-d | 单测：mock 死锁一次后成功；指标计数 +1 |

**注意：** finalize 若改为先 task SUCCESS 后 bill CLEARED，需保证中断时补偿可发现「task 成功 bill 未清」——或保持短事务 + 顺序与 claim 一致并依赖事务回滚。推荐：**同一 `@DSTransactional` 内先 task 后 bill，失败整单回滚**。

### 3.4 验收

| 项 | 通过条件 |
|----|----------|
| 压测 | 同 TPS 下死锁日志 / 1213 次数显著下降 |
| 正确性 | 无「task SUCCESS 且 bill 非 CLEARED」脏状态（或补偿 Job 可修且可观测） |
| 指标 | deadlock 计数可在 Prometheus 查询 |

---

## 4. C5 — fee_split 耗时压缩

### 4.1 问题解释

方案 2：`fee_split` 均值 **94～184ms**，约占成功路径 **45%**。内含规则匹配、分账明细、凭证、outbox。  
已有基础：PR-A 真批量 INSERT、PR-E fee+split 合并短事务、规则 Redis 缓存。

### 4.2 优化目标

1. 单消息内 **再减往返**：split_detail / voucher / outbox 能批则批；避免 N+1
2. 热路径少读：关系 / 规则命中缓存；禁止在 fee_split 事务内打 Redis 分布式锁（P0-4 已禁 TX 内锁）
3. 可观测：`STAGE_FEE_SPLIT` 分位下降 20%+（相对同机基线）

### 4.3 推荐方案

| 步骤 | 做法 |
|------|------|
| C5-a | 审计 `SplitServiceImpl.generateSplitDetail`：确认 `insertBatch` 覆盖 detail+voucher；outbox 单行保持单插 |
| C5-b | 幂等短路：已存在 fee/split 时跳过重算，仅补缺失 outbox（`ensureOutboxIfNeeded` 已有则复用） |
| C5-c | 计费结果：同 billNo 二次进入直接读已落库结果，不重复算费 |
| C5-d | 可选：fee 与 split 若仍分事务则保持合并（现状 `runFeeAndSplit`）；不回退成长事务包裹 claim+finalize |

### 4.4 验收

| 项 | 通过条件 |
|----|----------|
| 耗时 | `pay_calc_stage_duration{stage=fee_split}` p50/p99 下降 |
| SQL | 单次清算 split/voucher 语句数不随分润方线性「逐条 insert」 |
| 正确性 | 分账金额、outbox 投递与改造前一致（对照单测 + 抽样对账） |

---

## 5. 一次落地实施切片（建议顺序）

```text
C2-a/b（去脏可测） → C3-a（fail 单 SQL） → C4-a/b（锁序+死锁重试）
    → C5-a/b（fee_split 审计与批写补齐） → C2-c/d + C3-c + C4-c/d + C5-c 测试
    → 联合压测（scripts/run-c1-loadtest.sh 或升级为 run-c-level-loadtest.sh）
```

| 切片 | 内容 | 依赖 |
|------|------|------|
| 1 | C2 offset 脚本 + skip 指标 | 无 |
| 2 | C3 markFailed 原子 retry | 无 |
| 3 | C4 finalize 锁序对齐 + claim 死锁重试 | C1 |
| 4 | C5 split 批写/幂等审计补齐 | PR-A/E |
| 5 | 验收单测 + 压测报告附录 | 1～4 |

建议提交：每切片 1～2 个 commit；最后补 `docs/方案C级别优化.md` 落地记录表。

---

## 6. 联合验收（压测）

| 项 | 通过条件 |
|----|----------|
| C1 回归 | `NO_VALUE_PRESENT_COUNT=0` |
| C2 | 干净环境 skip/consume &lt; 阈值阈值；脏积压 reset 后有效 SUCCESS 接近发送量 |
| C3/C5 | claim / fee_split / finalize 阶段耗时优于方案 2 基线 |
| C4 | 死锁次数接近 0 或仅个位数且可自愈 |
| 吞吐 | 有效清算 TPS 进入 **20～40/s** 量级（线程=12、费率种子齐全、无历史脏 MQ） |

复现参考：

```bash
bash scripts/seed-loadtest-merchants.sh
bash scripts/seed-loadtest-merchants-003.sh   # 含 fee_share_rule
DURATION=180 TPS=30 RESULT_DIR=/tmp/c-level-loadtest \
  ./scripts/run-c1-loadtest.sh
```

---

## 7. 风险与回滚

| 风险 | 缓解 |
|------|------|
| resetOffset 误伤未消费合法消息 | 仅压测/运维窗口；生产需审批与积压确认 |
| finalize 改锁序引入中间态 | 同事务回滚；加巡检 SQL |
| 死锁重试掩盖慢 SQL | 重试次数上限 + deadlock 指标告警 |
| 批写漏字段 / 时区 | 单测覆盖；`createTime` 业务侧赋值 |

回滚：按切片 revert；C2 脚本与消费 skip 可开关；C4 重试可配置关闭。

---

## 8. 落地记录

| 切片 | 状态 | 说明 |
|------|------|------|
| C1 | ✅ 已落地 | 见 `docs/问题C1修复.md` |
| C2-a | ✅ 已落地 | `scripts/reset-mq-offsets.sh`；`run-c1-loadtest.sh` 调用 |
| C2-b | ✅ 已落地 | `recordSkip(reason=terminal\|unclaimed)` |
| C2-c | ✅ 已落地 | DEAD → `NonRetryableException`（Classifier ACK）；SUCCESS skip ACK |
| C2-d | ✅ 已落地 | `scripts/inspect-dead-clearance-tasks.sh`；RetryJob 注释明确只扫 FAILED |
| C3-a | ✅ 已落地 | `markFailed` SQL 内原子 retry + 退避；fail 前不再 find 整行 |
| C3-b | ✅ 已落地 | claim CAS miss 最多再读一次并回填已读实体 |
| C3-c | ✅ 已落地 | `ClearanceWatchdogP0AcceptanceTest` 等：fail 不预读实体 |
| C4-a | ✅ 已落地 | `finalizeSuccess` 先 task 后 bill |
| C4-b | ✅ 已落地 | `claimWithDeadlockRetry` 最多 3 次，退避 5/10/20ms |
| C4-c | ✅ 已落地 | claim/mark* 均带 merchant_id + bill_no |
| C4-d | ✅ 已落地 | `pay_calc_deadlock_total`；`ClearanceC2C4AcceptanceTest` |
| C5-a/b | ✅ 已落地 | split 幂等补 voucher+outbox；仍走 `insertBatch` |
| C5-c | ✅ 已落地 | fee 入口已按 billNo 短路；去掉 calc 内多余二次 find |

---

## 9. 参考

- `docs/mq/整体优化方案2.md` §7～§8
- `docs/问题C1修复.md`
- `docs/mq/整体优化方案批量写入.md`（PR-A/B）
- `docs/mq/mq多线程.md` §12.2 三层重试 / DEAD
- `pay-calc/.../ClearanceTaskServiceImpl.java`（终态 skip / 死锁重试）
- `pay-calc/.../ClearanceTaskTxSupport.java`（claim / fee_split / finalize / fail）
- `scripts/reset-mq-offsets.sh` / `scripts/inspect-dead-clearance-tasks.sh` / `scripts/run-c1-loadtest.sh`

---

*文档版本：2026-08-09 · 方案 C 级别（C2～C5）· 已落地*
