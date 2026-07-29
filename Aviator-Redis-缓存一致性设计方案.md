# Aviator 表达式缓存一致性设计方案

> **背景**：特征在管理台配置、测试和发布，发布后在统一报文加工服务执行。管理台发布/下线特征时，需保证数据库和 Redis 的事务一致性。报文加工服务通过定时任务从 Redis 拉取数据更新本地缓存，Redis 宕机时降级查数据库。
>
> **核心约束**：不使用广播机制（Redis Pub/Sub、gRPC、Feign 均不使用）。

---

## 一、整体架构

```
┌─────────────────────┐         ┌─────────────────────┐         ┌─────────────────────┐
│     管理台服务        │         │       Redis         │         │    报文加工服务       │
│                     │         │                     │         │                     │
│  ① 写 DB（事务）     │──成功──▶│  ② 写 Redis          │         │  定时任务（30s）：     │
│  ② 写 Redis         │         │  ③ 更新版本号        │         │  ① 轮询版本号         │
│  ③ 失败则记录待同步  │         │                     │◀────────│  ② 拉取变更数据       │
│                     │         │  (Redis 宕机时)       │         │  ③ 更新本地缓存       │
│                     │         │  降级查 DB           │────────▶│                     │
│                     │         │                     │         │  降级：直接查 DB      │
─────────────────────┘         └─────────────────────┘         └─────────────────────┘
```

### 数据流向

| 阶段 | 数据源 | 数据目标 | 触发方式 |
|------|--------|---------|---------|
| 发布/下线 | 管理台操作 | DB + Redis | 用户手动触发 |
| 缓存同步 | Redis | 报文加工服务本地缓存 | 定时任务（30s） |
| 降级查询 | DB | 报文加工服务本地缓存 | Redis 不可用时自动触发 |
| 补偿同步 | 待同步记录表 | Redis | 定时任务（60s） |

---

## 二、Redis 存储结构设计

### 2.1 Key 结构总览

```
expr:txn:versions                          Hash    全局交易码版本索引
  field: {txnCode}            value: {txnVersion}

expr:txn:{txnCode}                         Hash    交易码骨架（特征版本号）
  field: {featureCode}        value: {featureVersion}

expr:txn:data:{txnCode}:{featureCode}      String  特征预编译对象字节（5-10KB）
```

### 2.2 各 Key 详细说明

#### `expr:txn:versions` — 全局交易码版本索引

| 属性 | 说明 |
|------|------|
| 类型 | Hash |
| 用途 | 存储所有交易码及其当前版本号，供报文加工服务定时轮询比对 |
| 写入时机 | 交易码下任意特征发布/下线/更新时，该交易码版本号 +1 |
| 删除时机 | 交易码下所有特征均下线后，清理该交易码的 Hash 字段 |

**设计要点**：使用 Hash 而非 String 的原因——报文加工服务只需一次 `HGETALL` 即可获取所有交易码版本号，避免 N 次 `GET` 操作。当交易码数量在百级别时，Hash 结构的网络开销远优于多次单 Key 查询。

#### `expr:txn:{txnCode}` — 交易码骨架

| 属性 | 说明 |
|------|------|
| 类型 | Hash |
| 用途 | 存储交易码下每个特征的版本号，用于精确比对哪些特征发生了变化 |
| 写入时机 | 特征发布时 HSET，特征下线时 HDEL |
| 删除时机 | 交易码下无特征时，DEL 整个 Hash |

**设计要点**：骨架中只存版本号，不存表达式内容。这样报文加工服务在轮询时，先通过骨架比对版本号差异，再用 `MGET` 批量拉取变化的特征字节，实现**增量精确同步**，避免全量拉取。

#### `expr:txn:data:{txnCode}:{featureCode}` — 特征预编译对象

| 属性 | 说明 |
|------|------|
| 类型 | String（存储原始 byte[]） |
| 用途 | 存储 Aviator 编译并序列化后的 Expression 对象字节 |
| 写入时机 | 特征发布/更新时写入 |
| 删除时机 | 特征下线时 DEL |
| 序列化方式 | AviatorObjectOutputStream（Aviator 5.4.1） |
| 大小 | 通常 5-10KB，单个特征不超过 50KB |

