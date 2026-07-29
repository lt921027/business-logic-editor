# AviatorEvaluator 函数注册方法详解

## 概述

本文档详细介绍 AviatorEvaluator 中三种函数注册方法的逻辑、优势和使用场景，帮助开发者根据实际需求选择最合适的方式。

## 三种方法对比

| 特性 | `addFunction` | `addStaticFunctions` | `addFunctionLoader` |
|------|---------------|---------------------|-------------------|
| **注册数量** | 单个函数 | 批量注册类的所有静态方法 | 动态按需加载 |
| **加载时机** | 即时注册 | 即时注册 | 延迟加载（按需） |
| **性能优化** | 无特殊优化 | 无特殊优化 | 显著优化启动速度 |
| **内存占用** | 与注册数量成正比 | 取决于类的方法数量 | 按需加载，占用最小 |
| **灵活性** | 高（完全可控） | 中（需修改类源码） | 高（动态加载逻辑） |
| **使用难度** | 简单 | 简单 | 中等 |

---

## 1. addFunction

### 功能说明

`addFunction` 用于向 AviatorEvaluator 注册**单个自定义函数**。这是最直接、最常用的函数注册方式。

### 核心逻辑

```
1. 开发者创建一个实现 com.googlecode.aviator.runtime.type.AviatorFunction 接口的类
2. 实现相应的方法，如 execute(...)
3. 调用 evaluator.addFunction(new MyFunction())
4. 函数立即可用，可以在表达式中直接调用
```

### 代码示例

```java
import com.googlecode.aviator.AviatorEvaluator;
import com.googlecode.aviator.runtime.type.AviatorFunction;

public class MyFunctions {
    public static AviatorFunction myUpperCase = new AviatorFunction() {
        @Override
        public String getName() {
            return "myUpperCase";  // 函数名称
        }

        @Override
        public AviatorObject execute(Map<String, Object> env, AviatorObject... args) {
            if (args.length != 1) {
                throw new IllegalArgumentException("myUpperCase requires exactly 1 argument");
            }
            String value = args[0].getValue(env).toString();
            return new AviatorString(value.toUpperCase());
        }
    };

    public static void main(String[] args) {
        // 注册单个函数
        AviatorEvaluator.addFunction(myUpperCase);

        // 在表达式中使用
        String result = AviatorEvaluator.execute("myUpperCase('hello')", null);
        System.out.println(result);  // 输出: HELLO
    }
}
```

### 优势

✅ **简单直观**：一对一的关系，易于理解和维护
✅ **完全可控**：可以精细控制每个函数的创建和初始化逻辑
✅ **调试友好**：单个函数的执行路径清晰，便于排查问题
✅ **按需注册**：只注册需要的函数，避免不必要的资源占用

### 适用场景

- 函数数量较少（少于 20 个）
- 函数逻辑简单或高度定制化
- 需要在运行时动态注册/注销函数
- 函数的创建依赖特殊条件或配置

---

## 2. addStaticFunctions

### 功能说明

`addStaticFunctions` 用于批量注册一个类中的**所有 public static 方法**作为 Aviator 函数。这是一种批量化的注册方式，可以一次性将工具类的所有静态方法暴露给表达式使用。

### 核心逻辑

```
1. 创建一个工具类，将相关的静态方法定义为 public static
2. 调用 evaluator.addStaticFunctions(MyUtils.class)
3. 工具类中所有 public static 方法自动注册为同名函数
4. 注册时自动扫描方法的参数和返回值类型
```

### 代码示例

```java
import com.googlecode.aviator.AviatorEvaluator;
import java.util.*;

public class StringUtils {

    public static String upper(String str) {
        return str != null ? str.toUpperCase() : null;
    }

    public static String lower(String str) {
        return str != null ? str.toLowerCase() : null;
    }

    public static int length(String str) {
        return str != null ? str.length() : 0;
    }

    public static boolean isEmpty(String str) {
        return str == null || str.isEmpty();
    }
}

public class Main {
    public static void main(String[] args) {
        // 批量注册 StringUtils 中的所有静态方法
        AviatorEvaluator.addStaticFunctions(StringUtils.class);

        // 在表达式中使用
        String expr1 = "upper('hello')";
        String expr2 = "length('world')";
        String expr3 = "isEmpty('')";

        System.out.println(AviatorEvaluator.execute(expr1));  // HELLO
        System.out.println(AviatorEvaluator.execute(expr2));  // 5
        System.out.println(AviatorEvaluator.execute(expr3));  // true
    }
}
```

