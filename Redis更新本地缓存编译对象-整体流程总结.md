# Redis 更新本地缓存编译对象 - 整体流程总结

## 一、系统整体架构

```
┌─────────────────────┐                      ┌──────────────────────────┐
│   特征管理台服务     │                      │   统一报文加工服务        │
│                     │                      │                          │
│  - 配置/测试/发布    │                      │  - 执行表达式             │
│  - 写入 DB + Redis  │ ──────Redis────────► │  - 同步缓存              │
│                     │                      │                          │
└─────────────────────┘                      └──────────────────────────┘
         │                                              │
         ▼                                              ▼
   feature_expression                              localCache
   (BLOB 字节)                            (Map<TxnCode, TxnContext>)
```

**两端职责**：
- **特征管理台**：编译、序列化、入库、入 Redis
- **统一报文加工服务**：从 Redis 拉字节、反序列化为 Expression、更新本地缓存

---

## 二、Redis Key 结构

```
Key: expr:txn:versions                       Hash    全局交易码版本索引
  Field: {txnCode}  →  {txnVersion}

Key: expr:txn:{txnCode}                      Hash    交易码骨架
  Field: {featureCode}  →  {featureVersion}

Key: expr:txn:data:{txnCode}:{featureCode}   String  特征字节（二进制，5-10KB）
```

**特性**：
- 索引与数据分离：轻量比对、快速定位
- 字节用 String 直接存原始 `byte[]`（不用 Base64）
- 同一个特征版本号存在于骨架 Hash，字节单独 Key

---

## 三、写入流程（特征管理台）

### 3.1 新增特征 `putFeature(txnCode, featureCode, expression)`

```
输入：交易码、特征编码、表达式源码

步骤 1: 编译 + 序列化
  evaluator.compile(expression)
    → Expression 对象
  AviatorObjectOutputStream.writeObject(exp)
    → byte[] bytes (5-10KB)

步骤 2: Redis 事务
  MULTI
    SET   expr:txn:data:{txnCode}:{featureCode}  bytes
    HSET  expr:txn:{txnCode}  {featureCode}  1
    HINCRBY expr:txn:versions  {txnCode}  1
  EXEC

步骤 3: 数据库持久化（异步或同步）
  UPDATE feature_expression SET expression_bytes = bytes WHERE feature_code = ?
```

### 3.2 更新特征 `updateFeature(txnCode, featureCode, expression)`

```
与新增几乎相同，区别：
  HSET 替换为 HINCRBY（特征版本号 +1）
```

### 3.3 删除特征 `removeFeature(txnCode, featureCode)`

```
步骤 1: Redis 事务
  MULTI
    DEL   expr:txn:data:{txnCode}:{featureCode}
    HDEL  expr:txn:{txnCode}  {featureCode}
    HINCRBY expr:txn:versions  {txnCode}  1
  EXEC

步骤 2: 事务后判断
  HLEN expr:txn:{txnCode}  → 0 ?
    是：
      DEL   expr:txn:{txnCode}
      HDEL  expr:txn:versions  {txnCode}
    否：
      保留
```

### 3.4 删除交易码 `removeTxn(txnCode)`

```
步骤 1: HKEYS expr:txn:{txnCode}  → 拿到所有特征编码

步骤 2: Redis 事务
  MULTI
    DEL   expr:txn:{txnCode}
    HDEL  expr:txn:versions  {txnCode}
    DEL   expr:txn:data:{txnCode}:{feat1}
    DEL   expr:txn:data:{txnCode}:{feat2}
    ...
  EXEC
```

---

## 四、读取同步流程（统一报文加工服务）

### 4.1 启动全量加载

```
触发：ApplicationReadyEvent

1. HGETALL expr:txn:versions
   → 拿到所有交易码版本号 Map

2. 遍历每个交易码：
   a. HGETALL expr:txn:{txnCode}
      → 拿到所有特征版本号 Map
   b. MGET expr:txn:data:{txnCode}:feat1, feat2, ...
      → 批量拉所有特征字节
   c. 逐个反序列化 byte[] → Expression
   d. 组装 TxnExpressionContext

3. 构建完整 localCache，原子替换
   this.localCache = newCache
```

### 4.2 定时增量轮询（每10秒）

```
1. HGETALL expr:txn:versions
   → 拿到所有版本号

2. 与本地 localVersions 比对：
   - 新增的（Redis有、本地无） → 加载
   - 删除的（本地有、Redis无） → 移除
   - 修改的（版本号不一致） → 精确同步

3. 收集 changedTxnCodes 列表

4. 尝试获取 reloadLock（tryLock）
   拿不到 → 直接返回（等下轮）

5. 加锁后双重检查

6. 对每个 changedTxnCode 执行 syncTxnFeatures

7. 释放锁
```

