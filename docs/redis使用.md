# Redis 使用方案

> **现状：** 代码未接入 Redis（无 `spring-boot-starter-data-redis`、无 Spring Cache）。架构文档与监控已预留 Redis 7。  
> **原则：** 主数据/配置类缓存一律走 **Spring Cache 注解**（`@Cacheable` / `@CacheEvict` / `@CachePut`），禁止业务代码硬编码 Key、TTL、序列化；仅序列号、分布式锁、幂等 SETNX 使用封装组件（内部仍禁止散落硬编码）。

---

## 1. 现状与问题

### 1.1 清算热路径上的重复 DB 读

每笔账单清算大致会打到：

| 调用点 | 当前实现 | 问题 |
|--------|----------|------|
| `FeeCalcServiceImpl.calcShareFee` | `feeShareRuleRepository.findAll()` | **全表扫描**，极低写、极高读 |
| `ClearanceTaskServiceImpl.executeTask` | `merchantValidateService.loadRelation` | 每任务查 `agent_merchant_relation` |
| `MerchantValidateServiceImpl.validateBill` | `merchantProfileRepository.findById` | 接入时每单查档案 |
| `FeeCalcPipeline.joinMonths` | `merchantContractRepository.findByMerchantId` | 单次计费最多 3 次合约查询 |
| `SettleAccountServiceImpl` | 提现/T+1/查余额读合约 | 结算路径重复读合约 |

### 1.2 多实例安全缺口

| 能力 | 当前 | 风险 |
|------|------|------|
| `SeqGenerator` | JVM `AtomicLong` | 多实例单号冲突 |
| 同商户并发入账/提现 | 仅 DB 乐观锁 | 高并发下重试风暴，需可选分布式锁 |
| 同 billNo 并发计费 | DB 幂等 | 可加短锁降低重复计算 |

### 1.3 缓存选型结论

| 数据类型 | 是否缓存 | 方式 |
|----------|----------|------|
| `fee_share_rule` / 关系 / 档案 / 合约 | **必须** | Spring Cache 注解 + Redis |
| 单号序列 | **必须（多实例）** | `StringRedisTemplate.INCR` 封装 |
| 结算/计费锁、幂等 | 推荐 | Redis SET NX 封装 |
| 余额、流水、账单状态机 | **禁止** | MySQL 权威源 |

---

## 2. 总体架构

```
业务 Service / Repository
        │
        ▼
┌───────────────────────────────┐
│  Spring Cache 注解层          │  ← 主数据：@Cacheable / @CacheEvict
│  CacheNames 常量（唯一 Key 源）│
└───────────────┬───────────────┘
                │
                ▼
┌───────────────────────────────┐
│  RedisCacheManager            │  ← Lettuce，按 cacheName 配 TTL
└───────────────┬───────────────┘
                │
                ▼
            Redis 7
                ▲
┌───────────────┴───────────────┐
│  RedisSeqGenerator            │  ← 序列号
│  RedisDistributedLock         │  ← settle/fee 锁
│  RedisIdempotentGuard         │  ← 可选幂等
│  （Key 全部来自 RedisKeys）    │
└───────────────────────────────┘
```

**降级：** Redis 不可用时，注解缓存 miss 回源 DB；锁/序列组件需明确失败策略（见 §8）。

---

## 3. 接入清单（基础设施）

### 3.1 依赖

在 `pay-app`（或公共父模块）引入：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-cache</artifactId>
</dependency>
<!-- 可选：本地一级缓存，降低 Redis 往返 -->
<dependency>
    <groupId>com.github.ben-manes.caffeine</groupId>
    <artifactId>caffeine</artifactId>
</dependency>
```

### 3.2 配置（`application.yml`）

```yaml
spring:
  data:
    redis:
      host: ${REDIS_HOST:127.0.0.1}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD:}
      timeout: 2s
      lettuce:
        pool:
          max-active: 32
          max-idle: 16
          min-idle: 4
  cache:
    type: redis
    redis:
      time-to-live: 3600000   # 默认 1h，可被 CacheManager 按 name 覆盖
      cache-null-values: false
