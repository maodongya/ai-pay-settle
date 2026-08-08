# ai-pay-settle MQ 消费提速与多线程方案

> 版本：V1.0 | 日期：2026-07-12  
> 适用：RocketMQ 4.x/5.x + rocketmq-spring-boot-starter 2.3.x + 本项目 Topic 链路

---

## 1. 结论先行

**可以使用多线程提高 MQ 消费速度，但不能“无脑开线程”。**

RocketMQ 消费提速的本质是：**在消息幂等、顺序约束、数据库并发能力三者都满足的前提下，提高并行度**。对本项目而言：

| 手段 | 是否推荐 | 预期收益 | 主要风险 |
|------|----------|----------|----------|
| 调大 Consumer 消费线程（`consumeThreadMax`） | ✅ 推荐（首选） | 中～高 | DB 连接池、锁冲突 |
| 水平扩容 Consumer 实例（多 Pod） | ✅ 强烈推荐 | 高 | 需监控堆积与 rebalance |
| 消费模式改为 `CONCURRENTLY` | ✅ 推荐（大部分 Topic） | 高 | 同 Key 乱序 |
| 同 Topic 内自建线程池异步 `handle` | ⚠️ 谨慎 | 中 | ACK 时机、异常重试复杂 |
| 批量消费（Batch Listener） | ⚠️ 部分场景 | 中 | 需改造幂等与事务 |
| 有序消费（`ORDERLY`）+ 多线程 | ❌ 不适用提速 | — | 队列内串行，无法并行 |

**一句话：** 对本项目，优先 **RocketMQ 并发消费 + 水平扩容 + 按商户/单据分片**；清算重链路（计费→清分→入账）的瓶颈往往在 **DB 事务**，线程数超过 DB/连接池承载后会反向降速。

---

## 2. 本项目 MQ 链路现状

### 2.1 Topic 与 Consumer

```
trade_pay_topic      → TradePayConsumer        (pay-access-consumer)
trade_refund_topic   → TradeRefundConsumer     (pay-access-consumer)
clearance_task_topic → ClearanceTaskConsumer   (pay-calc-consumer)
settle_amount_topic  → SettleAmountConsumer    (pay-settlement-consumer)
payment_result_topic → PaymentResultConsumer   (pay-settlement-consumer)
```

当前实现（以 `TradePayConsumer` 为例）：

```java
@RocketMQMessageListener(
    topic = MqTopics.TRADE_PAY,
    selectorExpression = MqTags.PAY,
    consumerGroup = MqConsumerGroups.ACCESS
)
public static class PayRocketListener implements RocketMQListener<String> {
    @Override
    public void onMessage(String message) {
        delegate.handle(message);  // 同步执行业务
    }
}
```

**特征：**

- 默认 **并发消费**（`ConsumeMode.CONCURRENTLY` 为默认值）
- 默认消费线程：**20**（RocketMQ Client 默认 `consumeThreadMin=20, consumeThreadMax=20`）
- `onMessage` 内同步调用 `BillAccessService.submitBill` → 落库 → 投递 `clearance_task_topic` 或同步 `executeTask`
- 清算执行 `ClearanceTaskServiceImpl.executeTask` 含：计费 + 清分 + Outbox，**单条消息 RT 高、事务重**

### 2.2 端到端流水线

```
支付渠道
  → trade_pay_topic
  → 接入层（校验 + trade_bill 落库 + 发 clearance_task）
  → clearance_task_topic
  → 算账层（计费 + 清分 + outbox）
  → settle_amount_topic（或 OutboxDispatchJob 本地轮询）
  → 结算层（creditBalance / debitRefundBalance）
```

**提速关键不在某一个 Consumer，而在整条链路的并行度与瓶颈转移。**

---

## 3. 为什么“加线程”有时无效甚至变慢

### 3.1 Amdahl 定律：串行部分限制加速比

单条清算任务内部步骤高度串行：

```
claim task → fee calc → split → update bill status → outbox
```