**设计要点**：使用 `ByteArrayRedisSerializer` 直接存储 byte[]，不使用 Base64 编码。原因：Base64 会使数据膨胀约 33%，增加 Redis 内存占用和网络传输开销。直接存储 byte[] 在反序列化时性能也更好。

---

## 三、管理台侧：发布/下线流程

### 3.1 发布特征流程

```
┌──────────────────────────────────────────────────────────────┐
│ 步骤1: 开启 DB 事务                                            │
│                                                              │
│   ├── 更新特征表                                               │
│   │   UPDATE feature SET status = 'PUBLISHED',                │
│   │          version = version + 1, update_time = NOW()       │
│   │   WHERE txn_code = ? AND feature_code = ?                │
│   │                                                          │
│   ├── 更新交易码表                                             │
│   │   UPDATE transaction SET version = version + 1,           │
│   │          update_time = NOW()                              │
│   │   WHERE txn_code = ?                                     │
│   │                                                          │
│   └── 写入待同步记录表                                         │
│       INSERT INTO feature_sync_record                         │
│       (txn_code, feature_code, action, version, status,       │
│        retry_count, create_time)                              │
│       VALUES (?, ?, 'PUBLISH', ?, 'PENDING', 0, NOW())        │
│                                                              │
──────────────────────────────────────────────────────────────┤
│ 步骤2: 提交 DB 事务                                            │
│   → 此时 DB 数据已持久化，即使后续 Redis 写入失败也可补偿         │
│                                                              │
├──────────────────────────────────────────────────────────────┤
│ 步骤3: 写 Redis                                               │
│                                                              │
│   ├── 编译表达式并序列化                                       │
│   │   Expression exp = evaluator.compile(expression)         │
│   │   byte[] bytes = serialize(exp)                          │
│   │                                                          │
│   ├── SET 特征字节                                            │
│   │   SET expr:txn:data:{txnCode}:{featureCode} → bytes      │
│   │                                                          │
│   ├── HSET 交易码骨架                                         │
│   │   HSET expr:txn:{txnCode} {featureCode} → {newVersion}   │
│   │                                                          │
│   └── HINCRBY 全局版本索引                                    │
│       HINCRBY expr:txn:versions {txnCode} 1                  │
│                                                              │
├──────────────────────────────────────────────────────────────┤
│ 步骤4: 更新待同步记录                                          │
│   UPDATE feature_sync_record                                  │
│   SET status = 'SUCCESS', update_time = NOW()                 │
│   WHERE id = ?                                                │
│                                                              │
├──────────────────────────────────────────────────────────────┤
│ 步骤5: 异常处理（Redis 写入失败）                                │
│                                                              │
│   ├── 更新待同步记录                                           │
│   │   UPDATE feature_sync_record                              │
│   │   SET status = 'FAILED', retry_count = retry_count + 1,  │
│   │       error_msg = ?, update_time = NOW()                 │
│   │   WHERE id = ?                                           │
│   │                                                          │
│   └── 补偿任务将在下次轮询时重试（最多3次）                       │
└──────────────────────────────────────────────────────────────┘
```

### 3.2 下线特征流程

```
──────────────────────────────────────────────────────────────┐
│ 步骤1: 开启 DB 事务                                            │
│                                                              │
│   ├── 更新特征表                                               │
│   │   UPDATE feature SET status = 'OFFLINE',                  │
│   │          update_time = NOW()                              │
│   │   WHERE txn_code = ? AND feature_code = ?                │
│   │                                                          │
│   ├── 更新交易码表                                             │
│   │   UPDATE transaction SET version = version + 1,           │
│   │          update_time = NOW()                              │
│   │   WHERE txn_code = ?                                     │
│   │                                                          │
│   └── 写入待同步记录表                                         │
│       INSERT INTO feature_sync_record                         │
│       (txn_code, feature_code, action, version, status,       │
│        retry_count, create_time)                              │
│       VALUES (?, ?, 'OFFLINE', ?, 'PENDING', 0, NOW())        │
│                                                              │
├──────────────────────────────────────────────────────────────┤
│ 步骤2: 提交 DB 事务                                            │
│                                                              │
├──────────────────────────────────────────────────────────────┤
│ 步骤3: 写 Redis（清理）                                        │
│                                                              │
│   ├── DEL 特征字节                                            │
│   │   DEL expr:txn:data:{txnCode}:{featureCode}              │
│   │                                                          │
│   ├── HDEL 交易码骨架中的特征                                  │
│   │   HDEL expr:txn:{txnCode} {featureCode}                  │
│   │                                                          │
│   └── HINCRBY 全局版本索引                                    │
│       HINCRBY expr:txn:versions {txnCode} 1                  │
│                                                              │
├──────────────────────────────────────────────────────────────┤
│ 步骤4: 判断交易码下是否还有特征                                  │
│                                                              │
│   ├── HLEN expr:txn:{txnCode}                                │
│   │                                                          │
│   ├── 如果 = 0（无特征）                                       │
│   │   ├── DEL expr:txn:{txnCode}                             │
│   │   └── HDEL expr:txn:versions {txnCode}                   │
│   │                                                          │
│   └── 如果 > 0（还有特征）                                     │
│       └── 不做额外清理                                        │
│                                                              │
├──────────────────────────────────────────────────────────────┤
│ 步骤5: 更新待同步记录                                          │
│   UPDATE feature_sync_record                                  │
│   SET status = 'SUCCESS', update_time = NOW()                 │
│   WHERE id = ?                                                │
└──────────────────────────────────────────────────────────────┘
```