### 优势

✅ **批量操作**：一个调用注册整个工具类的所有方法
✅ **代码复用**：可以直接复用已有的工具类，无需为 Aviator 单独实现
✅ **统一管理**：相关的工具方法集中在一个类中，便于维护
✅ **命名规范**：函数名自动使用方法名，保持一致性

### 注意事项

⚠️ **命名冲突**：如果多个工具类有同名的静态方法，后者会覆盖前者
⚠️ **参数限制**：只支持 Aviator 原生类型的参数（如 String、Long、Doulbe、List 等）
⚠️ **类加载**：传入的是 Class 对象，静态代码块会执行

### 适用场景

- 将现有的工具类快速暴露给表达式使用
- 函数数量较多但功能相似（如各种 String/Date/Collection 工具方法）
- 需要保持工具类与表达式函数的命名一致性
- 团队已经有成熟的静态工具类库

---

## 3. addFunctionLoader

### 功能说明

`addFunctionLoader` 是一种**延迟加载（Lazy Loading）**机制。它允许你注册一个函数加载器，只有当表达式实际调用某个函数时，加载器才会被触发并返回相应的函数实例。

### 核心逻辑

```
1. 创建一个实现 FunctionLoader 接口的类
2. 在 loadFunction 方法中定义加载逻辑（根据函数名返回对应的函数）
3. 调用 evaluator.addFunctionLoader(prefix, loader)
4. 当表达式执行到未知函数时，自动调用加载器
5. 加载器根据函数名决定返回哪个函数实例或返回 null
```

### 代码示例

```java
import com.googlecode.aviator.AviatorEvaluator;
import com.googlecode.aviator.FunctionLoader;
import com.googlecode.aviator.runtime.type.AviatorFunction;
import java.util.concurrent.ConcurrentHashMap;

public class DynamicFunctionLoader implements FunctionLoader {

    private final Map<String, AviatorFunction> functionCache = new ConcurrentHashMap<>();

    public DynamicFunctionLoader() {
        // 预注册一些常用函数
        functionCache.put("customFunc1", new CustomFunc1());
        functionCache.put("customFunc2", new CustomFunc2());
    }

    @Override
    public AviatorFunction loadFunction(String name) {
        // 1. 首先检查缓存
        AviatorFunction func = functionCache.get(name);
        if (func != null) {
            return func;
        }

        // 2. 按需创建新函数（这里演示动态创建）
        switch (name) {
            case "dynamicAdd":
                return createDynamicAdd();
            case "dynamicMultiply":
                return createDynamicMultiply();
            case "formatDate":
                return createFormatDate();
            default:
                // 3. 无法加载返回 null
                return null;
        }
    }

    private AviatorFunction createDynamicAdd() {
        return new AviatorFunction() {
            @Override
            public String getName() { return "dynamicAdd"; }

            @Override
            public AviatorObject execute(Map<String, Object> env, AviatorObject... args) {
                long sum = 0;
                for (AviatorObject arg : args) {
                    sum += ((Number)arg.getValue(env)).longValue();
                }
                return new AviatorLong(sum);
            }
        };
    }

    // ... 其他动态函数创建逻辑
}

public class Main {
    public static void main(String[] args) {
        DynamicFunctionLoader loader = new DynamicFunctionLoader();

        // 注册函数加载器，指定前缀
        AviatorEvaluator.addFunctionLoader("biz", loader);

        // 第一次调用时，加载器会按需加载
        String expr1 = "biz.customFunc1(10, 20)";
        String expr2 = "biz.dynamicAdd(1, 2, 3, 4, 5)";

        System.out.println(AviatorEvaluator.execute(expr1));
        System.out.println(AviatorEvaluator.execute(expr2));
    }
}
```

### 优势

✅ **启动性能优**：不预先加载所有函数，加快系统启动速度
✅ **按需分配**：只有实际使用的函数才会被创建，节省内存
✅ **动态扩展**：可以在运行时动态添加新的函数支持
✅ **分组管理**：支持前缀分组，如 `math.*`、`str.*`、`date.*`
✅ **热加载**：可以在运行时更新加载器逻辑，实现函数的热更新

### 实现原理