```

### 3.3 启动与 CacheManager

```java
@SpringBootApplication
@EnableCaching
public class PaySettleApplication { ... }
```

```java
@Configuration
@EnableCaching
public class RedisCacheConfig {

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory factory) {
        RedisCacheConfiguration defaults = RedisCacheConfiguration.defaultCacheConfig()
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        fromSerializer(new GenericJackson2JsonRedisSerializer()))
                .disableCachingNullValues();

        Map<String, RedisCacheConfiguration> map = new HashMap<>();
        map.put(CacheNames.FEE_RULES, defaults.entryTtl(Duration.ofMinutes(30)));
        map.put(CacheNames.AGENT_RELATION, defaults.entryTtl(Duration.ofHours(1)));
        map.put(CacheNames.MERCHANT_PROFILE, defaults.entryTtl(Duration.ofHours(6)));
        map.put(CacheNames.MERCHANT_CONTRACT, defaults.entryTtl(Duration.ofHours(6)));

        return RedisCacheManager.builder(factory)
                .cacheDefaults(defaults.entryTtl(Duration.ofHours(1)))
                .withInitialCacheConfigurations(map)
                .build();
    }
}
```

---

## 4. 禁止硬编码：常量与注解规范

### 4.1 Cache 名称唯一入口

```java
package com.payment.common.cache;

/**
 * Spring Cache 名称常量。业务代码只引用此处，禁止字面量 "feeRules" 等。
 */
public final class CacheNames {
    public static final String FEE_RULES = "feeRules";
    public static final String AGENT_RELATION = "agentRelation";
    public static final String MERCHANT_PROFILE = "merchantProfile";
    public static final String MERCHANT_CONTRACT = "merchantContract";

    private CacheNames() {}
}
```

### 4.2 非 Cache 场景 Key 模板

```java
package com.payment.common.cache;

/**
 * Redis 命令型 Key（序列/锁/幂等）。禁止在业务类中拼接 "settle:lock:" 等字面量。
 */
public final class RedisKeys {
    public static String seqBill(String yyyyMMdd) {
        return "seq:bill:" + yyyyMMdd;
    }
    public static String seqSettle(String yyyyMMdd) {
        return "seq:settle:" + yyyyMMdd;
    }
    public static String seqWithdraw(String yyyyMMdd) {
        return "seq:withdraw:" + yyyyMMdd;
    }
    public static String settleLock(Long merchantId) {
        return "settle:lock:" + merchantId;
    }
    public static String feeLock(String billNo) {
        return "fee:lock:" + billNo;
    }
    public static String idempotent(String billNo, String op) {
        return "idempotent:" + billNo + ":" + op;
    }

    private RedisKeys() {}
}
```

### 4.3 注解写法（推荐）

| 场景 | 注解 | 说明 |
|------|------|------|
| 读缓存 | `@Cacheable(cacheNames = CacheNames.XXX, key = "#id")` | Cache-Aside |
| 写后失效 | `@CacheEvict(cacheNames = CacheNames.XXX, key = "#entity.merchantId")` | 变更删 Key |
| 全量失效 | `@CacheEvict(cacheNames = CacheNames.FEE_RULES, allEntries = true)` | 规则审批 |
| 写后更新 | `@CachePut` | 本项目主数据变更少，优先 Evict |

**强制约定：**

1. `cacheNames` / `value` **必须**引用 `CacheNames` 常量，禁止字符串字面量。  
2. `key` 使用 SpEL（`#merchantId`、`#root.methodName`），禁止手写 Redis Key。  
3. 注解打在 **Spring 代理可达** 的 public 方法上（Service / Repository Impl），同类内部自调用无效时用自注入或抽到独立 Cache Service。  
4. 禁止业务代码直接 `redisTemplate.opsForValue().get("rel:merchant:" + id)`。

---

## 5. 缓存点设计（按优先级）

### 5.1 P0 — 计费规则 `fee_share_rule`

| 项 | 设计 |
|----|------|
| **为何** | 每笔新计费 `findAll()`，文档定性「必须 Redis」 |
| **CacheName** | `CacheNames.FEE_RULES` |
| **逻辑 Key（SpEL）** | `'all'` 或 `#targetType`（按目标类型分片更优） |
| **Redis 物理形态** | Spring Cache → `feeRules::all`；或预热为 Hash `fee:rules:{targetType}`（与 [04-计费引擎](04-计费引擎详细设计.md) 对齐，由 CacheManager/自定义 CacheLoader 实现） |
| **TTL** | 30 分钟 |
| **失效** | 规则审批通过 / 停用 → `@CacheEvict(allEntries = true)` |
| **落点** | 读：`FeeShareRuleRepository.findAll` 或独立 `FeeRuleCacheService.loadActiveRules()`；写：`FeeRuleServiceImpl` 审批方法 |

**注解示例：**

```java
@Cacheable(cacheNames = CacheNames.FEE_RULES, key = "'all'")
public List<FeeShareRuleEntity> findAllActive() { ... }

@CacheEvict(cacheNames = CacheNames.FEE_RULES, allEntries = true)
public void approveRule(Long ruleId) { ... }
```