### 3.3 特殊设计说明

#### 为什么先写 DB 再写 Redis？

| 顺序 | 优点 | 缺点 |
|------|------|------|
| **先 DB 后 Redis**（本方案） | DB 事务可回滚，数据源权威；Redis 失败可补偿 | Redis 短暂不一致 |
| 先 Redis 后 DB | 用户感知快 | DB 失败需回滚 Redis，回滚本身可能失败 |

**选择先 DB 后 Redis 的原因**：
1. DB 是权威数据源，事务性强，回滚机制成熟
2. Redis 写入失败可通过待同步记录表补偿，不会丢数据
3. 报文加工服务有版本号比对机制，即使 Redis 短暂不一致，下次定时任务也能修复
4. 管理台操作频率低（人工触发），短暂不一致可接受

#### 为什么需要待同步记录表？

DB 事务提交后，Redis 写入可能因以下原因失败：
- Redis 连接超时
- Redis 内存不足
- 网络抖动

待同步记录表的作用是**持久化记录这些失败操作**，确保不会丢失。补偿任务定期扫描失败记录并重试，最多重试 3 次，超过后告警人工介入。

---

## 四、待同步记录表设计

### 4.1 表结构

```sql
CREATE TABLE feature_sync_record (
    id            BIGINT       PRIMARY KEY AUTO_INCREMENT  COMMENT '主键',
    txn_code      VARCHAR(64)  NOT NULL                    COMMENT '交易码',
    feature_code  VARCHAR(64)  NOT NULL                    COMMENT '特征编码',
    action        VARCHAR(16)  NOT NULL                    COMMENT '操作类型: PUBLISH/OFFLINE',
    version       BIGINT       NOT NULL                    COMMENT '操作时的版本号',
    status        VARCHAR(16)  NOT NULL DEFAULT 'PENDING'  COMMENT '状态: PENDING/SUCCESS/FAILED/MANUAL_REQUIRED',
    retry_count   INT          NOT NULL DEFAULT 0          COMMENT '已重试次数',
    error_msg     VARCHAR(512) DEFAULT NULL                 COMMENT '失败原因',
    create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    INDEX idx_status_retry (status, retry_count),
    INDEX idx_txn_feature (txn_code, feature_code)
) COMMENT '特征同步待处理记录表';
```

### 4.2 状态流转

```
PENDING ──Redis写入成功──▶ SUCCESS
   │
   ──Redis写入失败──▶ FAILED ─重试成功──▶ SUCCESS
                            │
                            └──重试3次仍失败──▶ MANUAL_REQUIRED
                                                  │
                                                  ──人工处理──▶ SUCCESS
```

### 4.3 状态说明

| 状态 | 含义 | 处理方式 |
|------|------|---------|
| PENDING | 刚写入，等待 Redis 同步 | 立即执行 Redis 写入 |
| SUCCESS | Redis 同步成功 | 定期归档清理（保留7天） |
| FAILED | Redis 同步失败，待重试 | 补偿任务自动重试 |
| MANUAL_REQUIRED | 重试超限，需人工介入 | 告警通知，人工处理后手动更新状态 |

---

## 五、报文加工服务侧：定时同步流程

### 5.1 定时任务入口

