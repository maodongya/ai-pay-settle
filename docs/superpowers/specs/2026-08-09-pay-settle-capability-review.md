# ai-pay-settle 能力梳理与代码 Review

| 项 | 内容 |
|----|------|
| 日期 | 2026-08-09 |
| 分支 | `20260808bugfix`（HEAD：`3582306`） |
| 方法 | Superpowers：分域并行只读审查 → 合成规格 |
| 范围 | 只读梳理与评审；**不包含**业务代码改动 |
| 读者 | 架构、后端、联调与评审 |
| 审查域 | access · calc/fee/split · settlement · mq/common · control/docs/ops |

---

## 1. 结论摘要

`ai-pay-settle` 已形成完整清算结算主链路：**接入 → 清算编排 → 计费 → 分账/Outbox → 结算出款**，并具备 MQ、分片、Redis 策略、分层限流与 Prometheus/Grafana 骨架。本分支已落地 **P0（Watchdog/补偿/回调 CAS/锁策略）** 与 **C1～C5（finalize 路由、脏 MQ 隔离、fail 单 SQL、死锁重试、fee_split 幂等批写）**。

当前最需要关注的不是「缺主流程」，而是三类问题：

1. **一致性窗口**：清算分阶段短事务（fee_split 已提交、finalize CAS miss）；结算 T+1 批次幂等键过粗导致部分商户被跳过；PAYING 无主动查单。  
2. **跨实例运维控制面**：进程内限流、本地积压熔断、告警只落库不外发、DLQ 语义「标注≠快速进 DLQ」。  
3. **文档漂移**：`00/01/05/08` 与监控方案仍宣称校验/审批/企微告警/Redis 结算锁等，与实现不符。

**一句话成熟度：** 主链路 **中高**；结算产品面与运维闭环 **中偏低**；LOCAL 账务一致性 P0 **已可验收**。

---

## 2. 系统定位与模块地图

**定位：** 支付平台清算、计费分润、中间户/账务结算全链路。

```text
上游交易/支付
    → pay-access（落单 + 补偿 Job）
    → pay-calc（编排 C1–C5）→ pay-fee（计费）→ pay-split（分账/凭证/Outbox）
    → pay-settlement（入账 / D0·T+1 / 回调）
    → 下游渠道（Mock）/ 账务 ACCOUNT_ONLY（本分支延期）
```

| 模块 | 职责 | 成熟度 |
|------|------|--------|
| pay-access | HTTP/MQ 接入、账单与路由、任务补偿 | 中高 |
| pay-calc | 清算分阶段编排、重试、Watchdog、终态隔离 | 高（A−） |
| pay-fee | 计费 Pipeline、规则缓存、幂等落库 | 中高（B+） |
| pay-split | 分账、凭证批写、Outbox 投递/补偿 | 中高（B+） |
| pay-settlement | 本地中间户、D0/T+1、回调 | 中（LOCAL 高；产品面中低） |
| pay-mq | Producer/消费分类/积压熔断/DLQ 巡检 | 中（原语 B−；运维 C） |
| pay-domain | 实体、仓储、分片路由 | 高 |
| pay-common | 锁策略、限流、AfterCommit、序号 | 中高 |
| pay-control | 校验、告警落库、工单 | 中 |
| pay-app | 启动与 Profile 聚合 | 高 |
| pay-test | MQ 压测工具 | 中 |

---

## 3. 主链路时序（短事务边界）

```text
[access TX] persist trade_bill + bill_route
     │（事务外）
     ├─ createTask(insertIfAbsent) + publish clearance_task / sync execute
     │
[calc TX1] claim: task PENDING→RUNNING → bill PENDING→CLEARING   ← 锁序 task→bill
     │（事务外）loadRelation
[calc TX2] fee_split: FeeCalc + Split(detail/voucher/outbox)
[calc TX3] finalize: task RUNNING→SUCCESS → bill CLEARING→CLEARED
     │ 失败: markFailed(RUNNING→FAILED|DEAD) → bill CLEARING→FAILED
     │
[split Job] OutboxDispatch → settle_amount_topic
[settle TX] credit / withdraw freeze / callback CAS→资金
     │ AfterCommit → MockPaymentChannel.submitAsync
```

