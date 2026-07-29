# 业务逻辑编辑器 - Code Wiki

## 文档信息

| 项目名称 | 业务逻辑编辑器 (Business Logic Editor) |
|---------|--------------------------------------|
| 版本 | 1.0.0 |
| 创建日期 | 2024年 |
| 最后更新 | 2024年 |

---

## 1. 项目概述

### 1.1 项目简介

业务逻辑编辑器是一个完整的全栈解决方案，包含前端可视化编辑器和后端服务。用户可以通过图形化界面配置业务逻辑，后端自动生成可执行的 **AviatorScript** 表达式，并支持逻辑的保存、加载和执行。

### 1.2 核心功能

- **可视化配置**：通过图形化界面配置业务逻辑，无需编写代码
- **表达式自动生成**：根据配置自动生成 AviatorScript 表达式
- **逻辑执行引擎**：使用高性能表达式引擎执行业务逻辑
- **热加载函数**：支持运行时动态注册和更新函数
- **表达式缓存**：预编译表达式并缓存，提高执行效率

### 1.3 应用场景

- 数据转换与映射
- 条件筛选与过滤
- 业务规则配置
- ETL 数据清洗
- 动态表单处理

---

## 2. 项目架构

### 2.1 整体架构图

```
┌─────────────────────────────────────────────────────────────────┐
│                        前端 (HTML/Vue.js)                       │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────┐              │
│  │ JSON输入区   │  │ 逻辑编辑区  │  │ 预览区  │              │
│  └──────────────┘  └──────────────┘  └──────────┘              │
└─────────────────────────────────────────────────────────────────┘
                              │ HTTP/REST API
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                    后端 (Spring Boot 2.7.18)                    │
│  ┌────────────────────────────────────────────────────────────┐  │
│  │                    Controller 层                           │  │
│  │              BusinessLogicController                      │  │
│  └────────────────────────────────────────────────────────────┘  │
│                              │                                   │
│                              ▼                                   │
│  ┌────────────────────────────────────────────────────────────┐  │
│  │                     Service 层                             │  │
│  │            BusinessLogicService / CacheService             │  │
│  └────────────────────────────────────────────────────────────┘  │
│          │                    │                    │              │
│          ▼                    ▼                    ▼              │
│  ┌──────────────┐  ┌──────────────────┐  ┌──────────────┐      │
│  │   Generator   │  │    Executor      │  │   Mapper     │      │
│  │ AviatorExprGen │  │ AviatorExecutor  │  │ MyBatisPlus │      │
│  └──────────────┘  └──────────────────┘  └──────────────┘      │
│                              │                                   │
│                              ▼                                   │
│  ┌────────────────────────────────────────────────────────────┐  │
│  │              Cache / Hot-Load 层                          │  │
│  │        ExpressionCache / AviatorFunctionRegistry          │  │
│  └────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                    MySQL 数据库 + Redis 缓存                    │
└─────────────────────────────────────────────────────────────────┘
```

### 2.2 项目目录结构