```
执行流程：

1. 表达式执行 → 发现未知函数名 "dynamicFunc"
      ↓
2. 检查内置函数和已注册函数 → 未找到
      ↓
3. 调用已注册的 FunctionLoader（按前缀匹配）
      ↓
4. Loader.loadFunction("dynamicFunc") 被调用
      ↓
5. Loader 内部决定返回 AviatorFunction 或 null
      ↓
6. 如果返回函数 → 继续执行
   如果返回 null → 抛出函数未定义异常
```

### 适用场景

- 函数数量庞大（数十甚至数百个）
- 需要根据配置或数据库动态决定可用函数
- 追求极致的启动性能和内存占用
- 需要支持插件化的函数扩展机制
- 函数按业务模块划分，使用前缀区分

---

## 最佳实践建议

### 选择决策树

```
需要注册函数吗？
    │
    ├── 数量少（<20），逻辑简单
    │       └── ✅ 使用 addFunction
    │
    ├── 数量多，已有工具类
    │       └── ✅ 使用 addStaticFunctions
    │
    └── 数量多，需动态加载，按需加载
            └── ✅ 使用 addFunctionLoader
```

### 性能优化建议

| 场景 | 推荐方案 | 原因 |
|------|---------|------|
| 启动时初始化 | `addStaticFunctions` | 一次性完成，代码简单 |
| 运行时动态注册 | `addFunction` | 细粒度控制，可随时增删 |
| 超多函数（100+） | `addFunctionLoader` | 按需加载，内存友好 |
| 函数分组管理 | `addFunctionLoader` | 支持前缀分组 |

### 组合使用

这三种方法完全可以**组合使用**，各司其职：

```java
public class AviatorConfig {

    public static void configure(AviatorEvaluatorInstance evaluator) {

        // 1. 基础内置函数 - 使用静态方法批量注册
        evaluator.addStaticFunctions(MathUtils.class);
        evaluator.addStaticFunctions(DateUtils.class);

        // 2. 核心业务函数 - 精确注册
        evaluator.addFunction(new CoreBusinessFunc1());
        evaluator.addFunction(new CoreBusinessFunc2());

        // 3. 扩展函数 - 使用加载器按需加载
        evaluator.addFunctionLoader("ext", new ExtensionFunctionLoader());
        evaluator.addFunctionLoader("report", new ReportFunctionLoader());
    }
}
```

### 常见问题

**Q: addStaticFunctions 会覆盖 addFunction 注册的同名函数吗？**
A: 是的，后注册的有效。如果先调用 `addFunction(myFunc)`，再调用 `addStaticFunctions(SomeClass.class)` 且 SomeClass 有同名方法，SomeClass 的会覆盖之前的。

**Q: FunctionLoader 的前缀有什么作用？**
A: 前缀用于分组。例如注册 `evaluator.addFunctionLoader("str", loader)` 后，只有调用 `str.length()`、`str.upper()` 等时才会触发 loader，`math.add()` 等不会触发。

**Q: 如何实现函数的热更新？**
A: 推荐使用 `AviatorFunctionRegistry` 实现完整的热更新机制，详见下文"热部署方案"。

---

## 热部署方案（AviatorFunctionRegistry）

基于 AviatorScript 官方推荐的 `FunctionLoader` 机制，设计了 `AviatorFunctionRegistry` 组件，统一封装三种注册方式，实现运行时热更新。

### 架构设计

```
┌─────────────────────────────────────────────────────────────┐
│                    AviatorFunctionRegistry                   │
├─────────────────────────────────────────────────────────────┤
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────┐  │
│  │ Function     │  │ Function     │  │ HotLoadFunction  │  │
│  │ Definitions  │  │ Instances    │  │ Loader           │  │
│  │ (Map)        │  │ (Cache)      │  │ (FunctionLoader) │  │
│  └──────────────┘  └──────────────┘  └──────────────────┘  │
│         │                 │                      │          │
│         └─────────────────┼──────────────────────┘          │
│                           │                                 │
│  ┌────────────────────────┴─────────────────────────────┐   │
│  │  三种注册方式统一封装                                   │   │
│  │  • registerExpressionFunction()                       │   │
│  │  • registerScriptFunction()                           │   │
│  │  • registerJavaFunction()                             │   │
│  │  • registerStaticFunctions()                          │   │
│  └────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│              AviatorEvaluatorInstance                        │
│         (通过 addFunctionLoader 绑定)                         │
└─────────────────────────────────────────────────────────────┘
```

### 核心特性