```
触发频率：每 30 秒执行一次（可配置）
执行方式：单线程串行执行（避免并发同步导致缓存不一致）
并发控制：ReentrantLock.tryLock()，拿不到锁则跳过本轮
```

### 5.2 同步流程详细步骤

```
┌──────────────────────────────────────────────────────────────┐
│ 步骤1: 尝试获取同步锁                                          │
│                                                              │
│   if (!syncLock.tryLock()) {                                 │
│       logger.debug("上一轮同步仍在执行，跳过本轮");              │
│       return;                                                │
│   }                                                          │
│                                                              │
├──────────────────────────────────────────────────────────────┤
│ 步骤2: 从 Redis 获取所有交易码版本号                            │
│                                                              │
│   Map<Object, Object> redisVersions =                        │
│       redisUtils.hGetAll("expr:txn:versions");               │
│                                                              │
│   if (redisVersions == null || redisVersions.isEmpty()) {     │
│       // Redis 无数据，可能是首次启动或全部下线                  │
│       localCache = Collections.emptyMap();                    │
│       return;                                                │
│   }                                                          │
│                                                              │
├──────────────────────────────────────────────────────────────┤
│ 步骤3: 比对版本号，找出变化的交易码                              │
│                                                              │
│   List<String> changedTxnCodes = new ArrayList<>();           │
│   List<String> removedTxnCodes = new ArrayList<>();           │
│                                                              │
│   for (entry : redisVersions) {                              │
│       String txnCode = entry.key;                            │
│       long redisVersion = entry.value;                       │
│       Long localVersion = localVersions.get(txnCode);        │
│                                                              │
│       if (localVersion == null) {                            │
│           changedTxnCodes.add(txnCode);  // 新增交易码         │
│       } else if (localVersion < redisVersion) {              │
│           changedTxnCodes.add(txnCode);  // 版本变化           │
│       }                                                      │
│       // localVersion == redisVersion → 跳过                  │
│   }                                                          │
│                                                              │
│   // 找出本地有但 Redis 已删除的交易码                           │
│   for (String txnCode : localVersions.keySet()) {            │
│       if (!redisVersions.containsKey(txnCode)) {             │
│           removedTxnCodes.add(txnCode);                      │
│       }                                                      │
│   }                                                          │
│                                                              │
├──────────────────────────────────────────────────────────────┤
│ 步骤4: 增量同步变化的交易码                                     │
│                                                              │
│   for (String txnCode : changedTxnCodes) {                   │
│       syncTxnFeatures(txnCode);  // 见 5.3 节                 │
│   }                                                          │
│                                                              │
├──────────────────────────────────────────────────────────────┤
│ 步骤5: 清理已删除的交易码                                       │
│                                                              │
│   for (String txnCode : removedTxnCodes) {                   │
│       removeFromLocalCache(txnCode);                         │
│   }                                                          │
│                                                              │
├──────────────────────────────────────────────────────────────┤
│ 步骤6: 释放同步锁                                              │
│   syncLock.unlock();                                         │
└──────────────────────────────────────────────────────────────┘
```

### 5.3 单交易码精确同步（核心热路径）