**关键窗口：** TX2 成功且 TX3 CAS 失败 → 任务/账单停留 RUNNING/CLEARING，依赖 fail 路径或 Watchdog（30min）收敛。

---

## 4. 能力清单（按域）

### 4.1 接入（pay-access）

| 能力 | 入口 | 状态 |
|------|------|------|
| HTTP 提交 | `ClearanceController` → `BillAccessServiceImpl.submitBill` | 完整 |
| MQ PAY/REFUND | `TradePayConsumer` / `TradeRefundConsumer` | 完整 |
| 短事务落库 + 路由 | `persistNewBill` | 完整 |
| WAIT_ORIGIN 退款 | 落库置位；激活在 calc `activateWaitingRefunds` | 完整 |
| 缺任务补偿（P0-3） | `ClearanceTaskCompensateJob` | 完整 |
| 路由补偿 | `BillRouteCompensateJob` | 完整 |
| 积压熔断拒收 | `MqBacklogState` → 503 | 完整 |
| 多账单类型规则 / 鉴权签名 | — | 部分/缺失 |
| WaitOriginScanJob | — | 缺失（改为 calc 内联激活） |

### 4.2 清算 / 计费 / 分账

| 能力 | 入口 | 状态 |
|------|------|------|
| claim / fee_split / finalize | `ClearanceTaskServiceImpl` + `ClearanceTaskTxSupport` | 完整（C1/C3/C4） |
| 终态 skip / DEAD NonRetryable | `findTerminalStatus` / `handleUnclaimed` | 完整（C2） |
| claim 死锁重试 + 指标 | `claimWithDeadlockRetry` / `pay_calc_deadlock_total` | 完整（C4） |
| L2 重试 / Watchdog | `ClearanceRetryJob`（只扫 FAILED） | 完整 |
| 计费幂等 | `FeeCalcServiceImpl` billNo 短路 + UK | 完整（C5） |
| 分账批写 + 幂等补 voucher/outbox | `SplitServiceImpl` | 完整（C5） |
| Outbox 投递/补偿 | `OutboxDispatchJob` / `OutboxCompensateJob` / `SplitCompensateJob` | 完整 |

### 4.3 结算（pay-settlement）

| 能力 | 状态 | 说明 |
|------|------|------|
| 入账 / 退款扣款 | 完整 | `SettleAmountConsumer` + `AccountOperator` CAS |
| 支付回调 | 完整（LOCAL P0） | CAS 先改单再动账；`AfterCommit` 提交渠道 |
| D0 提现 | 部分 | 无日限额/卡一致性 |
| T+1 批结 | 部分 | 无分页截断；批次幂等过粗 |
| 渠道 | Mock only | 无 `PaymentChannelAdapter` / 查单 Job |
| H0/D1 / ADJUST / ACCOUNT_ONLY | 缺失或延期 | 枚举或计划标注延期 |

### 4.4 MQ / 公共

| 能力 | 状态 |
|------|------|
| 异常分类 ACK/RETRY/DLQ 标注 | 完整；DLQ 与 RETRY 均 rethrow |
| Redis 锁 fail-closed + 禁 TX 内锁 | 完整（热路径目前以 DB CAS 为主） |
| `@DbRateLimit` block/reject | 完整；默认 block；集群非共享 |
| `AfterCommit` | 完整 |
| 积压熔断 | 完整；**每 JVM 本地状态** |

### 4.5 管控 / Job / 可观测

| 能力 | 状态 |
|------|------|
| 告警 | 仅 `alert_record` + 日志；无企微/邮件 |
| 工单 | 可开单；无关闭/指派/manualRetry API |
| 商户校验 | 金额>0、商户启用、退款原单；弱于 docs/08 |
| 定时任务 | ≥15 个（补偿/重试/Outbox/T+1/对账/积压/DLQ/库容） |
| Prometheus/Grafana | `docs/docker/monitoring` 齐全；告警外发未接 |