| 特性 | 说明 |
|------|------|
| **统一封装** | 将三种注册方式（addFunction/addStaticFunctions/addFunctionLoader）统一为简洁的 API |
| **延迟加载** | 函数首次调用时才创建实例，节省内存和启动时间 |
| **实例缓存** | 创建后的函数实例缓存复用，避免重复编译 |
| **热更新** | 更新函数定义后，下次调用自动创建新实例，无需重启 |
| **多实例隔离** | 每个 AviatorEvaluatorInstance 拥有独立的注册中心 |

### 支持的函数类型

| 类型 | 注册方法 | 适用场景 |
|------|---------|---------|
| `EXPRESSION` | `registerExpressionFunction()` | 简单公式计算，如 `price * discount` |
| `SCRIPT` | `registerScriptFunction()` | 复杂业务逻辑，支持变量、条件、循环 |
| `JAVA` | `registerJavaFunction()` | 需要调用 Java 复杂逻辑 |
| `STATIC` | `registerStaticFunctions()` | 复用现有工具类的静态方法 |

### 使用示例

```java
// 1. 创建注册中心并初始化
AviatorEvaluatorInstance evaluator = AviatorEvaluator.getInstance();
AviatorFunctionRegistry registry = new AviatorFunctionRegistry(evaluator);
registry.init();

// 2. 注册表达式函数
registry.registerExpressionFunction("calculateBonus",
    "salary * performance * 0.1", "salary", "performance");

// 3. 注册脚本函数（支持复杂逻辑）
String script = "let base = price * rate;\n" +
    "let discount = base > 1000 ? 0.9 : 1.0;\n" +
    "return base * discount;";
registry.registerScriptFunction("calculateTax", script, "price", "rate");

// 4. 注册静态方法（复用工具类）
registry.registerStaticFunctions("str", StringUtils.class);

// 5. 在表达式中使用
Map<String, Object> env = new HashMap<>();
env.put("salary", 10000);
env.put("performance", 1.5);
Object result = evaluator.execute("calculateBonus(salary, performance)", env);
// 输出: 1500.0

// 6. 热更新函数（无需重启）
registry.updateFunction("calculateBonus", "salary * performance * 0.15", "salary", "performance");
Object newResult = evaluator.execute("calculateBonus(salary, performance)", env);
// 输出: 2250.0（已应用新公式）
```

### Spring Boot 集成

```java
@Configuration
public class HotLoadConfig {

    @Bean
    public AviatorEvaluatorInstance aviatorEvaluatorInstance() {
        AviatorEvaluatorInstance instance = AviatorEvaluator.getInstance();
        instance.setCachedExpressionByDefault(true);
        instance.useLRUExpressionCache(100);
        return instance;
    }

    @Bean
    public AviatorFunctionRegistry aviatorFunctionRegistry(AviatorEvaluatorInstance evaluator) {
        AviatorFunctionRegistry registry = new AviatorFunctionRegistry(evaluator);
        registry.init();
        return registry;
    }
}
```

### REST API 接口

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/functions/list` | GET | 获取所有已注册函数 |
| `/api/functions/register/expression` | POST | 注册表达式函数 |
| `/api/functions/register/script` | POST | 注册脚本函数 |
| `/api/functions/update/{name}` | POST | 热更新函数 |
| `/api/functions/delete/{name}` | DELETE | 删除函数 |
| `/api/functions/test/{name}` | POST | 测试函数执行 |

### 项目文件结构

```
src/main/java/com/businesslogic/hotload/
├── AviatorFunctionRegistry.java          # 核心注册中心
├── FunctionDefinition.java               # 函数定义
├── FunctionType.java                     # 函数类型枚举
├── HotLoadFunctionLoader.java            # FunctionLoader 实现
├── config/
│   └── HotLoadConfig.java                # Spring Boot 配置
├── controller/
│   └── FunctionRegistryController.java   # REST API
└── demo/
    └── HotLoadDemo.java                  # 使用示例
```

---

## 总结

| 方法 | 推荐指数 | 主要用途 |
|------|---------|---------|
| `addFunction` | ⭐⭐⭐⭐⭐ | 单个精确控制，最灵活 |
| `addStaticFunctions` | ⭐⭐⭐⭐ | 批量注册工具类函数 |
| `addFunctionLoader` | ⭐⭐⭐ | 大型系统、插件化架构 |

在实际项目中，建议：
- **简单场景**：直接使用 `addFunction`，代码清晰易维护
- **工具类复用**：使用 `addStaticFunctions` 快速暴露现有工具
- **大型系统**：结合三者，基础函数用前两种，扩展函数用 Loader