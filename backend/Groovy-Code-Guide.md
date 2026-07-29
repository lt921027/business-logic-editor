# Groovy 表达式引擎代码说明

本文档按**执行顺序**介绍 Groovy 代码在整体业务逻辑中的作用，帮助快速理解代码配置与调用链路。

---

## 一、整体架构概览

Groovy 引擎作为 Aviator 引擎的并行替代方案，独立运行，不依赖任何 Aviator 文件。整体分为以下层次：

```
┌─────────────────────────────────────────────────────────────┐
│                     Controller 层                            │
│  GroovyBusinessLogicController  /  GroovyEngineTestController│
│  GroovyFunctionRegistryController / GroovyRedisTestController│
└───────────────────────────┬─────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│                     Service 层                               │
│  GroovyBusinessLogicService（业务 CRUD + 执行编排）          │
└───────────────────────────┬─────────────────────────────────┘
                            ↓
┌──────────────┬────────────┴───────────┬────────────────────┐
│  生成器       │     执行器              │    缓存层          │
│  Generator   │  GroovyExecutor        │  ExpressionCache   │
│  (DTO→脚本)   │  GroovyExpressionEngine │  RedisCache        │
└──────────────┘                        │  FeatureCache      │
                                        ↓
┌─────────────────────────────────────────────────────────────┐
│              安全沙箱 + 函数注册中心                         │
│  GroovySandbox（AST 级安全限制）                             │
│  GroovyFunctionRegistry（热加载自定义函数）                  │
└─────────────────────────────────────────────────────────────┘
```

---

## 二、启动阶段（Spring Boot 初始化）

### 步骤 1：创建安全沙箱

