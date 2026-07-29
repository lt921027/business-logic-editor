# Groovy 特征表达式合并方案

> 本文档记录"将同一交易码下所有特征表达式合并为一个交易码级表达式"方案的思考过程与设计结果，供后续实施参考。

---

## 一、背景与动机

### 1.1 业务执行链路

```
输入数据(JSON)
  → 按交易码查找所有特征
  → 逐个执行特征表达式，每个返回一个值
  → 汇总为 Map<featureCode, value>
  → 转为 JSON 返回
```

交易码是粗粒度分组（如"贷款申请"），特征码是细粒度规则（如"金额校验"、"日期判断"）。一个交易码下通常有数十个特征。

### 1.2 问题

当前实现中，每个特征独立编译为一个 `CompiledGroovyScript`（内部持有一个 `Class<? extends Script>`）。特征数量多时，Groovy 的 Class 对象比 Aviator 的 Expression 对象"重"得多，导致 Metaspace 内存占用过大。

---

## 二、内存问题量化分析

### 2.1 Groovy Class 在 Metaspace 中的组成

每个 Groovy 生成的 Class 在 Metaspace 中占用的空间分为**固定开销**和**可变开销**两部分：

#### 固定开销（与源码复杂度无关，每个 Class 都有）

| 组成部分 | 说明 | 估算大小 |
|---------|------|---------|
| Class 对象本身 | JVM Class 结构、字段表、方法表 | ~5-8 KB |
| MetaClass | Groovy 动态分派核心，每个 Class 必生成 | ~3-5 KB |
| CallSite 数组 | Groovy 动态方法调用缓存 | ~2-3 KB |
| Method 元数据 | `run()` 方法的反射信息 | ~1-2 KB |
| **固定开销小计** | | **~12-18 KB/Class** |

#### 可变开销（与源码逻辑量成正比）

| 组成部分 | 说明 | 估算大小 |
|---------|------|---------|
| 字节码 | 实际执行逻辑编译后的 JVM 指令 | ~1-3 KB/特征 |
| 常量池 | 字符串字面量、方法引用等 | ~0.5-1 KB/特征 |
| 异常表 | try-catch 生成的跳转表 | ~0.1-0.2 KB/特征 |

### 2.2 合并前后对比（修正版）

假设 5 个交易码，每个交易码 50 个特征：

#### 当前（特征级编译）

```
每个特征 Class:
  固定开销: ~15 KB
  可变开销: ~3 KB（1个特征的字节码+常量池）
  合计: ~18 KB/Class

250 个 Class × 18 KB = 4,500 KB ≈ 4.4 MB
```

#### 合并后（交易码级编译）

```
每个交易码 Class:
  固定开销: ~15 KB（只算1次）
  可变开销: 50 × 3 KB = 150 KB（50个特征的字节码）
  try-catch 额外: 50 × 0.2 KB = 10 KB
  LinkedHashMap 构建: ~3 KB
  合计: ~178 KB/Class

5 个 Class × 178 KB = 890 KB ≈ 0.87 MB
```

#### 对比表

| 组成部分 | 当前（250 Class） | 合并后（5 Class） | 变化 |
|---------|------------------|------------------|------|
| 固定元数据开销 | 250 × 15KB = **3,750 KB** | 5 × 15KB = **75 KB** | **-3,675 KB** |
| 可变字节码（逻辑） | 250 × 3KB = **750 KB** | 5 × 163KB = **815 KB** | **+65 KB** |
| **合计** | **4,500 KB** | **890 KB** | **-3,610 KB（-80%）** |

### 2.3 关键结论

- **内存减少约 80%**（而非 97%），主要收益来自**消除 Class 固定元数据开销**
- **实际逻辑字节码增加了约 8.7%**（try-catch 包裹 + LinkedHashMap 构建）
- 如果特征更复杂（每个特征字节码更大），减少比例会降低（因为可变部分占比增大）
- 如果特征更简单（每个特征只有几行），减少比例会更高（因为固定部分占比更大）

---

## 三、合并方案核心设计

### 3.1 合并后的脚本结构

每个特征用 `try-catch` 包裹，异常时写入 `null` 作为默认值，保证单特征异常不影响整体：