即使 MQ 层 32 线程并行拉消息，若每个 `executeTask` 平均 50～200ms（DB + 规则匹配 + 多表写入），**单机 TPS 上限 ≈ 线程数 / 平均 RT**。

例：RT=100ms，20 线程 → 理论 ~200 TPS；RT=500ms → ~40 TPS。

### 3.2 数据库成为硬瓶颈

本项目大量操作带 `@Transactional`：

- `trade_bill` 唯一索引幂等
- `clearance_task` 状态 claim（防重）
- `fee_calc_result` / `split_detail` 写入
- `merchant_settle_account` **乐观锁 version**（`AccountOperator`）

当多线程同时更新 **同一 merchantId** 账户时：

- 触发 `30005 CONCURRENT_UPDATE`
- 重试放大延迟，吞吐不升反降

### 3.3 顺序与幂等约束

| 场景 | 顺序要求 | 能否多线程 |
|------|----------|------------|
| 不同 `bill_no` 的收款单 | 无 | ✅ 完全并行 |
| 同一 `bill_no` 重复消息 | 幂等返回 | ✅ 并行安全（UK 保证） |
| 退款单 vs 原收款单 | 原单先清算 | ⚠️ 需业务层 WAIT_ORIGIN |
| 同一 `merchantId` 余额入账 | 逻辑上可并行但锁竞争 | ⚠️ 并行需分片/锁 |
| 同一 `merchantId` D0 提现 | 强互斥 | ❌ 必须串行或加锁 |

### 3.4 连接池与下游资源

线程数 ↑ → 同时占用：

- HikariCP 连接数
- MyBatis Session
- Redis（若启用规则缓存）
- 磁盘 IO（binlog、redo）

**线程数 > 连接池 max → 线程阻塞等待连接，消费 TPS 崩溃。**

---

## 4. RocketMQ 多线程消费机制（原理）

### 4.1 Client 内部线程模型

```
Broker Queue
    ↓ pull
ConsumeMessageService（线程池 consumeThreadMin ~ consumeThreadMax）
    ↓ 每条消息
RocketMQListener.onMessage() / MessageListenerConcurrently
    ↓ 成功
CONSUME_SUCCESS（ACK）
    ↓ 异常
RECONSUME_LATER（重试，最多 16 次 → DLQ）
```

- **CONCURRENTLY**：同一 Queue 的多条消息可被不同线程同时处理（默认，适合本项目大部分 Topic）
- **ORDERLY**：同一 Queue 内严格串行（同一 sharding key 进同一 queue 时保证顺序，但**无法并行**）

### 4.2 并行度公式（估算）

```
Consumer 总并行度 ≈ 实例数 × consumeThreadMax × 有效利用率

有效利用率 = 1 - (锁冲突率 + 连接等待率 + GC 停顿率)
```

Queue 数量也影响并行：

```
Topic 并行上限 ≈ Queue 总数（所有 Broker 上）
```

若 Topic 只有 4 个 Queue，开 32 线程可能空转；**Queue 数应与目标并行度同量级**。

---

## 5. 分层提速方案（由易到难）

### 5.1 方案 A：调 Consumer 线程 + 批量拉取（零代码改动）

在 `@RocketMQMessageListener` 增加参数：

```java
@RocketMQMessageListener(
    topic = MqTopics.CLEARANCE_TASK,
    consumerGroup = MqConsumerGroups.CALC,
    consumeMode = ConsumeMode.CONCURRENTLY,
    consumeThreadMax = 32
)
```

或在 `application-mq.yml` 扩展（需统一封装 Listener 或通过 `DefaultRocketMQListenerContainer` 配置）：

```yaml
rocketmq:
  consumer:
    # 全局默认，可被 @RocketMQMessageListener 覆盖
    consume-thread-min: 20
    consume-thread-max: 32
    pull-batch-size: 32
```

**建议初始值：**

| Consumer | consumeThreadMax | 说明 |
|----------|------------------|------|
| TradePayConsumer | 16～32 | IO 轻、落库为主 |
| ClearanceTaskConsumer | 16～32 | CPU+DB 重，先压测再定 |
| SettleAmountConsumer | 8～16 | 账户乐观锁敏感，不宜过大 |
| PaymentResultConsumer | 8～16 | 出款回调量通常较低 |