### 4.3 单交易码精确同步（核心热路径）

```
输入：txnCode
目标：只更新变化的特征，不动其他

步骤 1: 取交易码骨架（轻量）
  HGETALL expr:txn:{txnCode}
  → Map<featureCode, featureVersion>
  → 数据量：约 N × 几十字节 = 几KB

步骤 2: 取本地缓存
  localCache.get(txnCode)
  → Map<featureCode, FeatureVersionedExpression>

步骤 3: 比对找出变化的特征
  for each (featCode, redisVersion) in redisData:
    local = localMap.get(featCode)
    if local == null OR local.version != redisVersion:
      changedFeatures.add(featCode)

步骤 4: 按需取变化的特征字节（重量级）
  if changedFeatures 非空:
    keys = [expr:txn:data:{txnCode}:{fc} for fc in changedFeatures]
    MGET keys
    → List<byte[]>

步骤 5: 反序列化 + 更新本地
  for i, featCode in changedFeatures:
    bytes = bytesList[i]
    Expression expr = deserialize(bytes)  // AviatorObjectInputStream
    localMap.put(featCode, new FeatureVersionedExpression(featCode, version, expr))

步骤 6: 清理本地已删除的特征
  for each featCode in localMap:
    if featCode not in redisData:
      localMap.remove(featCode)

步骤 7: 更新交易码版本号
  ctx.version = redisTxnVersion
```

### 4.4 查询时按需刷新

```
getByTxnCode(txnCode):

  1. HGET expr:txn:versions {txnCode}
     → redisTxnVersion

  2. 与本地版本比对
     if local.version == redisTxnVersion:
       return localCache.get(txnCode)  // 命中

  3. 版本不一致 → syncTxnFeatures(txnCode)
     return localCache.get(txnCode)
```

---

## 五、并发控制

### 5.1 全局锁 reloadLock

```java
private final ReentrantLock reloadLock = new ReentrantLock();

// 轮询 / 查询时：
if (reloadLock.tryLock()) {
    try {
        // 双重检查版本号
        // 同步
    } finally {
        reloadLock.unlock();
    }
}
// 拿不到锁 → 用旧数据，下轮再试
```

**为什么用 tryLock 而不是 lock()？**
- 锁等待会阻塞查询线程，影响业务
- 拿不到锁就用旧数据，符合最终一致性
- 下一个轮询周期（10秒）会重试

### 5.2 volatile 读 + 整体替换

```java
private volatile Map<String, TxnExpressionContext> localCache = new HashMap<>();

// 替换：构建新 Map 后整体赋值
this.localCache = newCache;  // 读线程立即可见
```

**为什么不增量修改 Map？**
- 避免读线程看到"半新半旧"的中间状态
- 整体替换是原子的（volatile 写）

### 5.3 双重检查模式

```java
if (reloadLock.tryLock()) {
    try {
        // 重新检查版本号
        if (localVersion != redisVersion) {
            syncTxnFeatures(txnCode);
        }
    } finally {
        reloadLock.unlock();
    }
}
```

避免加锁后才发现"其他线程已经同步好了"。

---

## 六、数据生命周期

```
        特征管理台                       Redis                          本地缓存
        ┌────────┐                  ┌──────────────┐                ┌──────────────┐
        │ 编译   │  ───序列化──►    │  bytes:      │   ──轮询──►   │ Expression   │
        │        │                  │  version:    │                │              │
        └────────┘                  └──────────────┘                └──────────────┘
              │                            │                              │
              │                            │                              ▼
              │                            │                       报文加工时执行
              │                            │                              │
              ▼                            ▼                              │
        ┌────────────┐               ┌──────────────┐                     │
        │  MySQL DB  │               │  TTL: 永久    │                     │
        │  BLOB字节  │               │  (业务维护)   │                     │
        └────────────┘               └──────────────┘                     │
              ▲                                                             │
              │                                                             │
              └─────────────── 灾难恢复：DB 重建 Redis ─────────────────────┘
```

**特点**：
- Redis 是中心，下游不直接读 DB
- 启动/轮询是常规同步路径
- 查询时按需同步作为兜底（轮询间隙内的变化）

---

## 七、性能关键点

| 优化点 | 实现 |
|--------|------|
| 版本比对轻量 | Hash 只存版本号字符串，单次 HGETALL 几KB |
| 字节按需取 | 哪个特征变了取哪个，不批量取无关特征 |
| 字节存原始格式 | 无 Base64 转换，省 CPU 省 33% 存储 |
| 锁竞争小 | tryLock 不等待，并发查询不受影响 |
| Hash 紧凑存储 | Redis 内部 ziplist，< 1000字段性能最优 |

---