```
┌──────────────────────────────────────────────────────────────┐
│ syncTxnFeatures(String txnCode)                              │
│                                                              │
│ 步骤1: HGETALL 交易码骨架                                     │
│   Map<Object, Object> redisFeatureVersions =                 │
│       redisUtils.hGetAll("expr:txn:{txnCode}");              │
│                                                              │
│ 步骤2: 取本地缓存                                             │
│   TxnExpressionContext localCtx = localCache.get(txnCode);   │
│   Map<String, FeatureVersionedExpression> localFeatures =    │
│       localCtx != null ? localCtx.getFeatures() : emptyMap;  │
│                                                              │
│ 步骤3: 比对找出变化的特征                                      │
│   List<String> changedFeatures = new ArrayList<>();           │
│   Set<String> redisFeatureCodes = new HashSet<>();            │
│                                                              │
│   for (entry : redisFeatureVersions) {                       │
│       String featCode = entry.key;                           │
│       long featVersion = entry.value;                        │
│       redisFeatureCodes.add(featCode);                       │
│                                                              │
│       FeatureVersionedExpression localFeat =                  │
│           localFeatures.get(featCode);                       │
│       if (localFeat == null ||                               │
│           localFeat.getVersion() != featVersion) {           │
│           changedFeatures.add(featCode);                     │
│       }                                                      │
│   }                                                          │
│                                                              │
│ 步骤4: 找出本地有但 Redis 已删除的特征                          │
│   List<String> removedFeatures = new ArrayList<>();           │
│   for (String localFeatCode : localFeatures.keySet()) {      │
│       if (!redisFeatureCodes.contains(localFeatCode)) {      │
│           removedFeatures.add(localFeatCode);                │
│       }                                                      │
│   }                                                          │
│                                                              │
│ 步骤5: MGET 批量拉取变化的特征字节                              │
│   List<String> dataKeys = changedFeatures.stream()           │
│       .map(fc -> "expr:txn:data:" + txnCode + ":" + fc)     │
│       .collect(toList());                                    │
│   List<byte[]> bytesList = redisUtils.multiGetBytes(dataKeys);│
│                                                              │
│ 步骤6: 反序列化 + 组装新特征 Map                               │
│   Map<String, FeatureVersionedExpression> newFeatureMap =    │
│       new ConcurrentHashMap<>(localFeatures);                │
│                                                              │
│   for (int i = 0; i < changedFeatures.size(); i++) {         │
│       byte[] bytes = bytesList.get(i);                       │
│       if (bytes == null || bytes.length == 0) {              │
│           logger.warn("特征字节为空，跳过");                    │
│           continue;                                          │
│       }                                                      │
│       Expression exp = deserialize(bytes);                   │
│       newFeatureMap.put(featCode,                            │
│           new FeatureVersionedExpression(featCode,            │
│               featVersion, exp));                            │
│   }                                                          │
│                                                              │
│ 步骤7: 清理已删除的特征                                        │
│   for (String featCode : removedFeatures) {                  │
│       newFeatureMap.remove(featCode);                        │
│   }                                                          │
│                                                              │
│ 步骤8: 构建新上下文，原子替换                                   │
│   TxnExpressionContext newCtx = new TxnExpressionContext(    │
│       txnCode, redisVersion, newFeatureMap);                 │
│   Map<String, TxnExpressionContext> newCache =               │
│       new ConcurrentHashMap<>(localCache);                   │
│   newCache.put(txnCode, newCtx);                             │
│   this.localCache = newCache;  // volatile 原子替换           │
│   localVersions.put(txnCode, redisVersion);                  │
└──────────────────────────────────────────────────────────────┘
```

### 5.4 特殊设计说明

#### 为什么使用 volatile + 原子替换而非 synchronized？

| 方案 | 读性能 | 写性能 | 一致性 |
|------|--------|--------|--------|
| volatile + 原子替换 | 无锁，极快 | 创建新 Map 后原子替换 | 最终一致 |
| synchronized | 读写互斥 | 需排队等待 | 强一致 |

**选择 volatile + 原子替换的原因**：
1. 读操作远多于写操作（查询频率 >> 同步频率），无锁读性能更好
2. 同步操作本身有 tryLock 保护，不会并发写
3. Java 的 volatile 写具有 happens-before 语义，保证其他线程立即看到新缓存
4. ConcurrentHashMap 保证特征 Map 内部的线程安全

#### 为什么同步失败时保留旧缓存？

同步过程中任何一步失败（Redis 连接断开、反序列化异常等），整个交易码的同步中止，**保留旧缓存不变**。原因：
1. 旧缓存虽然可能不是最新，但至少是可用的
2. 下次定时任务会继续重试
3. 避免因部分同步导致缓存数据不一致（如只更新了部分特征）

---

## 六、Redis 宕机降级策略

### 6.1 降级触发条件

| 场景 | 判断方式 | 触发阈值 |
|------|---------|---------|
| Redis 连接超时 | 连接池获取连接超时 | 连续 3 次超时 |
| Redis 命令执行失败 | 操作抛异常 | 连续 3 次异常 |
| Redis 慢查询 | 命令执行时间过长 | 连续 3 次 > 1s |

### 6.2 降级状态机