**文件**：[GroovySandbox.java](file:///c:/Users/lt/Desktop/业务逻辑编辑/backend/src/main/java/com/businesslogic/groovy/security/GroovySandbox.java)

**作用**：在 AST 级别限制 Groovy 脚本能访问的类和方法，防止恶意脚本执行系统命令、文件操作、反射等危险操作。

**关键配置**：
- **白名单导入**（`IMPORTS_WHITELIST`）：仅允许 `java.util.*`、`java.math.*` 和项目工具类（`JsonPathUtil`、`StringUtil`、`GroovyDateFunctions`）
- **黑名单接收者**（`RECEIVERS_BLACKLIST`）：禁止 `System`、`Runtime`、`ProcessBuilder`、`ClassLoader`、`File`、`Socket` 等作为方法调用接收者
- **间接导入检查**：防止通过别名绕过白名单（如 `def R = java.lang.Runtime`）
- **预导入**（`ImportCustomizer`）：自动注入常用工具类，业务脚本无需写 import

**为何首先执行**：沙箱配置是后续所有编译操作的安全基础，必须在 `GroovyClassLoader` 创建前就绪。

---

### 步骤 2：初始化表达式引擎

**文件**：[GroovyExpressionEngine.java](file:///c:/Users/lt/Desktop/业务逻辑编辑/backend/src/main/java/com/businesslogic/groovy/engine/GroovyExpressionEngine.java)

**作用**：核心引擎，负责编译、执行、序列化 Groovy 脚本。

**初始化内容**：
1. 创建 `GroovySandbox` 实例，获取安全编译器配置
2. 创建 `GroovyClassLoader`，绑定沙箱配置
3. 初始化编译缓存 `ConcurrentHashMap<源码MD5, Script Class>`
4. 初始化函数注册表 `registeredFunctions` 和 `registeredStaticClasses`

**关键设计**：
- **编译缓存**：以源码 MD5 为 key，避免重复编译（Groovy parseClass 开销远高于 Aviator）
- **每次执行新建 Script 实例 + Binding**：保证线程安全，避免状态串扰

---

### 步骤 3：暴露引擎为 Spring Bean

**文件**：[GroovyEngineConfig.java](file:///c:/Users/lt/Desktop/业务逻辑编辑/backend/src/main/java/com/businesslogic/groovy/config/GroovyEngineConfig.java)

**作用**：Spring Boot 自动配置类，负责 Bean 装配。

**配置内容**：
1. **`groovyExpressionEngine()` Bean**：返回 `GroovyExecutor.getEngine()` 的全局单例（**不是** new 新实例），保证 Spring Bean 与 `GroovyExecutor` 静态方法使用的引擎一致
2. **`groovyFunctionRegistry()` Bean**：创建函数注册中心并调用 `init()` 完成初始化

**为何共享单例**：`GroovyExecutor` 的所有静态方法（`compile`/`execute`）都通过 `getEngine()` 获取引擎，若 new 新实例会导致编译缓存和函数注册状态分裂。

---

### 步骤 4：初始化函数注册中心

**文件**：[GroovyFunctionRegistry.java](file:///c:/Users/lt/Desktop/业务逻辑编辑/backend/src/main/java/com/businesslogic/groovy/hotload/GroovyFunctionRegistry.java)

**作用**：管理自定义函数的热加载，支持三种类型：
- **EXPRESSION**：表达式函数，编译为 `CompiledGroovyScript`，包装为 `ExpressionFunctionClosure`
- **SCRIPT**：脚本函数，先拼接参数声明再编译，包装为 `ScriptFunctionClosure`
- **JAVA**：Java 函数，直接包装 `GroovyFunction` 实例为 `JavaFunctionClosure`

**关键设计**：
- 所有函数最终以 `Closure` 形式通过 `engine.addFunction()` 注入到引擎全局函数表
- 注册/更新/注销操作使用 `synchronized` 保护，查询走 `ConcurrentHashMap` 无锁
- EXPRESSION/SCRIPT 类型提前编译验证，尽早发现语法错误

---

## 三、业务流程阶段

### 场景 A：保存业务逻辑

#### 步骤 1：接收请求

**文件**：[GroovyBusinessLogicController.java](file:///c:/Users/lt/Desktop/业务逻辑编辑/backend/src/main/java/com/businesslogic/groovy/controller/GroovyBusinessLogicController.java)

**入口**：`POST /groovy-business-logic`

**作用**：接收前端传入的 `BusinessLogicSaveDTO`（含步骤列表），委托给 Service 层处理。

---

#### 步骤 2：生成 Groovy 脚本

**文件**：[GroovyExpressionGenerator.java](file:///c:/Users/lt/Desktop/业务逻辑编辑/backend/src/main/java/com/businesslogic/groovy/generator/GroovyExpressionGenerator.java)

**作用**：将可视化配置的 DTO 转换为可执行的 Groovy 脚本字符串。

**生成流程**：
1. 遍历 `LogicStepDTO` 列表
2. 按 `functionCategory` 分发到具体生成方法：
   - **direct**：直接映射 → `def step1 = JsonPathUtil.read(inputData, '$.field')`
   - **calculation**：计算步骤 → 支持字符串/数值/日期函数，多个子计算通过 `&&`/`||` 组合
   - **filter**：筛选步骤 → 生成 `for (item in list) { if (condition) result << item }` 循环
   - **custom**：自定义表达式 → Aviator 语法转 Groovy（`nil`→`null`，`AND`→`&&`）
3. 末尾追加 `return lastVarName`，确保返回最后一步结果

**语法映射示例**：
```
Aviator                              → Groovy
let x = expr;                        → def x = expr
for item in list { }                 → for (item in list) { }
seq.add(list, item)                  → list << item
count(list)                          → list.size()
max(a, b)                            → Math.max(a, b)
date.diff_months(a, b)               → GroovyDateFunctions.diffMonths(a, b)
```

---

#### 步骤 3：保存到数据库 + 预编译缓存

**文件**：[GroovyBusinessLogicService.java](file:///c:/Users/lt/Desktop/业务逻辑编辑/backend/src/main/java/com/businesslogic/groovy/service/GroovyBusinessLogicService.java)

**方法**：`save(dto)`

**流程**：
1. 创建 `BusinessLogic` 实体，设置 `groovyExpression` 字段为生成的脚本
2. 调用 `baseMapper.insert()` 保存到数据库
3. **预编译并缓存**：
   - 调用 `GroovyExecutor.compile(groovyScript)` 编译脚本
   - 调用 `expressionCache.put(id, compiled)` 写入本地缓存
4. 保存逻辑步骤到 `logic_step` 表

**为何预编译**：避免首次执行时的编译延迟，同时尽早发现语法错误。即使预编译失败也不阻断保存流程（catch 后仅记录日志）。

---

### 场景 B：执行业务逻辑

#### 步骤 1：接收执行请求

**文件**：[GroovyBusinessLogicController.java](file:///c:/Users/lt/Desktop/业务逻辑编辑/backend/src/main/java/com/businesslogic/groovy/controller/GroovyBusinessLogicController.java)

**入口**：`POST /groovy-business-logic/{id}/execute`

**作用**：接收 `Map<String, Object>` 入参，通过 `ObjectMapper` 序列化为 JSON 字符串，传给 Service 层。

---

#### 步骤 2：从缓存获取或重新编译

**文件**：[GroovyBusinessLogicService.java](file:///c:/Users/lt/Desktop/业务逻辑编辑/backend/src/main/java/com/businesslogic/groovy/service/GroovyBusinessLogicService.java)

**方法**：`executeLogic(id, inputData)`

**流程**：
1. 从数据库查询 `BusinessLogic` 实体
2. 尝试从 `GroovyExpressionCache` 获取预编译脚本：
   - **缓存命中**：直接使用缓存的 `CompiledGroovyScript`
   - **缓存未命中**：从 `businessLogic.getGroovyExpression()` 取源码，调用 `GroovyExecutor.compile()` 重新编译并写入缓存
3. 调用 `GroovyExecutor.execute(compiled, inputData)` 执行

---

#### 步骤 3：准备执行环境

**文件**：[GroovyExecutor.java](file:///c:/Users/lt/Desktop/业务逻辑编辑/backend/src/main/java/com/businesslogic/groovy/engine/GroovyExecutor.java)

**方法**：`prepareEnvironment(inputData, additionalData)`

**注入内容**：
- `inputData`：原始 JSON 字符串（脚本中通过 `JsonPathUtil.read(inputData, '$.field')` 访问字段）
- `currentDate`：当前时间，供日期比较函数使用
- `additionalData`：业务侧自定义变量（扁平展开）

---

#### 步骤 4：执行脚本

**文件**：[GroovyExpressionEngine.java](file:///c:/Users/lt/Desktop/业务逻辑编辑/backend/src/main/java/com/businesslogic/groovy/engine/GroovyExpressionEngine.java)

**方法**：`execute(compiled, env)`

**Binding 注入顺序**（后注入覆盖先注入）：
1. **工具类**（Class 对象）：
   - `JsonPathUtil`：字段访问
   - `StringUtil`：字符串工具
   - `GroovyDateFunctions`：日期函数
2. **自定义函数**（来自 `GroovyFunctionRegistry`，通常为 Closure）
3. **静态函数类**（按 prefix 暴露整组静态方法）
4. **业务 env**（最后注入，避免被工具类覆盖）

**执行**：调用 `script.run()`，返回 `return` 语句的值。

---

## 四、缓存层说明

### 本地缓存（单机）

**文件**：[GroovyExpressionCache.java](file:///c:/Users/lt/Desktop/业务逻辑编辑/backend/src/main/java/com/businesslogic/groovy/cache/GroovyExpressionCache.java)

**作用**：按业务逻辑 ID 索引预编译脚本，使用 `ConcurrentHashMap`，适合读多写少场景。

**操作时机**：
- `save` 时 `put` 写入
- `update` 时 `refresh` 刷新
- `delete` 时 `remove` 移除
- `executeLogic` 时 `get` 读取

---

### Redis 缓存（分布式）

**文件**：[GroovyRedisExpressionCache.java](file:///c:/Users/lt/Desktop/业务逻辑编辑/backend/src/main/java/com/businesslogic/groovy/redisCache/GroovyRedisExpressionCache.java)

**作用**：跨实例共享编译结果，支持特征级别版本号方案。

**Redis 存储结构（3 个 Key）**：
```
groovy-expr:txn:versions                     Hash    全局交易码版本索引
groovy-expr:txn:{txnCode}                    Hash    交易码骨架（特征版本号）
groovy-expr:txn:data:{txnCode}:{featureCode} String  特征源码（Groovy 脚本字符串）
```

**与 Aviator 的核心差异**：
- Redis 存储**源码字符串**（String），而非序列化的 byte[]
- 反序列化即重新编译源码为 `CompiledGroovyScript`
- 使用独立 Key 前缀 `groovy-expr:` 与 Aviator 缓存隔离

**同步机制**：
- **启动时**：全量加载所有交易码
- **运行时**：定时轮询交易码版本号，增量同步变化的交易码
- **查询时**：按需检查单交易码版本号，不一致则精确同步
- **并发控制**：`ReentrantLock.tryLock()`，拿不到锁直接放弃，不排队

---

## 五、辅助组件说明

### 编译后的脚本包装类

**文件**：[CompiledGroovyScript.java](file:///c:/Users/lt/Desktop/业务逻辑编辑/backend/src/main/java/com/businesslogic/groovy/engine/CompiledGroovyScript.java)

**作用**：包装编译后的 `Script Class` + 原始源码 + 源码 MD5。

**关键设计**：
- `equals`/`hashCode` 基于 `sourceHash` 判等（Groovy Class 对象身份敏感，直接比较不稳定）
- `newScriptInstance()`：每次执行创建新 Script 实例，保证线程安全

---

### 日期工具类

**文件**：[GroovyDateFunctions.java](file:///c:/Users/lt/Desktop/业务逻辑编辑/backend/src/main/java/com/businesslogic/groovy/util/GroovyDateFunctions.java)

**作用**：提供 Aviator 内置 `date.*` 函数的等价实现。

**支持的函数**：
- `diffMonths(a, b)` / `diffDays(a, b)` / `diffYears(a, b)`：日期差值计算
- `withinLast3/6/9/12Months(arg)`：判断是否在最近 N 个月内
- `before(a, b)` / `after(a, b)` / `equal(a, b)`：日期比较
- `format(date)`：日期格式化

**注入方式**：
1. 被 `GroovySandbox` 加入白名单和预导入
2. 被 `GroovyExpressionEngine.execute()` 以 Class 形式注入到 Binding
3. 脚本中通过 `GroovyDateFunctions.diffMonths(a, b)` 调用

---

## 六、完整调用链路图

### 保存业务逻辑

```
前端请求
  → GroovyBusinessLogicController.save()
    → GroovyBusinessLogicService.save()
      → GroovyExpressionGenerator.generate()     // DTO → Groovy 脚本
      → baseMapper.insert()                       // 保存到数据库
      → GroovyExecutor.compile()                  // 预编译
        → GroovyExpressionEngine.compile()
          → GroovySandbox 安全检查
          → GroovyClassLoader.parseClass()
          → 写入编译缓存（MD5 → Class）
      → GroovyExpressionCache.put()               // 写入本地缓存
```

### 执行业务逻辑

```
前端请求
  → GroovyBusinessLogicController.execute()
    → ObjectMapper 序列化入参为 JSON
    → GroovyBusinessLogicService.executeLogic()
      → baseMapper.selectById()                   // 查询数据库
      → GroovyExpressionCache.get()               // 查本地缓存
        ├─ 命中：直接使用 CompiledGroovyScript
        └─ 未命中：GroovyExecutor.compile() 重新编译
      → GroovyExecutor.execute()
        → prepareEnvironment()                    // 构建 env（inputData/currentDate）
        → GroovyExpressionEngine.execute()
          → CompiledGroovyScript.newScriptInstance()  // 新建 Script 实例
          → 构建 Binding（注入工具类/函数/env）
          → script.run()                          // 执行脚本
      → 返回结果
```

### 自定义函数调用

```
业务脚本中调用 myFunc(arg1, arg2)
  → Groovy 引擎查找 registeredFunctions
    → 找到对应的 Closure
      → ExpressionFunctionClosure.call(args)
        → 按参数名绑定到 funcEnv
        → GroovyExpressionEngine.execute(compiled, funcEnv)
          → 执行预编译的表达式
          → 返回结果
```

---

## 七、测试入口

| Controller | 路径前缀 | 作用 |
|------------|----------|------|
| `GroovyBusinessLogicController` | `/groovy-business-logic` | 业务逻辑 CRUD + 执行 |
| `GroovyEngineTestController` | `/groovy/engine` | 引擎功能测试（编译/执行/缓存） |
| `GroovyFunctionRegistryController` | `/groovy/functions` | 函数注册管理 |
| `GroovyRedisTestController` | `/groovy/redis` | Redis 缓存测试 |

---

## 八、关键设计决策总结

1. **源码字符串缓存**：Groovy Class 无法标准序列化，Redis 存源码、加载时重新编译
2. **MD5 编译缓存**：避免重复编译，同一源码只解析一次
3. **每次新建 Script 实例**：保证线程安全，Binding 独立无污染
4. **沙箱双保险**：白名单管 import + 黑名单管直接调用
5. **单例引擎共享**：Spring Bean 与 GroovyExecutor 静态方法使用同一实例
6. **Closure 包装**：统一三类函数（表达式/脚本/Java）的调用方式
7. **独立 URL 前缀**：与 Aviator 控制器隔离，调用方可按需选择引擎