**配合调整 HikariCP：**

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 40   # ≥ 最大并发消费线程 + 预留 HTTP/Job
      minimum-idle: 10
```

### 5.2 方案 B：水平扩容 Consumer 实例（强烈推荐）

同一 `consumerGroup` 下启动 **N 个 pay-app 实例**：

- RocketMQ 自动 **Rebalance**，Queue 分配到各实例
- 总吞吐近似线性扩展（直到 DB 饱和）
- 无单点线程/连接池压力

```
                    ┌─ pay-app-1 (20 threads)
trade_pay_topic ────┼─ pay-app-2 (20 threads)  → 总并行 ~40+
                    └─ pay-app-3 (20 threads)
```

**注意：** 实例数 ≤ Queue 总数时收益最大；超过后部分实例空闲。

**建 Topic 时设置：**

```bash
# 例：16 Queue，匹配 16 分片 shard_id
sh mqadmin updateTopic -n localhost:9876 -t clearance_task_topic -r 8 -w 8
```

本项目已有 `shardId = merchantId % 16`，**Topic Queue 数建议 16 或 32**，与分片对齐。

### 5.3 方案 C：按业务 Key 选择性顺序 + 跨 Key 并行

对 **必须有序** 的消息使用 `MessageQueueSelector` 发送：

```java
// 同一 merchantId 进同一 Queue → ORDERLY 消费时可保序
// CONCURRENTLY 时同 Queue 仍可能并行，若需严格串行改 ORDERLY
producer.send(msg, (list, message, arg) -> {
    Long merchantId = (Long) arg;
    int index = (int) (merchantId % list.size());
    return list.get(index);
}, merchantId);
```

策略选择：

| Topic | 发送 Key | 消费模式 | 原因 |
|-------|----------|----------|------|
| trade_pay | billNo | CONCURRENTLY | 单据级幂等，互不干扰 |
| clearance_task | merchantId 或 shardId | CONCURRENTLY | 提高并行；同商户冲突靠 DB 乐观锁 |
| settle_amount | merchantId | ORDERLY 或 小线程池 | 余额更新冲突多，建议同商户串行 |
| payment_result | settleNo | CONCURRENTLY | 结算单号唯一 |

### 5.4 方案 D：Listener 内异步线程池（谨慎使用）

```java
@Component
public class AsyncClearanceTaskConsumer implements RocketMQListener<String> {

    private final ExecutorService workers = new ThreadPoolExecutor(
            16, 32, 60, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(1000),
            new ThreadPoolExecutor.CallerRunsPolicy()
    );