```groovy
def results = new LinkedHashMap()

// ===== 特征: featureA (源码hash: a1b2c3) =====
try {
    def featureA = JsonPathUtil.read(inputData, '$.amount') > 1000
    results['featureA'] = featureA
} catch (Exception e) {
    results['featureA'] = null
}

// ===== 特征: featureB (源码hash: d4e5f6) =====
try {
    def featureB = GroovyDateFunctions.withinLast3Months(JsonPathUtil.read(inputData, '$.date'))
    results['featureB'] = featureB
} catch (Exception e) {
    results['featureB'] = null
}

return results
```

**设计要点**：
- 每个特征用 `try-catch` 包裹，异常时写入 `null` 作为默认值
- 特征间变量名以 `featureCode` 命名，避免冲突
- 使用 `LinkedHashMap` 保持特征顺序（便于调试和结果可读性）
- 注释中标注源码 hash，便于定位问题特征

### 3.2 默认值策略

| 特征返回类型 | 建议默认值 | 理由 |
|-------------|-----------|------|
| 布尔型（条件判断） | `null` | null 表示"计算失败"，与 false（条件不满足）区分 |
| 数值型（金额、次数） | `null` | null 表示"无数据"，与 0（值为零）区分 |
| 字符串型 | `null` | 统一用 null 表示缺失 |

**建议**：先用 `null`，后续可通过特征配置支持自定义默认值（如特征表增加 `default_value` 字段）。

---

## 四、Redis 缓存结构变化

### 4.1 当前结构（3 个 Key）

```
groovy-expr:txn:versions                     Hash    全局交易码版本索引
groovy-expr:txn:{txnCode}                    Hash    特征版本号骨架
groovy-expr:txn:data:{txnCode}:{featureCode} String  特征源码
```

### 4.2 合并后结构（2 个 Key）

```
groovy-expr:txn:versions                     Hash    全局交易码版本索引
groovy-expr:txn:data:{txnCode}               String  合并后的交易码脚本源码
```

**变化**：移除了中间的骨架 Hash Key，不再需要特征级版本号。

---

## 五、版本号管理

升级为交易码级版本号。关键问题：**单个特征变更时，交易码版本号如何变化？**

| 策略 | 说明 | 问题 |
|------|------|------|
| 取所有特征的最大版本号 | 简单 | 特征删除时最大版本号可能不变，无法检测变更 |
| **每次发布操作自增** | 可靠 | 推荐：增/删/改任何特征都让交易码版本号 +1 |

**采用"每次发布自增"**：发布服务在处理特征变更（增/删/改）后，先合并生成新脚本，然后将交易码版本号 +1 写入 Redis。这样无论什么变更都能被增量同步检测到。

---

## 六、特征源码持久化

Redis 只存合并后的交易码脚本，单个特征的源码存储在数据库中（如 `feature` 表），包含字段：

- `txn_code`：交易码
- `feature_code`：特征编码
- `expression`：Groovy 表达式源码
- `version`：特征版本号
- `status`：状态（有效/失效）

**发布流程**：
1. 特征变更写入数据库
2. 发布服务查询该交易码下所有有效特征
3. 调用合并器生成交易码脚本
4. 编译验证
5. 写入 Redis（交易码级 Key）+ 版本号自增
6. 通知各节点增量同步

---

## 七、本地缓存结构变化

### 7.1 当前（两层嵌套）

```
GroovyRedisExpressionCache:
  Map<txnCode, GroovyTxnExpressionContext>
    └── Map<featureCode, FeatureVersionedExpression>
        └── CompiledGroovyScript
```

### 7.2 合并后（单层）

```
GroovyRedisExpressionCache:
  Map<txnCode, TxnCacheEntry>
    └── CompiledGroovyScript  (直接持有合并后的脚本)
```

**变化**：
- `GroovyTxnExpressionContext` 简化，移除 `Map<String, FeatureVersionedExpression>`
- `GroovyFeatureExpressionCache` 的两阶段提交从特征级变为交易码级
- 不再需要 `getByTransactionCode` 返回特征级 Map

---

## 八、两阶段提交变化

### 8.1 当前（特征级）

```
Prepare(syncId, txnCode, featureCode, version, expression)
Commit(syncId, txnCode, featureCode)
```

### 8.2 合并后（交易码级）

```
Prepare(syncId, txnCode, version, mergedScript)
Commit(syncId, txnCode)
```

**变化**：Prepare 阶段直接传入合并后的交易码脚本，以交易码为粒度暂存和提交。