**改造点：** `FeeCalcServiceImpl` 改为调用带注解的加载方法，去掉裸 `findAll()` 热路径。

---

### 5.2 P0 — 代理关系 `agent_merchant_relation`

| 项 | 设计 |
|----|------|
| **为何** | 每笔清算 `loadRelation` |
| **CacheName** | `CacheNames.AGENT_RELATION` |
| **Key** | `#merchantId` |
| **Value** | `AgentRelationDTO`（或实体 JSON） |
| **TTL** | 1 小时 |
| **失效** | 关系变更 API + 小时级全量刷新 Job（见 [01-架构设计](01-架构设计.md) §4.2） |
| **落点** | `MerchantValidateServiceImpl.loadRelation` |

```java
@Cacheable(cacheNames = CacheNames.AGENT_RELATION, key = "#merchantId")
public AgentRelationDTO loadRelation(Long merchantId) { ... }

@CacheEvict(cacheNames = CacheNames.AGENT_RELATION, key = "#merchantId")
public void onRelationChanged(Long merchantId) { ... }
```

---

### 5.3 P1 — 商户档案 `merchant_profile`

| 项 | 设计 |
|----|------|
| **为何** | 接入校验每单 `findById` |
| **CacheName** | `CacheNames.MERCHANT_PROFILE` |
| **Key** | `#merchantId` |
| **Value** | 精简字段：id / status / name（避免缓存过大无关列） |
| **TTL** | 6 小时 |
| **失效** | 冻结/解冻/档案变更 → `@CacheEvict` |
| **落点** | `MerchantProfileRepository` 或 Validate 内抽取的 Cache Service |

---

### 5.4 P1 — 商户合约 `merchant_contract`

| 项 | 设计 |
|----|------|
| **为何** | 计费 joinMonths、结算模式/最低提现额多次读取 |
| **CacheName** | `CacheNames.MERCHANT_CONTRACT` |
| **Key** | `#merchantId` |
| **TTL** | 6 小时 |
| **失效** | 合约变更 → `@CacheEvict` |
| **落点** | `MerchantContractRepositoryImpl.findByMerchantId` |

同一清算任务内 L1/L2/合伙人可能各查一次；注解缓存 + 可选 Caffeine L1 后变为 1 次 Redis/本地命中。

---

### 5.5 P2 — 单号序列（非 Cache 注解）

| 项 | 设计 |
|----|------|
| **为何** | 替换 `SeqGenerator` 的 JVM 计数器，保证多实例唯一 |
| **Key** | `RedisKeys.seqBill/Settle/Withdraw(date)` |
| **命令** | `INCR`；首次 `EXPIRE` 2 天 |
| **封装** | `RedisSeqGenerator`（Spring Bean），**禁止**业务直接 INCR |
| **降级** | Redis 不可用时拒绝发号或回落 DB 号段表（二选一，生产建议号段表兜底） |

---

### 5.6 P2 — 分布式锁

| Key 工厂 | TTL | 用途 |
|----------|-----|------|
| `RedisKeys.settleLock(merchantId)` | 30s | D0/并发入账，配合 `AccountOperator` 乐观锁 |
| `RedisKeys.feeLock(billNo)` | 短（如 5–10s） | 同单并发计费 |

封装 `RedisDistributedLock.tryLock / unlock`（基于 SET NX EX + 持有者 token），业务只调 API，不拼 Key。

---

### 5.7 P3 — 幂等（可选）

| 项 | 设计 |
|----|------|
| **Key** | `RedisKeys.idempotent(billNo, op)` |
| **命令** | SET NX + TTL 24h |
| **定位** | DB 唯一索引仍是正确性底线；Redis 仅加速短路 |
| **封装** | `RedisIdempotentGuard` |

---

### 5.8 可选 — `bill_route`

| 项 | 设计 |
|----|------|
| **Key** | 若用注解：独立 `CacheNames.BILL_ROUTE`，`key = "#billNo"` |
| **TTL** | 24h–7d（写一次、几乎不变） |
| **注意** | 错误缓存会导致分片路由错误；优先级低于规则/关系 |

---

### 5.9 明确不缓存

- `merchant_settle_account` 余额、冻结额  
- `account_flow` 流水  
- `trade_bill` / `clearance_task` 状态（状态机权威在 DB）  
- `fee_calc_result`（已有 billNo 幂等，缓存收益低）  

---

## 6. 模块改造映射