```
                    ┌──────────────┐
                    │   NORMAL     │  正常模式：从 Redis 同步
                    │  (正常模式)    │
                    └──────┬───────┘
                           │ 连续3次失败
                           ▼
                    ──────────────┐
                    │  DEGRADED    │  降级模式：从 DB 查询
                    │  (降级模式)    │
                    └──────┬───────┘
                           │ 定时探测成功
                           ▼
                    ┌──────────────
                    │  RECOVERING  │  恢复中：全量同步 Redis
                    │  (恢复中)      │
                    ──────┬───────┘
                           │ 全量同步成功
                           ▼
                    ──────────────┐
                    │   NORMAL     │  回到正常模式
                    └──────────────┘
```

### 6.3 降级时的查询流程

```
查询请求进入
  │
  ├── 正常模式：
  │   └── 走本地缓存（定时任务已同步，直接返回）
  │
  ── 降级模式：
      ├── 查本地缓存
      │   ├── 命中且版本号 >= DB 版本号 → 返回（缓存仍有效）
      │   └── 未命中或版本过旧 → 查 DB
      │       ├── SELECT * FROM feature
      │       │   WHERE txn_code = ? AND status = 'PUBLISHED'
      │       ├── 编译表达式
      │       ├── 更新本地缓存
      │       └── 返回结果
      │
      └── 记录降级日志（便于监控和排查）
```

### 6.4 降级恢复流程

```
Redis 恢复后：
  │
  ├── 定时健康探测发现 Redis 可用
  │   └── 尝试执行 HGETALL expr:txn:versions
  │
  ├── 切换到 RECOVERING 状态
  │   └── 执行一次全量同步
  │       ├── 从 Redis 拉取所有交易码数据
  │       ├── 与 DB 版本号比对，确保 Redis 数据是最新的
  │       │   └── 如果 Redis 版本 < DB 版本 → 说明 Redis 有脏数据
  │       │       └── 以 DB 为准，重新写入 Redis
  │       └── 全量替换本地缓存
  │
  ── 切换到 NORMAL 状态
      └── 恢复定时轮询同步
```

### 6.5 特殊设计说明

#### 为什么降级时仍优先查本地缓存？

即使 Redis 宕机，本地缓存中的数据可能仍然是有效的（版本号未变化）。直接查 DB 会增加数据库压力，且 DB 查询 + 编译表达式的耗时远高于本地缓存命中。

**判断缓存是否有效的逻辑**：
1. 本地缓存中有该交易码
2. 本地缓存的交易码版本号 >= DB 中该交易码的版本号
3. 满足以上条件 → 缓存有效，直接返回

#### 为什么恢复时需要全量同步而非增量？

Redis 宕机期间，管理台可能进行了多次发布/下线操作。虽然有待同步记录表保证最终一致，但 Redis 恢复后其数据可能不完整或过旧。全量同步可以确保 Redis 数据与 DB 完全一致，避免增量同步遗漏变更。

---

## 七、补偿机制

### 7.1 管理台侧补偿任务

```
触发频率：每 60 秒执行一次
执行方式：单线程串行

查询条件：
  SELECT * FROM feature_sync_record
  WHERE status = 'FAILED' AND retry_count < 3
  ORDER BY create_time ASC
  LIMIT 100

对每条记录：
  ├── 根据 action 执行对应的 Redis 操作
  │   ├── PUBLISH → 编译表达式 + 写 Redis
  │   └── OFFLINE → 清理 Redis 数据
  │
  ├── 成功 → UPDATE status = 'SUCCESS'
  │
  └── 失败 → UPDATE retry_count = retry_count + 1,
  │          error_msg = 最新错误信息
  │
  └── retry_count >= 3 → UPDATE status = 'MANUAL_REQUIRED'
      └── 发送告警通知
```

### 7.2 报文加工服务侧补偿

定时同步任务本身具有补偿效果：

```
场景：某次同步因网络抖动部分特征未同步

补偿机制：
  ├── 下次定时任务（30s后）再次轮询版本号
  ├── 发现本地版本号 < Redis 版本号
  ├── 重新执行增量同步
  └── 最多延迟 30 秒即可同步到最新数据
```

### 7.3 对账任务（兜底）

```
触发频率：每 5 分钟执行一次

流程：
  1. 查 DB：SELECT txn_code, MAX(version) FROM feature
     WHERE status = 'PUBLISHED' GROUP BY txn_code

  2. 查 Redis：HGETALL expr:txn:versions

  3. 逐交易码比对：
     ├── DB 有但 Redis 没有 → 补写 Redis
     ├── Redis 有但 DB 没有 → 清理 Redis
     └── 版本号不一致 → 以 DB 为准更新 Redis

  4. 记录对账日志：
     ├── 差异数量
     ├── 修复动作
     └── 耗时
```