## 八、异常与容错

| 异常场景 | 处理策略 |
|---------|---------|
| Redis 连接超时 | 重试 3 次后降级到 DB 读取 |
| 单个特征反序列化失败 | 打 warn 日志，跳过该特征，不影响其他 |
| 同步过程 Redis 异常 | 保留旧本地缓存，下轮重试 |
| 启动时 Redis 不可用 | 应用启动失败（fail fast），不进入运行时 |
| 写事务部分失败 | MULTI/EXEC 保证原子性，要么全成要么全败 |
| 字节丢失（被外部删除） | 加载时 null 检查，降级为"特征不存在" |

---

## 九、关键文件清单

| 文件 | 角色 |
|------|------|
| `ExprRedisKeys.java` | Key 常量定义 |
| `TxnExpressionContext.java` | 上下文（含 version + features Map） |
| `FeatureVersionedExpression`（新增） | 含 version 字段的表达式封装 |
| `AviatorRedisExpressionCache.java` | 核心缓存类 |
| `RedisConfig.java` | 新增 `bytesRedisTemplate` Bean |
| `RedisUtils.java` | 新增 `setBytes/getBytes/multiGetBytes` |

---

## 十、改代码 checklist

### 10.1 ExprRedisKeys
- [ ] 删除旧的 `VERSION` / `FEATURE_PREFIX` / `TXN_PREFIX`
- [ ] 新增 `TXN_VERSIONS_KEY`（全局版本索引 Hash）
- [ ] 新增 `TXN_SKELETON_PREFIX`（交易码骨架 Hash 前缀）
- [ ] 新增 `TXN_DATA_PREFIX`（特征字节 Key 前缀）
- [ ] 新增 `txnSkeletonKey(txnCode)` 方法
- [ ] 新增 `featureDataKey(txnCode, featureCode)` 方法
- [ ] 新增 `txnVersionKey()` 方法（返回全局版本 Hash Key）

### 10.2 TxnExpressionContext
- [ ] `features` 字段从 `List<FeatureExpression>` 改为 `Map<String, FeatureVersionedExpression>`
- [ ] 新增 `version` 字段（long）
- [ ] 新增三参数构造函数（txnCode, version, features）
- [ ] 更新 equals/hashCode/toString

### 10.3 FeatureVersionedExpression（新增内嵌类）
- [ ] 字段：`featureCode`（String）
- [ ] 字段：`version`（long）
- [ ] 字段：`expression`（Expression）
- [ ] getter/setter
- [ ] equals/hashCode/toString

### 10.4 AviatorRedisExpressionCache
- [ ] `localCache` 类型改为 `Map<String, TxnExpressionContext>`
- [ ] 新增 `localVersions`（ConcurrentHashMap<String, Long>）
- [ ] `putFeature` 接口加 `txnCode` 参数
- [ ] `updateFeature` 接口加 `txnCode` 参数
- [ ] `removeFeature` 去掉 `deleteBytes` 参数
- [ ] 所有序列化/反序列化使用 `byte[]`（不 Base64）
- [ ] 写入：`SET bytes` + `HSET` + `HINCRBY` 事务
- [ ] 重写 `fullReloadInternal()`（骨架 + MGET 字节）
- [ ] 重写 `pollAndReload()`（HGETALL 索引 + 增量同步）
- [ ] 新增 `syncTxnFeatures(txnCode)`（单交易码精确同步）
- [ ] 重写 `ensureFreshCache(txnCode)`（单交易码版本检查）
- [ ] 重写 `loadFromRedis(txnCode)`（骨架 + MGET 字节）
- [ ] 重写 `getByTxnCode(txnCode)`

### 10.5 RedisConfig（新增或修改）
- [ ] 新增 `RedisTemplate<String, byte[]> bytesRedisTemplate` Bean
- [ ] KeySerializer = StringRedisSerializer
- [ ] ValueSerializer = ByteArrayRedisSerializer

### 10.6 RedisUtils
- [ ] 新增 `setBytes(String key, byte[] value)` 方法
- [ ] 新增 `byte[] getBytes(String key)` 方法
- [ ] 新增 `List<byte[]> multiGetBytes(List<String> keys)` 方法
- [ ] 新增 `hGetAll(String key)` 方法（Hash 操作）
- [ ] 新增 `hSet(String key, String field, String value)` 方法
- [ ] 新增 `hIncrBy(String key, String field, long delta)` 方法
- [ ] 新增 `hDel(String key, String... fields)` 方法
- [ ] 新增 `hLen(String key)` 方法

### 10.7 特征管理台发布代码
- [ ] 发布接口调用新的 `putFeature/updateFeature`（带 txnCode）
- [ ] 数据库同步存储 `byte[]`（BLOB 字段）