    @Override
    public void onMessage(String message) {
        workers.submit(() -> delegate.handle(message));
        // ⚠️ 此处 return 会立即 ACK，业务尚未完成
    }
}
```

**问题：** `onMessage` 返回即 ACK，业务异步失败 **不会触发 MQ 重试**，造成 **静默丢单**。

**正确做法（二选一）：**

1. **不用 Listener 内异步**，只用 RocketMQ 自带消费线程池（推荐）
2. 使用 `MessageListenerConcurrently` 自行管理 `ConsumeConcurrentlyStatus`，异步完成后 ACK（实现复杂，需 inflight 计数与优雅停机）

**结论：本项目不建议在 `RocketMQListener` 内再套一层无 ACK 关联的线程池。**

### 5.5 方案 E：批量消费

适用于 **单条 RT 高、但彼此独立** 的场景（如 clearance_task）：

```java
public class BatchClearanceListener implements RocketMQListener<List<String>> {
    @Override
    public void onMessage(List<String> messages) {
        for (String msg : messages) {
            delegate.handle(msg);
        }
    }
}
```

配合：

```java
@RocketMQMessageListener(
    topic = MqTopics.CLEARANCE_TASK,
    consumerGroup = MqConsumerGroups.CALC,
    consumeMessageBatchMaxSize = 10
)
```

**收益：** 减少 pull 次数与网络往返。  
**风险：** 一批中一条失败会导致整批重试（需幂等）；不适合强事务批量提交除非改造为 Batch Insert。

### 5.6 方案 F：流水线彻底异步化（架构级，收益最大）

当前 `BillAccessServiceImpl` 已支持 `pay.mq.clearance-via-mq`：

```
接入：落 trade_bill → 发 clearance_task_topic（快，毫秒级 ACK）
算账：独立 Consumer 集群消费 clearance_task（可独立扩容）
清分：Outbox → settle_amount_topic
结算：SettleAmountConsumer 入账
```

**提速要点：**

1. 生产环境 **`clearanceViaMq=true`**，避免接入 Consumer 同步跑完清算
2. 接入层与算账层 **独立部署、独立扩缩容**
3. Outbox 优先走 MQ（`outboxViaMq=true`），减少 `OutboxDispatchJob` 轮询延迟

这是比“加线程”更有效的 **结构性优化**。

---

## 6. 按 Topic 的具体建议（本项目）

### 6.1 trade_pay_topic / trade_refund_topic

**瓶颈：** 校验 + insert trade_bill + 发下游 MQ  
**策略：**

- `CONCURRENTLY` + `consumeThreadMax=32`
- 多实例扩容接入层
- 确保 `bill_no` 幂等（已实现）

### 6.2 clearance_task_topic（核心热点）

**瓶颈：** `executeTask` 事务重  
**策略：**

- `consumeThreadMax=32`，压测观察 DB CPU / 慢 SQL
- Topic Queue = 16，与 `shardId` 对齐
- 可选：按 `shardId` 路由发送，便于监控各分片堆积
- **不要** 在 Listener 内异步 ACK
- 失败重试依赖 MQ + `ClearanceRetryJob` 双保险

**分片消费伪代码（未来扩展）：**

```java
// 每个实例只消费指定 shard（多 Group 或多 Tag）
@RocketMQMessageListener(
    topic = MqTopics.CLEARANCE_TASK,
    consumerGroup = "pay-calc-consumer-shard-03",
    selectorExpression = "shard-03"
)
```

### 6.3 settle_amount_topic

**瓶颈：** `merchant_settle_account` 乐观锁  
**策略：**

- **降低并发**：`consumeThreadMax=8～16`
- 同 `merchantId` 使用 `MessageQueueSelector` 固定 Queue + `ORDERLY` 消费（同商户串行、跨商户并行）
- 或 Redis 分布式锁 `settle:lock:{merchantId}`（设计文档已有）

```java
@RocketMQMessageListener(
    topic = MqTopics.SETTLE_AMOUNT,
    consumerGroup = MqConsumerGroups.SETTLEMENT,
    consumeMode = ConsumeMode.ORDERLY,
    consumeThreadMax = 16
)
```

`ORDERLY` 模式下：**每个 Queue 一条线程串行**，多 Queue 仍可并行。

### 6.4 payment_result_topic

出款回调 QPS 通常低于清算，默认线程即可；重点保证 **幂等**（`handlePaymentCallback` 已实现 SUCCESS 短路）。

---

## 7. 推荐配置模板

### 7.1 application-mq.yml 扩展建议

```yaml
pay:
  mq:
    enabled: true
    clearance-via-mq: true      # 接入与清算解耦
    outbox-via-mq: true         # Outbox 走 MQ 而非本地 Job
    payment-callback-via-mq: true

spring:
  datasource:
    hikari:
      maximum-pool-size: 50
      connection-timeout: 3000

rocketmq:
  name-server: 127.0.0.1:9877
  producer:
    group: pay-settle-producer
    send-message-timeout: 10000
  consumer:
    pull-batch-size: 32