**设计要点**：对账任务作为最后一道防线，即使待同步记录表和定时同步都出现问题，对账任务也能发现并修复不一致。对账频率较低（5分钟），避免对 DB 和 Redis 造成过大压力。

---

## 八、数据一致性保障总结

### 8.1 风险场景与应对措施

| 风险场景 | 发生概率 | 影响 | 应对措施 | 恢复时间 |
|---------|---------|------|---------|---------|
| DB 写成功，Redis 写失败 | 低 | Redis 短暂不一致 | 待同步记录表 + 补偿任务 | 最多60秒 |
| Redis 写成功，DB 写失败 | 极低 | Redis 有脏数据 | DB 事务回滚 + 对账任务清理 | 最多5分钟 |
| Redis 宕机 | 低 | 无法从 Redis 同步 | 降级查 DB | 即时降级 |
| Redis 恢复后数据过旧 | 中 | 缓存数据不一致 | 恢复时全量同步 + 版本号比对 | 一次全量同步时间 |
| 定时任务执行失败 | 低 | 同步延迟 | 下次轮询继续 | 最多30秒 |
| 网络抖动导致部分特征未同步 | 中 | 部分特征过旧 | 版本号比对 + 增量重试 | 最多30秒 |
| 补偿任务重试超限 | 极低 | 需人工介入 | 告警通知 + 人工处理 | 取决于响应时间 |

### 8.2 一致性级别

本方案实现的是**最终一致性**，而非强一致性。

| 维度 | 说明 |
|------|------|
| 一致性窗口 | 发布/下线后，报文加工服务最多延迟 30 秒感知到变更 |
| 降级时一致性 | Redis 宕机时，本地缓存可能不是最新，但通过 DB 查询兜底 |
| 数据权威性 | DB 是权威数据源，Redis 是缓存层，不一致时以 DB 为准 |

---

## 九、需要改动的文件清单

### 9.1 管理台侧

| 文件 | 类型 | 改动内容 |
|------|------|---------|
| `FeatureService.java` | 新建 | 发布/下线业务逻辑，DB 事务 + 待同步记录写入 |
| `FeatureSyncRecord.java` | 新建 | 待同步记录实体类（MyBatis-Plus Entity） |
| `FeatureSyncRecordMapper.java` | 新建 | MyBatis Mapper 接口 |
| `FeatureSyncCompensateTask.java` | 新建 | 补偿定时任务，扫描 FAILED 记录并重试 |
| `FeatureReconcileTask.java` | 新建 | 对账定时任务，DB 与 Redis 数据比对修复 |
| `RedisPublisherService.java` | 修改 | 简化，去掉广播逻辑，只保留 Redis 写入 |
| `BroadcastSyncCoordinator.java` | 删除/注释 | 不再使用广播机制 |
| `GrpcBroadcastService.java` | 删除/注释 | 不再使用 gRPC 广播 |
| `FeignBroadcastService.java` | 删除/注释 | 不再使用 Feign 广播 |
| `RedisMessageListener.java` | 删除/注释 | 不再使用 Redis Pub/Sub |
| `ExpressionSyncService.java` | 删除/注释 | 不再使用广播同步 |

### 9.2 报文加工服务侧

| 文件 | 类型 | 改动内容 |
|------|------|---------|
| `AviatorRedisExpressionCache.java` | 修改 | 增加降级查 DB 逻辑、健康探测、状态切换 |
| `FeatureDbFallback.java` | 新建 | Redis 降级时的 DB 查询和表达式编译逻辑 |
| `RedisHealthChecker.java` | 新建 | Redis 健康探测，触发降级/恢复状态切换 |
| `ScheduledSyncTask.java` | 新建 | 定时同步任务入口（如未独立则合并到 Cache 类） |

### 9.3 数据库变更

| 表 | 操作 | 说明 |
|------|------|------|
| `feature` | 确认有 version 字段 | 特征表需有版本号字段用于比对 |
| `transaction` | 确认有 version 字段 | 交易码表需有版本号字段 |
| `feature_sync_record` | 新建 | 待同步记录表（见第四章） |

---

## 十、配置项

### 10.1 管理台侧配置