---

## 九、执行链路变化

### 9.1 当前

```
execute(txnCode, inputData)
  → 获取 Map<featureCode, FeatureVersionedExpression>
  → for each feature:
      → 创建 Script 实例 + Binding
      → script.run()
      → 收集结果
  → 返回 Map
```

执行开销：**O(N)**（N 为特征数）

### 9.2 合并后

```
execute(txnCode, inputData)
  → 获取 CompiledGroovyScript (交易码级)
  → 创建 Script 实例 + Binding (1次)
  → script.run() (1次)
  → 直接返回 Map (脚本内部已构建)
```

执行开销：**O(1)**

**收益**：执行开销从 O(N) 降为 O(1)。

---

## 十、增量同步变化

### 10.1 当前

1. 轮询 `txn:versions` Hash，比对交易码版本号
2. 版本号变化时，MGET 该交易码下所有特征的源码
3. 逐个编译并更新本地缓存

### 10.2 合并后

1. 轮询 `txn:versions` Hash，比对交易码版本号
2. 版本号变化时，GET 单个交易码脚本源码（1 次 GET 替代 N 次 MGET）
3. 编译 1 次并更新本地缓存

**收益**：Redis 操作从 N 次 MGET 降为 1 次 GET，网络开销大幅减少。

---

## 十一、需要新建/改造的文件

| 文件 | 操作 | 说明 |
|------|------|------|
| `GroovyTxnScriptMerger.java` | **新建** | 交易码脚本合并器，输入特征列表，输出合并脚本 |
| `GroovyExprRedisKeys.java` | 改造 | 移除 `TXN_SKELETON_PREFIX`，`featureDataKey` 改为 `txnDataKey` |
| `GroovyRedisExpressionCache.java` | 改造 | 缓存结构从 `Map<txn, Map<feature, FVE>>` 简化为 `Map<txn, TxnCacheEntry>` |
| `GroovyTxnExpressionContext.java` | 改造 | 移除 `FeatureVersionedExpression` 内部类，直接持有 `CompiledGroovyScript` |
| `GroovyFeatureExpressionCache.java` | 改造 | 两阶段提交从特征级变为交易码级 |
| `GroovyBusinessLogicService.java` | 改造 | 执行链路从循环执行特征改为单次执行交易码脚本 |

---

## 十二、潜在风险与应对

| 风险 | 影响 | 应对 |
|------|------|------|
| 合并后脚本过大 | 单个交易码特征极多（如 200+）时，脚本可能达几十 KB，编译变慢 | 设置单交易码特征数上限，或按子分组拆分 |
| 特征间变量名冲突 | 两个特征若都定义了 `def x`，合并后冲突 | 合并器将变量名重写为 `feature_{featureCode}_result` |
| 故障排查困难 | 异常被 try-catch 吞掉，难以定位 | catch 块中记录日志（需通过 Binding 注入 logger 或在结果中标记错误） |
| 特征引用前序特征结果 | 若业务需要特征B依赖特征A的结果 | 当前设计不支持，需确认业务是否需要 |

---

## 十三、待确认问题

实施前需确认：

1. 特征源码是否已有数据库表存储？还是需要新建？
2. 特征之间是否存在依赖关系（特征B需要引用特征A的结果）？
3. 默认值统一用 `null` 是否满足业务需求？

---

## 十四、总结

**合并方案可行且收益显著**：

1. **内存**：Metaspace 占用减少约 80%，主要收益来自消除 Class 固定元数据开销
2. **性能**：执行从 O(N) 降为 O(1)，Redis 同步从 N 次 GET 降为 1 次
3. **架构简化**：缓存结构从三层嵌套降为单层，两阶段提交粒度更粗但更简单
4. **故障隔离**：通过 try-catch + 默认值保证单特征异常不影响整体

### 设计决策汇总

| 决策点 | 选择 | 理由 |
|--------|------|------|
| 故障隔离策略 | 每个特征 try-catch，异常用默认值 | 保证可用性 |
| Redis 缓存结构 | 改为交易码级 | 简化结构，减少 Key 数量 |
| 版本号机制 | 升级为交易码级版本号，每次发布自增 | 与合并粒度一致，可靠检测变更 |
| 默认值 | 统一用 `null` | 表示"计算失败/无数据"，后续可扩展为自定义 |