```
业务逻辑编辑/
├── business-logic-editor.html      # 前端HTML文件
├── test-business-logic-editor.html # 测试页面
├── json-formatter.html              # JSON格式化工具
├── logic-list.html                  # 逻辑列表页面
├── vue.global.js                    # Vue.js 全局库
├── vite.config.js                   # Vite 配置
│
└── backend/                         # 后端项目
    ├── src/
    │   ├── main/
    │   │   ├── java/com/businesslogic/
    │   │   │   ├── BusinessLogicEditorApplication.java  # 启动类
    │   │   │   │
    │   │   │   ├── common/                  # 通用类
    │   │   │   │   └── Result.java           # 统一响应封装
    │   │   │   │
    │   │   │   ├── config/                  # 配置类
    │   │   │   │   ├── CacheConfig.java
    │   │   │   │   ├── RedisConfig.java
    │   │   │   │   └── MyMetaObjectHandler.java
    │   │   │   │
    │   │   │   ├── controller/              # 控制器层
    │   │   │   │   └── BusinessLogicController.java
    │   │   │   │
    │   │   │   ├── service/                 # 服务层
    │   │   │   │   ├── BusinessLogicService.java
    │   │   │   │   └── CacheService.java
    │   │   │   │
    │   │   │   ├── generator/               # 表达式生成器
    │   │   │   │   └── AviatorExpressionGenerator.java
    │   │   │   │
    │   │   │   ├── executor/                # 表达式执行器
    │   │   │   │   └── AviatorExecutor.java
    │   │   │   │
    │   │   │   ├── cache/                   # 缓存服务
    │   │   │   │   └── ExpressionCache.java
    │   │   │   │
    │   │   │   ├── hotload/                 # 热加载功能
    │   │   │   │   ├── AviatorFunctionRegistry.java
    │   │   │   │   ├── FunctionDefinition.java
    │   │   │   │   ├── FunctionType.java
    │   │   │   │   ├── config/
    │   │   │   │   └── controller/
    │   │   │   │
    │   │   │   ├── mapper/                  # 数据访问层
    │   │   │   │   ├── BusinessLogicMapper.java
    │   │   │   │   └── LogicStepMapper.java
    │   │   │   │
    │   │   │   ├── entity/                  # 实体类
    │   │   │   │   ├── BusinessLogic.java
    │   │   │   │   └── LogicStep.java
    │   │   │   │
    │   │   │   ├── dto/                     # 数据传输对象
    │   │   │   │   ├── BusinessLogicSaveDTO.java
    │   │   │   │   ├── LogicStepDTO.java
    │   │   │   │   └── ...
    │   │   │   │
    │   │   │   ├── vo/                      # 视图对象
    │   │   │   │   ├── BusinessLogicVO.java
    │   │   │   │   └── ...
    │   │   │   │
    │   │   │   ├── util/                    # 工具类
    │   │   │   │   ├── JsonPathUtil.java
    │   │   │   │   └── StringUtil.java
    │   │   │   │
    │   │   │   ├── exception/               # 异常处理
    │   │   │   │   └── GlobalExceptionHandler.java
    │   │   │   │
    │   │   │   ├── redis/                   # Redis相关
    │   │   │   │   ├── RedisPublisherService.java
    │   │   │   │   └── RedisMessageListener.java
    │   │   │   │
    │   │   │   └── demo/                     # 示例代码
    │   │   │       ├── service/
    │   │   │       ├── controller/
    │   │   │       └── model/
    │   │   │
    │   │   └── resources/
    │   │       ├── application.yml           # 应用配置
    │   │       ├── schema.sql               # 数据库表结构
    │   │       ├── cache-schema.sql         # 缓存表结构
    │   │       └── hot-function-schema.sql  # 热加载函数表结构
    │   │
    │   └── test/
    │       └── java/com/businesslogic/
    │           └── redis/
    │               └── RedisBroadcastTest.java
    │
    ├── pom.xml                              # Maven 配置
    ├── README.md                             # 后端说明
    ├── Aviator-Function-Registration.md     # 函数注册文档
    └── frontend-api-example.js             # 前端API示例
```

---

## 3. 技术栈

### 3.1 后端技术栈

| 技术 | 版本 | 用途 |
|-----|------|------|
| Spring Boot | 2.7.18 | Web框架 |
| MyBatis Plus | 3.5.5 | ORM框架 |
| AviatorScript | 5.3.3 | 表达式引擎 |
| JsonPath | 2.8.0 | JSON路径查询 |
| MySQL | 8.0 | 关系型数据库 |
| Redis | - | 缓存/消息队列 |
| Lombok | 1.18.30 | 代码简化 |
| Hutool | 5.8.23 | 工具类库 |
| Fastjson | 1.2.83 | JSON处理 |
| Jackson | 2.13.5 | JSON序列化 |

### 3.2 前端技术栈

| 技术 | 版本 | 用途 |
|-----|------|------|
| HTML5 | - | 页面结构 |
| Vue.js | 3.x | 前端框架 |
| JavaScript | ES6+ | 脚本语言 |
| JSON | - | 数据格式 |

---

## 4. 模块职责

### 4.1 Controller 层

**BusinessLogicController** 是系统的 REST API 入口，提供以下接口：

| 方法 | 路径 | 功能 |
|-----|------|------|
| POST | `/business-logic` | 保存业务逻辑 |
| PUT | `/business-logic/{id}` | 更新业务逻辑 |
| GET | `/business-logic/{id}` | 查询单个业务逻辑 |
| GET | `/business-logic` | 查询所有业务逻辑 |
| DELETE | `/business-logic/{id}` | 删除业务逻辑 |
| POST | `/business-logic/{id}/execute` | 执行业务逻辑 |
| POST | `/business-logic/generate-expression` | 生成表达式 |