---

## 5. 正确性与一致性 Review

| 主题 | 结论 | 证据要点 |
|------|------|----------|
| C1 finalize 路由 | ✅ merchantId CAS，无 finalize `findByBillNo` | `ClearanceTaskTxSupport.finalizeSuccess` |
| C2 脏消息 | ✅ 终态 skip；DEAD→NonRetryable；脚本 reset offset | `ClearanceTaskServiceImpl`；`scripts/reset-mq-offsets.sh` |
| C3 fail 单 SQL | ✅ `markFailed` 原子 retry；fail 前不整行 find | `ClearanceTaskMapper.markFailed` |
| C4 锁序/死锁 | ✅ 全路径 task→bill；claim 有限重试 | finalize/fail 无本地死锁重试 |
| P0-2 Watchdog | ✅ 短事务 fail + 事务外告警 | `watchdogFailAndNotify` |
| P0-3 接入补偿 | ✅ 存在；可能对 PENDING 任务反复 republish | `ClearanceTaskCompensateJob` |
| P0-1 结算回调 | ✅ CAS-first + AfterCommit | `SettleAccountTxSupport.applyPaymentCallback` |
| P0-4 锁/限流 | ✅ 策略落地；热路径少用 Redis 锁 | `CommonP0AcceptanceTest` |
| 清算跨 TX 半成品 | ⚠ fee 已写、finalize miss | 依赖 fail/Watchdog |
| fail 后 bill CAS | ⚠ `failRunningTask` 不检查 bill 更新行数 | 可能 task 终态 / bill 仍 CLEARING |
| T+1 批次幂等 | ⚠ 共享 `originSettleNo=batchNo` | 首笔成功后整批可能跳过 |
| PAYING 孤儿 | ⚠ 无查单 Job；Mock 渠道掩盖 | 真实渠道必现 |

---

## 6. 性能与运维 Review

| 主题 | 结论 |
|------|------|
| 有效清算 TPS | C1～C5 后理论上限抬升；仍依赖干净 MQ + 费率种子；PR-D3 未做 |
| Compensate 放大 | PENDING+task PENDING 每轮可全量 republish → MQ 风暴风险 |
| `RATE_LIMITED`→MQ RETRY | reject 模式下可能放大重试 |
| 积压熔断 | 仅本机；多实例可能一半 503 一半放行 |
| 告警外发 | 无；DEAD/DLQ 易「库里有、人不知」 |
| Admin 指标失败 | 常降级为 0 → 假健康 |
| 监控栈 | Prometheus/Grafana/exporters 与 dashboard 存在，需 staging 拉通 |

---

## 7. 文档 vs 实现漂移

| 文档主张 | 实现 |
|----------|------|
| `00` FR-CT01 / `08` 完整校验（精度、冻结、分润校验） | 仅金额/状态/退款原单 |
| `01` §9.2 企微/邮件/电话 | 告警落库 + log |
| `01` §9.3 双人审批 | 规则 submit pending，无审批流 |
| `00` Job 表（access） | 未列 `ClearanceTaskCompensateJob` |
| `08`/`09` WaitOriginScanJob | 无 Job；calc 内联激活 |
| `05` §10 Redis 结算锁 | 代码已移除，改 CAS |
| `05` §7 回调「先资金后状态」伪码 | 实现已 CAS-first |
| `monitor/系统监控方案` prometheus 仅 mq profile | 基线 yml 已暴露 prometheus |
| DEAD → 运营台 manualRetry | 无 control 重放 API |

**SoT 建议：** 以本文件 + `docs/方案C级别优化.md` + `docs/superpowers/plans/2026-08-08-p0-fixes.md` 为近期事实源；过时章节打「已过时」戳。

---

## 8. 风险分级（合成）

### P0