```

### 7.2 ClearanceTaskConsumer 改造示例

```java
@RocketMQMessageListener(
    topic = MqTopics.CLEARANCE_TASK,
    consumerGroup = MqConsumerGroups.CALC,
    consumeMode = ConsumeMode.CONCURRENTLY,
    consumeThreadMax = 32,
    consumeTimeout = 15L  // 分钟，长事务需调大
)
public static class RocketListener implements RocketMQListener<String> {
    // ...
}
```

### 7.3 资源配比经验法则

```
consumeThreadMax（单实例） ≤ DB 连接池 max × 0.7
实例数 × Queue 数 ≥ 目标 TPS × 平均 RT（秒）
```

例：目标 500 TPS，RT=0.1s → 需要 ~50 并行度 → 2 实例 × 25 线程，或 4 Queue × 多实例。

---

## 8. 压测与观测

### 8.1 已有工具

项目 `pay-test` 模块：

- `MqLoadTestRunner`：多 Producer 客户端 + 全局限流 TPS
- `ClearanceLoadTestRunner`：HTTP 接入压测

**压测步骤：**

1. 启动 RocketMQ（`pay-mq/docker-compose.yml`）
2. `spring.profiles.active=mysql,mq` 启动 pay-app（建议 2+ 实例）
3. 运行 MQ 压测，逐步升 TPS：100 → 500 → 1000
4. 观察 **Consumer Lag**、P99 RT、DB 连接/active threads、30005 错误率

### 8.2 关键指标

| 指标 | 告警阈值建议 | 说明 |
|------|--------------|------|
| Consumer Lag | > 10000 持续 5min | 消费跟不上生产 |
| consume RT P99 | > 500ms | 单条处理过慢 |
| DB 连接等待 | > 50ms | 连接池不足 |
| 30005 并发冲突率 | > 1% | 线程过多或同商户热点 |
| DLQ 消息数 | > 0 | 需人工介入 |

### 8.3 RocketMQ Dashboard

本地：`pay-mq/docker-compose.yml` 已含 Dashboard，查看：

- 各 Group 堆积量
- 每 Queue 消费 TPS
- 消费失败重试次数

---

## 9. 反模式清单（避免）

1. **Listener 内 fire-and-forget 异步** → 丢消息无重试  
2. **无限增加 consumeThreadMax** → DB 连接耗尽、乐观锁风暴  
3. **全 Topic ORDERLY** → 并行度降为 Queue 数  
4. **忽略 Queue 数量** → 线程空转  
5. **同步跑完整清算链**（`clearanceViaMq=false`）→ 接入 Consumer 成为瓶颈  
6. **同 merchant 高并发入账无锁** → 30005 重试雪崩  
7. **批量消费无幂等** → 重试重复入账  

---

## 10. 实施路线图

### 阶段 1（1 天，低风险）

- [ ] 确认生产 `clearanceViaMq=true`、`outboxViaMq=true`
- [ ] 各 Consumer 显式设置 `consumeThreadMax`
- [ ] 调 HikariCP `maximum-pool-size`
- [ ] Topic Queue 数调整为 16

### 阶段 2（3 天，中风险）

- [ ] pay-app 双实例部署 + Rebalance 验证
- [ ] `settle_amount_topic` 改 ORDERLY + merchantId 路由
- [ ] 接入 Prometheus 监控 Lag / RT / 30005

### 阶段 3（1～2 周，架构优化）

- [ ] 接入层 / 算账层 / 结算层 **独立部署**
- [ ] 按 shard 拆分 Consumer Group（可选）
- [ ] 批量消费 + 批量写库（需专项改造与压测）

### 阶段 4（3～5 天，容错闭环）

- [ ] 积压监控 Job + 分级告警（见 [mq性能优化实施方案.md](./mq性能优化实施方案.md) §9）
- [ ] MQ 异常分类器 + DLQ 巡检与人工重放（§10）
- [ ] DEAD 任务自动工单 + 补偿 Job 增强（§11 PR-4）

> **完整 Playbook、SOP、代码清单** → [mq性能优化实施方案.md](./mq性能优化实施方案.md) §9～§11

---

## 11. 总结

| 问题 | 答案 |
|------|------|
| 能否用多线程？ | **能**，RocketMQ 默认就是多线程并发消费 |
| 首选手段？ | **调 consumeThreadMax + 水平扩容 + 流水线 MQ 解耦** |
| 最大瓶颈？ | **DB 事务与账户乐观锁**，不是 MQ 本身 |
| 结算 Topic 怎么办？ | **同商户有序/加锁，跨商户并行** |
| 最不该做什么？ | **Listener 内异步 ACK 后不管业务结果** |

对本项目，建议目标架构：

```
                    ┌─ 接入集群（轻量，高并发）