### 4.2 Service 层

**BusinessLogicService** 负责业务逻辑的核心处理：

```
主要职责：
├── 业务逻辑的 CRUD 操作
├── Aviator 表达式生成
├── 表达式预编译与缓存
├── 业务逻辑执行
└── DTO/VO 转换
```

### 4.3 Generator 层

**AviatorExpressionGenerator** 负责将配置转换为 AviatorScript 表达式：

| 方法 | 功能 |
|-----|------|
| `generate()` | 生成完整表达式 |
| `generateDirectMapping()` | 生成直接映射 |
| `generateCalculation()` | 生成计算表达式 |
| `generateFilter()` | 生成筛选表达式 |
| `generateCustomExpression()` | 生成自定义表达式 |

### 4.4 Executor 层

**AviatorExecutor** 负责执行 AviatorScript 表达式：

| 方法 | 功能 |
|-----|------|
| `execute()` | 执行表达式 |
| `compile()` | 预编译表达式 |
| `executeBatch()` | 批量执行 |
| `prepareEnvironment()` | 准备执行环境 |

### 4.5 Cache 层

**ExpressionCache** 使用 ConcurrentHashMap 实现内存缓存：

```
缓存操作：
├── put()      添加缓存
├── get()      获取缓存
├── remove()   删除缓存
├── refresh()  刷新缓存
└── clear()    清空缓存
```

### 4.6 Hot-Load 层

**AviatorFunctionRegistry** 支持运行时函数注册：

| 功能 | 说明 |
|-----|------|
| registerExpressionFunction | 注册表达式函数 |
| registerScriptFunction | 注册脚本函数 |
| registerJavaFunction | 注册Java函数 |
| updateFunction | 更新函数 |
| unregisterFunction | 注销函数 |

---

## 5. 核心类详解

### 5.1 BusinessLogicController

**文件位置**: `com.businesslogic.controller.BusinessLogicController`

**类说明**: REST API 控制器，处理前端请求

```java
@Slf4j
@RestController
@RequestMapping("/business-logic")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", maxAge = 3600)
public class BusinessLogicController {

    private final BusinessLogicService businessLogicService;
    private final ObjectMapper objectMapper;

    // 核心方法
    @PostMapping                           // 保存业务逻辑
    @PutMapping("/{id}")                  // 更新业务逻辑
    @GetMapping("/{id}")                 // 查询单个
    @GetMapping                           // 查询所有
    @DeleteMapping("/{id}")              // 删除
    @PostMapping("/{id}/execute")        // 执行逻辑
    @PostMapping("/generate-expression") // 生成表达式
}
```

### 5.2 BusinessLogicService

**文件位置**: `com.businesslogic.service.BusinessLogicService`

**类说明**: 业务逻辑服务类，继承 MyBatis Plus 的 ServiceImpl

**核心方法**:

| 方法 | 访问修饰符 | 功能描述 |
|-----|----------|---------|
| `save(BusinessLogicSaveDTO)` | public | 保存业务逻辑，生成表达式并预编译缓存 |
| `update(Long, BusinessLogicSaveDTO)` | public | 更新业务逻辑，刷新表达式缓存 |
| `delete(Long)` | public | 删除业务逻辑，清除缓存 |
| `getDetail(Long)` | public | 获取业务逻辑详情 |
| `getById(Long)` | public | 根据ID获取（别名方法） |
| `listAll()` | public | 获取所有业务逻辑 |
| `executeLogic(Long, String)` | public | 执行业务逻辑 |
| `generateExpression(BusinessLogicSaveDTO)` | public | 生成表达式 |
| `convertToVO()` | private | 实体转VO |
| `convertToLogicSteps()` | private | DTO转实体 |

### 5.3 AviatorExpressionGenerator

**文件位置**: `com.businesslogic.generator.AviatorExpressionGenerator`

**类说明**: AviatorScript 表达式生成器

**核心方法**:

| 方法 | 功能 |
|-----|------|
| `generate(BusinessLogicSaveDTO)` | 主入口，生成完整表达式 |
| `generateStepExpression()` | 生成步骤表达式 |
| `generateDirectMapping()` | 生成直接映射表达式 |
| `generateCalculation()` | 生成计算表达式 |
| `generateCalculationStepExpression()` | 生成计算步骤表达式 |
| `generateFilter()` | 生成筛选表达式 |
| `generateFilterWithLoop()` | 生成带循环的筛选表达式 |
| `generateCustomExpression()` | 生成自定义表达式 |
| `generateFieldExpression()` | 生成字段访问表达式 |
| `generateOperandExpression()` | 生成操作数表达式 |
| `generateFilterCondition()` | 生成筛选条件 |
| `generateFilterLogicExecution()` | 生成筛选执行逻辑 |

**支持的函数类型**:

| 分类 | 函数 | 说明 |
|-----|------|------|
| string | includes, concat, equals, length | 字符串函数 |
| number | max, min, sum, avg, arithmetic | 数值函数 |
| date | withinLast3Months, days_between, format | 日期函数 |
| filter | count, sum, distinct | 筛选聚合函数 |

### 5.4 AviatorExecutor

**文件位置**: `com.businesslogic.executor.AviatorExecutor`

**类说明**: AviatorScript 表达式执行器

**核心方法**:

| 方法 | 参数 | 返回值 | 功能 |
|-----|------|-------|------|
| `execute(String, String)` | 表达式代码, 输入数据 | Object | 执行表达式 |
| `execute(String, String, Map)` | 表达式代码, 输入数据, 额外数据 | Object | 执行表达式(带额外数据) |
| `execute(Expression, String)` | 预编译表达式, 输入数据 | Object | 执行预编译表达式 |
| `execute(Expression, String, Map)` | 预编译表达式, 输入数据, 额外数据 | Object | 执行预编译表达式(带额外数据) |
| `compile(String)` | 表达式代码 | Expression | 预编译表达式 |
| `executeBatch(Map, String, Map)` | 表达式Map, 输入数据, 额外数据 | Map | 批量执行 |
| `prepareEnvironment(String, Map)` | 输入数据, 额外数据 | Map | 准备执行环境 |

### 5.5 ExpressionCache

**文件位置**: `com.businesslogic.cache.ExpressionCache`

**类说明**: Aviator 表达式缓存服务

**核心方法**:

| 方法 | 访问修饰符 | 功能 |
|-----|----------|------|
| `put(Long, Expression)` | public | 缓存表达式 |
| `get(Long)` | public | 获取缓存表达式 |
| `remove(Long)` | public | 移除缓存表达式 |
| `refresh(Long, Expression)` | public | 刷新缓存 |
| `containsKey(Long)` | public | 检查是否存在 |
| `clear()` | public | 清空所有缓存 |
| `size()` | public | 获取缓存数量 |

**实现细节**:
- 使用 `ConcurrentHashMap` 保证线程安全
- Key: 业务逻辑ID
- Value: 预编译的 Expression 对象

### 5.6 AviatorFunctionRegistry

**文件位置**: `com.businesslogic.hotload.AviatorFunctionRegistry`

**类说明**: Aviator 函数注册中心，支持热加载

**核心方法**:

| 方法 | 功能 |
|-----|------|
| `init()` | 初始化注册中心 |
| `registerExpressionFunction()` | 注册表达式类型函数 |
| `registerScriptFunction()` | 注册脚本类型函数 |
| `registerJavaFunction()` | 注册Java类型函数 |
| `registerStaticFunctions()` | 注册静态函数 |
| `updateFunction()` | 更新表达式函数 |
| `updateScriptFunction()` | 更新脚本函数 |
| `updateJavaFunction()` | 更新Java函数 |
| `unregisterFunction()` | 注销函数 |
| `getFunctionDefinition()` | 获取函数定义 |
| `hasFunction()` | 检查函数是否存在 |

**内部类**:

| 类名 | 功能 |
|-----|------|
| `ExpressionFunction` | 表达式函数实现 |
| `ScriptFunction` | 脚本函数实现 |

### 5.7 JsonPathUtil

**文件位置**: `com.businesslogic.util.JsonPathUtil`

**类说明**: JSON 路径查询工具类

**核心方法**:

| 方法 | 功能 |
|-----|------|
| `read(String, String)` | 读取JSON路径 |
| `readString(String, String)` | 读取字符串 |
| `readInt(String, String)` | 读取整数 |
| `readLong(String, String)` | 读取长整数 |
| `readDouble(String, String)` | 读取双精度 |
| `readBoolean(String, String)` | 读取布尔值 |
| `readList(String, String)` | 读取列表 |
| `exists(String, String)` | 检查路径是否存在 |

### 5.8 实体类

#### BusinessLogic

**文件位置**: `com.businesslogic.entity.BusinessLogic`

| 字段 | 类型 | 说明 |
|-----|------|------|
| id | Long | 主键ID |
| name | String | 业务逻辑名称 |
| description | String | 描述 |
| jsonInput | String | 入参JSON示例 |
| aviatorExpression | String | 生成的Aviator表达式 |
| stepCount | Integer | 步骤数量 |
| createdAt | LocalDateTime | 创建时间 |
| updatedAt | LocalDateTime | 更新时间 |
| deleted | Integer | 逻辑删除标记 |

#### LogicStep

**文件位置**: `com.businesslogic.entity.LogicStep`

| 字段 | 类型 | 说明 |
|-----|------|------|
| id | Long | 主键ID |
| businessLogicId | Long | 业务逻辑ID |
| stepOrder | Integer | 步骤顺序 |
| functionCategory | String | 函数分类 |
| field | String | 主字段 |
| functionName | String | 函数名 |
| params | String | 参数JSON |
| customExpression | String | 自定义表达式 |
| outputVar | String | 输出变量名 |
| comment | String | 备注说明 |
| filterScope | String | 筛选范围 |
| mappedField | String | 映射字段 |
| calculationSteps | String | 计算步骤JSON |
| filterItems | String | 筛选条件JSON |
| filterLogic | String | 满足条件执行JSON |
| reverseLogic | String | 条件不符执行JSON |
| collapsed | Boolean | 是否折叠 |
| createdAt | LocalDateTime | 创建时间 |
| updatedAt | LocalDateTime | 更新时间 |
| deleted | Integer | 逻辑删除标记 |

---

## 6. 数据库设计

### 6.1 数据库配置

```yaml
# application.yml
spring:
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://localhost:3306/business_logic_editor
    username: root
    password: a135798462
```

### 6.2 表结构

#### business_logic 表