| 模块 | 改造内容 |
|------|----------|
| `pay-common` | `CacheNames`、`RedisKeys`、序列/锁/幂等组件接口 |
| `pay-app` | 依赖、`@EnableCaching`、`RedisCacheConfig`、`application.yml` |
| `pay-domain` | Repository 读方法加 `@Cacheable`（或抽 Cache Service） |
| `pay-fee` | 规则加载走缓存；可选 `fee:lock` |
| `pay-control` | `loadRelation` / 档案缓存；规则审批 `@CacheEvict` |
| `pay-calc` | 无需改编排逻辑（透过 Validate/Fee 间接受益） |
| `pay-settlement` | 合约读缓存；`settle:lock`；`SeqGenerator` 换 Redis 实现 |
| Job | 小时级关系/规则缓存预热或对账刷新（原 02 手册待实现项） |

---

## 7. 实施顺序

```
1. 接入 Redis + EnableCaching + CacheNames/RedisKeys
2. fee_share_rule 注解缓存（收益最大）
3. agent_merchant_relation + merchant_profile + merchant_contract
4. RedisSeqGenerator 替换 JVM 序号
5. settle:lock / fee:lock
6. 可选：幂等 SETNX、bill_route、Caffeine 二级缓存
```

每步应可独立灰度：配置开关 `payment.redis.enabled=true`，关闭时 CacheManager 退化为 NoOp 或直查 DB。

---

## 8. 一致性、降级与容错

| 场景 | 策略 |
|------|------|
| 主数据变更 | **先写 DB，再 Evict**（Cache-Aside）；禁止只改缓存 |
| Redis 宕机 | 注解缓存穿透到 DB；业务可用，RT 上升（与 [09-异常与容错](09-异常与容错设计.md) 一致） |
| 锁获取失败 | 快速失败或短重试，不降级为「无锁写余额」 |
| 序列号 Redis 失败 | 拒绝发号或切 DB 号段，避免双实例撞号 |
| 缓存脏读窗口 | 主数据变更低频；TTL + 主动 Evict 可接受；金融余额永不走缓存 |
| 序列化 | 统一 Jackson；实体变更注意兼容或 bump cache 名版本（如 `feeRules_v2`） |

---

## 9. Key 总览（与既有文档对齐）

| Key / Cache | 类型 | TTL | 实现方式 | 说明 |
|-------------|------|-----|----------|------|
| `feeRules`（`fee:rules:{targetType}`） | Cache / Hash | 30m | **注解** | 计费规则 |
| `agentRelation`（`rel:merchant:{id}`） | Cache / Hash | 1h | **注解** | 代理关系 |
| `merchantProfile` | Cache | 6h | **注解** | 商户档案 |
| `merchantContract` | Cache | 6h | **注解** | 商户合约 |
| `seq:bill:{date}` 等 | String INCR | 2d | 封装组件 | 单号 |
| `settle:lock:{merchantId}` | String NX | 30s | 封装组件 | 结算锁 |
| `fee:lock:{billNo}` | String NX | 短 | 封装组件 | 计费锁 |
| `idempotent:{billNo}:{op}` | String NX | 24h | 封装组件 | 可选幂等 |

Spring Cache 默认 Key 形如 `{cacheName}::{spelKey}`，与文档中的业务 Key 语义对应即可；若需与运维文档完全一致的裸 Key，可通过自定义 `RedisCacheWriter` / `keyPrefix` 映射，**仍禁止业务硬编码**。

---

## 10. 代码审查 Checklist

- [ ] 新增缓存是否只覆盖「低频写、高频读」主数据？  
- [ ] `@Cacheable` / `@CacheEvict` 的 `cacheNames` 是否全部来自 `CacheNames`？  
- [ ] 是否存在 `redisTemplate` + 字符串拼接 Key 的业务代码？（应驳回）  
- [ ] 写路径是否有对应 `@CacheEvict`？  
- [ ] 余额/流水/状态机是否误加缓存？  
- [ ] Redis 故障时行为是否符合 §8？  
- [ ] 多实例下单号是否已切 `RedisSeqGenerator`？  

---

## 11. 相关文档

| 文档 | 相关章节 |
|------|----------|
| [02-开发手册](02-开发手册.md) §8 | 原 Redis Key 表（本文细化并落地注解规范） |
| [01-架构设计](01-架构设计.md) §4.2 | 关系/规则刷新策略 |
| [04-计费引擎详细设计](04-计费引擎详细设计.md) §7 | 规则缓存结构 |
| [03-领域模型与枚举字典](03-领域模型与枚举字典.md) | Redis INCR 单号 |
| [db/db拆分](db/db拆分.md) | fee_rule「必须 Redis」 |
| [09-异常与容错设计](09-异常与容错设计.md) | 缓存降级 |

---

## 12. 版本

- **V1.0** — 2026-07-16 — 对照当前代码梳理缓存点；明确注解优先、禁止硬编码的落地规范