| ID | 风险 | 域 |
|----|------|-----|
| R1 | T+1 批次幂等过粗，未完成商户被跳过 | settlement |
| R2 | PAYING 无主动查单，回调丢失则资金态悬挂（Mock 掩盖） | settlement |
| R3 | 告警/工单不外发 → 生产 DEAD/DLQ 静默 | control |
| R4 | DLQ 动作与 RETRY 同为 rethrow，毒消息耗尽 reconsume | mq |
| R5 | 清算 fee 已提交 + finalize 失败窗口依赖 Watchdog 收敛 | calc |
| R6 | Compensate 对 PENDING 任务反复 republish → MQ 风暴 | access |

### P1

| ID | 风险 | 域 |
|----|------|-----|
| R7 | MQ 缺 merchantId / fee·split `findByBillNo` 弱路由 | calc/fee/split |
| R8 | fail 路径 bill CAS 结果未校验 | calc |
| R9 | 校验弱于 docs/08；坏单可进管道 | access/control |
| R10 | 限流/熔断进程内，多实例行为不一致 | common/mq |
| R11 | `RATE_LIMITED` 被 Classifier 默认 RETRY | mq |
| R12 | 提现缺日限额/卡校验；渠道仅 Mock | settlement |
| R13 | 工单号 AtomicLong；Job 无选主 | control |

### P2

| ID | 风险 | 域 |
|----|------|-----|
| R14 | H0/D1/ADJUST/多账单类型/鉴权未实现 | settlement/access |
| R15 | finalize/fail 无死锁本地重试 | calc |
| R16 | 文档大面积漂移导致 onboarding 误判 | docs |
| R17 | ACCOUNT_ONLY 延期，勿在无设计下开启 | settlement |

---

## 9. 90 天优先级建议

1. **修 T+1 批次幂等**（按商户/settlement_order 粒度）并补回归单测。  
2. **补 PaymentStatusQueryJob（或等价）** + 真实/可切换渠道适配，去掉对 Mock 的依赖。  
3. **收敛清算半成品**：fail 校验 bill CAS；缩短或可观测 fee→finalize 窗口；Watchdog 阈值与告警联动。  
4. **告警外发**（至少 webhook/企微）覆盖 DEAD、DLQ、circuit、payment-fail；Admin 失败显式 `up` 指标。  
5. **Compensate 节流**：仅「缺任务或确证缺消息」时 republish；加幂等/冷却。  
6. **Classifier**：`RATE_LIMITED` / 可选毒消息快速 ACK 或真正 DLQ；补 Invoker 单测。  
7. **多实例控制面**：共享熔断标志或文档明确「每实例独立」；限流改为集群感知或按实例折算。  
8. **文档对齐冲刺**：改写 `00/01/05/08` 过时节；补 Job 表与 manualRetry 边界；或砍掉未实现 FR。  
9. **校验补齐或降级规格**：要么实现 docs/08 关键项，要么改 FR 避免虚假合规。  
10. **C 级压测基线固化**：干净 offset + 费率种子下目标 20～40 有效 TPS，写入运维手册。

---

## 10. 审查方法与来源

| 域 | 并行审查 |
|----|----------|
| access | 接入域并行审查 |
| calc/fee/split | 清算/计费/分账并行审查 |
| settlement | 结算域并行审查 |
| mq/common | MQ/公共并行审查 |
| control/docs | 管控/运维/文档并行审查 |

基线提交：`3582306`（含 C2～C5）。P0 plan：`docs/superpowers/plans/2026-08-08-p0-fixes.md`。C 级：`docs/方案C级别优化.md`、`docs/问题C1修复.md`。

---

## 11. Spec 自检

| 检查项 | 结果 |
|--------|------|
| 无 TBD/占位未填 | ✅ |
| 风险均有域归属 | ✅ |
| 不与「已落地 C1～C5」矛盾（残留为窗口/运维） | ✅ |
| 范围声明只读、无实现任务混入 | ✅ |
| 90 天建议可执行可验收 | ✅ |

---

*文档版本：2026-08-09 · 全量重梳 · 待用户审阅*