```sql
CREATE TABLE business_logic (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    name VARCHAR(255) NOT NULL COMMENT '业务逻辑名称',
    description TEXT COMMENT '描述',
    json_input TEXT COMMENT '入参JSON示例',
    aviator_expression TEXT COMMENT '生成的Aviator表达式',
    step_count INT DEFAULT 0 COMMENT '步骤数量',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除标记',
    INDEX idx_name (name),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

#### logic_step 表

```sql
CREATE TABLE logic_step (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    business_logic_id BIGINT NOT NULL COMMENT '业务逻辑ID',
    step_order INT NOT NULL COMMENT '步骤顺序',
    function_category VARCHAR(50) COMMENT '函数分类',
    field VARCHAR(500) COMMENT '主字段',
    function_name VARCHAR(100) COMMENT '函数名',
    params TEXT COMMENT '参数JSON',
    custom_expression TEXT COMMENT '自定义表达式',
    output_var VARCHAR(100) COMMENT '输出变量名',
    comment TEXT COMMENT '备注说明',
    filter_scope VARCHAR(500) COMMENT '筛选范围',
    mapped_field VARCHAR(500) COMMENT '映射字段',
    calculation_steps TEXT COMMENT '计算步骤JSON',
    filter_items TEXT COMMENT '筛选条件JSON',
    filter_logic TEXT COMMENT '满足条件时执行JSON',
    reverse_logic TEXT COMMENT '条件不符时执行JSON',
    collapsed TINYINT DEFAULT 0 COMMENT '是否折叠',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0,
    INDEX idx_business_logic_id (business_logic_id),
    INDEX idx_step_order (step_order),
    FOREIGN KEY (business_logic_id) REFERENCES business_logic(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 6.3 ER 关系图

```
┌─────────────────────┐         ┌─────────────────────┐
│   business_logic    │         │     logic_step       │
├─────────────────────┤         ├─────────────────────┤
│ id (PK)             │◄────────│ business_logic_id(FK)│
│ name                │   1:N   │ id (PK)             │
│ description         │         │ step_order          │
│ json_input          │         │ function_category   │
│ aviator_expression  │         │ field               │
│ step_count          │         │ function_name       │
│ created_at          │         │ params              │
│ updated_at          │         │ custom_expression   │
│ deleted             │         │ output_var          │
└─────────────────────┘         │ comment             │
                                │ filter_scope        │
                                │ mapped_field        │
                                │ calculation_steps    │
                                │ filter_items        │
                                │ filter_logic        │
                                │ reverse_logic       │
                                │ created_at          │
                                │ updated_at          │
                                │ deleted             │
                                └─────────────────────┘
```

---

## 7. API 接口文档

### 7.1 基础信息

- **Base URL**: `http://localhost:8080/api`
- **Content-Type**: `application/json`
- **响应格式**: JSON

### 7.2 统一响应格式

```json
{
  "code": 200,
  "message": "操作成功",
  "data": { }
}
```

### 7.3 接口列表

#### 7.3.1 保存业务逻辑

```
POST /business-logic
```

**请求参数**:

```json
{
  "name": "业务逻辑名称",
  "description": "业务逻辑描述",
  "jsonInput": "{\"name\":\"张三\",\"age\":25}",
  "logicSteps": [
    {
      "functionCategory": ["direct"],
      "mappedField": "name",
      "outputVar": "userName",
      "comment": "映射用户名"
    }
  ]
}
```

**响应示例**:

```json
{
  "code": 200,
  "message": "保存成功",
  "data": {
    "id": 1,
    "name": "业务逻辑名称",
    "description": "业务逻辑描述",
    "jsonInput": "{\"name\":\"张三\",\"age\":25}",
    "aviatorExpression": "let userName = JsonPathUtil.read(inputData, '$.name');",
    "stepCount": 1,
    "createdAt": "2024-01-01 00:00:00",
    "updatedAt": "2024-01-01 00:00:00",
    "logicSteps": [...]
  }
}
```

#### 7.3.2 更新业务逻辑

```
PUT /business-logic/{id}
```

**请求参数**: 同保存接口

#### 7.3.3 查询业务逻辑

```
GET /business-logic/{id}
```

**响应示例**: 同保存接口的 data 部分

#### 7.3.4 查询所有业务逻辑

```
GET /business-logic
```

**响应示例**:

```json
{
  "code": 200,
  "message": "操作成功",
  "data": [
    {
      "id": 1,
      "name": "业务逻辑名称",
      ...
    }
  ]
}
```

#### 7.3.5 删除业务逻辑

```
DELETE /business-logic/{id}
```

**响应示例**:

```json
{
  "code": 200,
  "message": "删除成功",
  "data": null
}
```

#### 7.3.6 执行业务逻辑

```
POST /business-logic/{id}/execute
```

**请求参数**:

```json
{
  "name": "张三",
  "age": 25
}
```

**响应示例**:

```json
{
  "code": 200,
  "message": "执行成功",
  "data": {
    "result": "执行结果"
  }
}
```

#### 7.3.7 生成表达式

```
POST /business-logic/generate-expression
```

**请求参数**: 同保存接口

**响应示例**:

```json
{
  "code": 200,
  "message": "生成成功",
  "data": {
    "aviatorExpression": "let userName = JsonPathUtil.read(inputData, '$.name');"
  }
}
```

---

## 8. 依赖关系

### 8.1 Maven 依赖

```
spring-boot-starter-web
    └── spring-boot-starter
    └── spring-web
    └── spring-webmvc

spring-boot-starter-validation
    └── hibernate-validator
    └── jakarta-validation-api

mybatis-plus-boot-starter (3.5.5)
    └── mybatis (3.5.15)
    └── mybatis-spring (2.1.2)

aviator (5.3.3)
    └── 表达式引擎

json-path (2.8.0)
    └── JsonPath 查询

mysql-connector-j (8.0.33)
    └── MySQL 驱动

spring-data-redis (2.7.18)
    └── Redis 支持

hutool-all (5.8.23)
    └── 工具类库

lombok (1.18.30)
    └── 代码简化

fastjson (1.2.83)
    └── JSON 处理
```

### 8.2 类依赖关系图

```
BusinessLogicController
    │
    ├──► BusinessLogicService
    │        │
    │        ├──► BusinessLogicMapper (MyBatis Plus)
    │        │
    │        ├──► LogicStepMapper (MyBatis Plus)
    │        │
    │        ├──► AviatorExpressionGenerator
    │        │        │
    │        │        └──► JsonPathUtil
    │        │
    │        ├──► ExpressionCache
    │        │
    │        └──► AviatorExecutor
    │                 │
    │                 └──► JsonPathUtil
    │                 └──► StringUtil
    │
    └──► ObjectMapper (Jackson)
```

---

## 9. 项目运行方式

### 9.1 环境要求

| 环境 | 要求 |
|-----|------|
| JDK | 1.8+ |
| Maven | 3.x |
| MySQL | 8.0+ |
| Redis | 3.x+ (可选) |

### 9.2 数据库初始化

1. 创建数据库:

```sql
CREATE DATABASE business_logic_editor
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;
```

2. 执行建表脚本 (`backend/src/main/resources/schema.sql`)

### 9.3 后端启动方式

#### 方式一: Maven 运行

```bash
cd backend
mvn clean compile
mvn spring-boot:run
```

#### 方式二: JAR 包运行

```bash
cd backend
mvn clean package
java -jar target/business-logic-editor-1.0.0.jar
```

#### 方式三: 批处理脚本

```bash
# Windows
run-backend.bat
```

### 9.4 配置修改

编辑 `backend/src/main/resources/application.yml`:

```yaml
server:
  port: 8080  # 修改端口

spring:
  datasource:
    url: jdbc:mysql://localhost:3306/business_logic_editor
    username: root
    password: your_password  # 修改密码

  redis:
    host: 127.0.0.1
    port: 6379
```

### 9.5 前端运行

直接打开 HTML 文件即可:

```bash
# 方式一: 直接打开
start business-logic-editor.html

# 方式二: 使用 HTTP 服务器
# 如 Live Server (VS Code 插件)
```

---

## 10. 扩展开发指南

### 10.1 新增函数类型

#### 步骤 1: 前端添加选项

在 HTML 中添加新的函数选项到选择器

#### 步骤 2: 后端添加生成方法

在 `AviatorExpressionGenerator` 中添加:

```java
private String generateNewFunction(String function, List<OperandDTO> operands) {
    if ("newFunction".equals(function)) {
        // 生成新函数表达式
        return "newFunction(" + operands + ")";
    }
    return "";
}
```

#### 步骤 3: 注册函数

如果需要自定义函数，在 `AviatorExecutor` 或 `AviatorFunctionRegistry` 中注册

### 10.2 新增操作类型

#### 步骤 1: 前端添加操作类型

在 UI 中添加新的操作类型选项

#### 步骤 2: 后端处理

在 `AviatorExpressionGenerator.generateStepExpression()` 中添加分支:

```java
if ("newOperation".equals(category)) {
    return generateNewOperation(step, stepNum);
}
```

### 10.3 自定义缓存策略

修改 `ExpressionCache` 实现:

```java
public class CustomExpressionCache {
    // 可添加过期时间
    private Map<Long, CacheEntry<Expression>> cache;

    // 或集成 Redis
    // 使用 spring-data-redis
}
```

### 10.4 热加载函数示例

```java
@Autowired
private AviatorFunctionRegistry registry;

public void registerCustomFunction() {
    // 表达式函数
    registry.registerExpressionFunction(
        "myFunc",
        "a + b * 2",
        "a", "b"
    );

    // 脚本函数
    registry.registerScriptFunction(
        "myScript",
        "let result = a + b;\nreturn result;",
        "a", "b"
    );
}
```

---

## 11. 表达式示例

### 11.1 直接映射

```java
// 前端配置
{
  "functionCategory": "direct",
  "mappedField": "name",
  "outputVar": "userName"
}

// 生成表达式
let userName = JsonPathUtil.read(inputData, '$.name');
```

### 11.2 字符串处理

```java
// 前端配置
{
  "functionCategory": "calculation",
  "calculationSteps": [{
    "functionCategory": ["string"],
    "filterFunction": "toUpperCase",
    "operands": [{"type": "field", "typeValue": "name"}]
  }],
  "outputVar": "upperName"
}

// 生成表达式
let upperName = string.to_upper_case(JsonPathUtil.read(inputData, '$.name'));
```

### 11.3 数值计算

```java
// 前端配置
{
  "functionCategory": "calculation",
  "calculationSteps": [{
    "functionCategory": ["number"],
    "filterFunction": "arithmetic",
    "operands": [
      {"type": "field", "typeValue": "price"},
      {"type": "operator", "typeValue": "*"},
      {"type": "value", "typeValue": "2"}
    ]
  }],
  "outputVar": "doublePrice"
}

// 生成表达式
let doublePrice = (JsonPathUtil.read(inputData, '$.price') * 2);
```

### 11.4 筛选逻辑

```java
// 前端配置
{
  "functionCategory": "filter",
  "filterScope": "orders",
  "filterItems": [{
    "type": "condition",
    "functionCategory": ["number"],
    "filterFunction": "greaterThan",
    "operands": [
      {"type": "field", "typeValue": "amount"},
      {"type": "value", "typeValue": "1000"}
    ]
  }],
  "filterLogic": [{
    "type": "count",
    "value": "all"
  }],
  "outputVar": "orderCount"
}

// 生成表达式
let orderCounttrue = seq.list();
for item in JsonPathUtil.read(inputData, '$.orders') {
  if ((JsonPathUtil.read(inputData, '$.orders')['amount'] > 1000)) {
    seq.add(orderCounttrue, item);
  }
}
let step1 = 0;
step1 = count(orderCounttrue);
```

---

## 12. 性能优化

### 12.1 表达式缓存

系统在保存/更新业务逻辑时会预编译表达式并缓存:

```java
// BusinessLogicService.save()
Expression compiledExpression = AviatorExecutor.compile(aviatorExpression);
expressionCache.put(businessLogic.getId(), compiledExpression);
```

### 12.2 执行时优先使用缓存

```java
// BusinessLogicService.executeLogic()
Expression compiledExpression = expressionCache.get(id);
if (compiledExpression != null) {
    result = AviatorExecutor.execute(compiledExpression, inputData);
}
```

### 12.3 数据库优化

- 为常用查询字段添加索引
- 使用逻辑删除减少数据量
- 批量操作时使用事务

---

## 13. 错误处理

### 13.1 全局异常处理

`GlobalExceptionHandler` 统一处理异常:

```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    // 处理业务异常
    // 处理参数校验异常
    // 处理系统异常
}
```

### 13.2 常见错误

| 错误码 | 说明 | 处理方式 |
|-------|------|---------|
| 500 | 系统错误 | 检查日志 |
| 400 | 参数错误 | 检查输入 |
| 404 | 资源不存在 | 检查ID |
| 401 | 未授权 | 登录认证 |

---

## 14. 配置文件说明

### 14.1 application.yml

```yaml
server:
  port: 8080                    # 服务端口
  servlet:
    context-path: /api          # 上下文路径

spring:
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://localhost:3306/business_logic_editor
    username: root
    password: your_password

  jackson:
    date-format: yyyy-MM-dd HH:mm:ss
    time-zone: GMT+8
    default-property-inclusion: non_null

  redis:
    host: 127.0.0.1
    port: 6379

mybatis-plus:
  configuration:
    map-underscore-to-camel-case: true
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl
  global-config:
    db-config:
      id-type: auto
      logic-delete-field: deleted
      logic-delete-value: 1
      logic-not-delete-value: 0

aviator:
  enabled: true
  optimize: true

logging:
  level:
    com.businesslogic: debug
    org.springframework.web: info
```

---

## 15. 参考资料

- [AviatorScript 官方文档](https://www.yuque.com/boyan-avfmj/aviatorscript)
- [JsonPath 官方文档](https://github.com/json-path/JsonPath)
- [Spring Boot 官方文档](https://spring.io/projects/spring-boot)
- [MyBatis Plus 官方文档](https://baomidou.com/)

---

## 附录 A: 术语表

| 术语 | 说明 |
|-----|------|
| DTO | Data Transfer Object，数据传输对象 |
| VO | View Object，视图对象 |
| Entity | 实体类，对应数据库表 |
| Mapper | 数据访问层接口 |
| Service | 服务层，业务逻辑处理 |
| Controller | 控制器层，API入口 |
| Expression | 表达式，AviatorScript表达式 |
| Hot Load | 热加载，运行时动态更新 |

---

*文档生成时间: 2024年*