trade_pay_topic ────┤
                    └─ 算账集群（重量，按 DB 能力扩）
                           ↓
                    settle 集群（中量，ORDERLY by merchant）
```

**先量后调：** 每次只改一个变量（线程数 / 实例数 / Queue 数），用 `pay-test` 压测对比 TPS 与 P99，避免多因素同时变更无法归因。

---

## 12. 消息积压与多次失败（概要）

> 详细处置方案见 **[mq性能优化实施方案.md §9～§11](./mq性能优化实施方案.md)**

### 12.1 消息积压

**两类积压不可混淆：**

| 类型 | 观测 | 处置方向 |
|------|------|----------|
| MQ Lag | Dashboard Consumer Offset | 扩容实例 / 加线程 / 上游限流 |
| Outbox Pending | `outbox_message.status=0` | 加大 Job 批量 / 修 Broker / 暂停上游 |

**处置顺序：** 止血（扩容+限流）→ 消堆积 → 根因修复。  
**告警分级：** Lag > 1k 预警，> 10k P2，> 100k P1；Outbox pending > 1k P2。

**分 Topic 要点：**

- `clearance_task`：优先水平扩容，其次加线程  
- `settle_amount`：勿盲目加线程（30005），用 ORDERLY + 按 merchant 路由  
- 接入层积压时 **禁止** 改回同步 `executeTask`

### 12.2 消息多次失败

**三层重试模型：**

```
L1 RocketMQ：16 次退避 → DLQ（瞬态故障）
L2 业务层：clearance_task FAILED×5 → DEAD + 补偿 Job
L3 人工：DLQ / DEAD → exception_record → 运营台重放
```

**关键规则：**

- 幂等重复、业务拒单 → **ACK**，不浪费 MQ 重试次数  
- 瞬态（30005、连接超时）→ **抛异常**，走 MQ 重试  
- 脏数据、规则永久缺失 → **进 DLQ**，停止自动重试  
- `task.status=DEAD` 时 Listener 应抛 `NonRetryableException`，避免 MQ 空转 16 次  
- 部分成功（有 fee 无 split）→ `SplitCompensateJob`；有 split 无 outbox → 待建 `OutboxCompensateJob`

**现有补偿能力：**

| 组件 | 现状 |
|------|------|
| `ClearanceRetryJob` | ✅ 每小时重试 FAILED |
| `ClearanceRetryJob.watchdog` | ✅ RUNNING > 30min → FAILED |
| `SplitCompensateJob` | ✅ 补 split |
| `PaymentRetryJob` | ✅ 失败打款重试 |
| DLQ 巡检/重放 | ❌ 待 PR-4 实现 |
| DEAD 自动工单 | ❌ 待 PR-4 实现 |

---

## 附录 A：ConsumeMode 对比

| 模式 | 并行性 | 顺序保证 | 适用 Topic |
|------|--------|----------|------------|
| CONCURRENTLY | 高 | 不保证 | trade_pay, clearance_task, payment_result |
| ORDERLY | 中（Queue 级并行） | 同 Queue 严格有序 | settle_amount（按 merchant 路由） |

## 附录 B：与本项目设计文档的对应关系

- 分片键 `merchant_id % 16` → 见 `docs/02-开发手册.md` §1.3  
- 账户乐观锁 → 见 `docs/05-结算引擎详细设计.md`  
- 计费幂等 `bill_no` → 见 `docs/04-计费引擎详细设计.md`  
- MQ Topic 清单 → 见 `docs/02-开发手册.md` §3.1  
- 消费重试 16 次 + DLQ → 见 `docs/02-开发手册.md` §3.3  