```yaml
# application.yml 新增
feature:
  sync:
    compensate:
      enabled: true
      cron: "0 */1 * * * *"        # 补偿任务：每1分钟
      max-retry: 3                  # 最大重试次数
    reconcile:
      enabled: true
      cron: "0 */5 * * * *"        # 对账任务：每5分钟
```

### 10.2 报文加工服务侧配置

```yaml
# application.yml 新增
feature:
  cache:
    sync:
      enabled: true
      cron: "0/30 * * * * *"       # 同步任务：每30秒
    degrade:
      enabled: true
      failure-threshold: 3          # 连续失败3次触发降级
      probe-interval: 10000         # 降级后探测间隔：10秒
    db-fallback:
      enabled: true                 # 是否启用 DB 降级查询
```

---

## 十一、监控与告警

### 11.1 关键监控指标

| 指标 | 说明 | 告警阈值 |
|------|------|---------|
| `feature_sync_failed_count` | 同步失败记录数 | > 10 条未处理 |
| `feature_sync_retry_exceeded` | 重试超限记录数 | > 0 即告警 |
| `redis_degrade_active` | Redis 降级状态 | 降级超过 5 分钟 |
| `cache_sync_duration_ms` | 单次同步耗时 | > 5000ms |
| `cache_hit_rate` | 本地缓存命中率 | < 95% |
| `reconcile_diff_count` | 对账发现差异数 | > 0 持续3次 |

### 11.2 告警方式

| 级别 | 场景 | 通知方式 |
|------|------|---------|
| WARN | 补偿任务重试失败 | 钉钉/企业微信群 |
| ERROR | 重试超限需人工介入 | 钉钉 + 邮件 + 电话 |
| ERROR | Redis 降级超过 5 分钟 | 钉钉 + 邮件 |
| INFO | 对账发现并修复差异 | 日志记录 |

---

## 十二、性能评估

### 12.1 管理台侧

| 操作 | 耗时 | 说明 |
|------|------|------|
| 发布特征（DB + Redis） | ~50-100ms | DB 事务 ~20ms + Redis 写入 ~30ms + 序列化 ~20ms |
| 下线特征（DB + Redis） | ~30-50ms | DB 事务 ~20ms + Redis 清理 ~20ms |
| 补偿任务单次重试 | ~50ms | 与发布类似 |

### 12.2 报文加工服务侧

| 操作 | 耗时 | 说明 |
|------|------|------|
| 定时同步（无变化） | ~5-10ms | 仅 HGETALL 版本号比对 |
| 定时同步（1个交易码变化） | ~20-50ms | 版本号比对 + MGET + 反序列化 |
| 定时同步（全量加载） | ~200-500ms | 取决于交易码和特征数量 |
| 降级查 DB（单交易码） | ~10-30ms | DB 查询 + 表达式编译 |
| 降级查 DB（全量） | ~100-300ms | 全量查询 + 批量编译 |

### 12.3 Redis 内存估算

| 数据项 | 单条大小 | 估算公式 | 示例（100交易码×300特征） |
|--------|---------|---------|------------------------|
| 交易码版本索引 | ~50B/条 | txnCode数 × 50B | ~5KB |
| 交易码骨架 | ~60B/特征 | 特征总数 × 60B | ~18KB |
| 特征字节 | ~5-10KB/特征 | 特征总数 × 7.5KB | ~2.25GB |
| **合计** | - | - | **~2.25GB** |

---

## 十三、扩展性考虑

### 13.1 多实例部署

管理台多实例部署时，待同步记录表天然支持多实例竞争：
- 补偿任务使用 `SELECT ... FOR UPDATE` 或乐观锁防止重复处理
- 对账任务多实例同时执行不会产生冲突（操作是幂等的）

### 13.2 特征数量增长

当前设计支持单交易码下最多 1000 个特征（一般 < 500）：
- 交易码骨架 Hash 字段数 < 1000，Redis Hash 性能无影响
- MGET 批量拉取 1000 个 Key 耗时 ~50ms，可接受
- 如特征数量继续增长，可考虑按交易码分片存储

### 13.3 表达式复杂度

Aviator 表达式编译后的字节大小与表达式复杂度正相关：
- 简单表达式（比较/算术）：~2-5KB
- 复杂表达式（嵌套函数/正则）：~10-50KB
- 超大表达式（> 100KB）：建议拆分或优化
