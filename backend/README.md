# 业务逻辑编辑器 - 后端项目

## 项目概述

这是一个基于Spring Boot的业务逻辑编辑器后端服务，用于保存前端配置的业务逻辑，并生成可执行的AviatorScript表达式。

## 技术栈

- **Spring Boot 3.2.0** - Web框架
- **MyBatis Plus 3.5.5** - ORM框架
- **AviatorScript 5.3.3** - 表达式引擎
- **JsonPath 2.8.0** - JSON路径查询
- **MySQL** - 数据库
- **Lombok** - 简化代码
- **Hutool** - 工具类库

## 项目结构

```
backend/
├── src/main/java/com/businesslogic/
│   ├── BusinessLogicEditorApplication.java    # 启动类
│   ├── common/
│   │   └── Result.java                    # 统一响应结果
│   ├── config/
│   │   └── MyMetaObjectHandler.java       # MyBatis Plus配置
│   ├── controller/
│   │   └── BusinessLogicController.java   # 控制器
│   ├── dto/
│   │   ├── BusinessLogicSaveDTO.java      # 业务逻辑保存DTO
│   │   ├── LogicStepDTO.java             # 逻辑步骤DTO
│   │   ├── CalculationStepDTO.java        # 计算步骤DTO
│   │   ├── OperandDTO.java              # 操作数DTO
│   │   ├── FilterItemDTO.java           # 筛选条件DTO
│   │   └── FilterLogicDTO.java          # 筛选逻辑DTO
│   ├── entity/
│   │   ├── BusinessLogic.java            # 业务逻辑实体
│   │   └── LogicStep.java               # 逻辑步骤实体
│   ├── exception/
│   │   └── GlobalExceptionHandler.java    # 全局异常处理
│   ├── generator/
│   │   └── AviatorExpressionGenerator.java # Aviator表达式生成器
│   ├── mapper/
│   │   ├── BusinessLogicMapper.java      # 业务逻辑Mapper
│   │   └── LogicStepMapper.java         # 逻辑步骤Mapper
│   ├── service/
│   │   └── BusinessLogicService.java     # 业务逻辑服务
│   ├── util/
│   │   └── JsonPathUtil.java            # JsonPath工具类
│   └── vo/
│       ├── BusinessLogicVO.java          # 业务逻辑VO
│       ├── LogicStepVO.java             # 逻辑步骤VO
│       ├── CalculationStepVO.java        # 计算步骤VO
│       ├── OperandVO.java              # 操作数VO
│       ├── FilterItemVO.java           # 筛选条件VO
│       └── FilterLogicVO.java          # 筛选逻辑VO
├── src/main/resources/
│   ├── application.yml                   # 应用配置
│   └── schema.sql                       # 数据库初始化脚本
└── pom.xml                             # Maven配置
```

## 数据库设计

### business_logic 表
存储业务逻辑的基本信息

| 字段名 | 类型 | 说明 |
|--------|------|------|
| id | BIGINT | 主键ID |
| name | VARCHAR(255) | 业务逻辑名称 |
| description | TEXT | 描述 |
| json_input | TEXT | 入参JSON示例 |
| aviator_expression | TEXT | 生成的Aviator表达式 |
| step_count | INT | 步骤数量 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |
| deleted | INT | 逻辑删除标记 |

### logic_step 表
存储逻辑步骤的详细信息

| 字段名 | 类型 | 说明 |
|--------|------|------|
| id | BIGINT | 主键ID |
| business_logic_id | BIGINT | 业务逻辑ID |
| step_order | INT | 步骤顺序 |
| function_category | VARCHAR(50) | 函数分类 |
| field | VARCHAR(500) | 主字段 |
| function_name | VARCHAR(100) | 函数名 |
| params | TEXT | 参数JSON |
| custom_expression | TEXT | 自定义表达式 |
| output_var | VARCHAR(100) | 输出变量名 |
| comment | TEXT | 备注说明 |
| filter_scope | VARCHAR(500) | 筛选范围 |
| mapped_field | VARCHAR(500) | 映射字段 |
| calculation_steps | TEXT | 计算步骤JSON |
| filter_items | TEXT | 筛选条件JSON |
| filter_logic | TEXT | 满足条件时执行JSON |
| reverse_logic | TEXT | 条件不符时执行JSON |
| collapsed | TINYINT | 是否折叠 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |
| deleted | INT | 逻辑删除标记 |

## API接口

### 1. 保存业务逻辑
**POST** `/api/business-logic`

**请求体**：
```json
{
  "name": "订单处理逻辑",
  "description": "处理订单的业务逻辑",
  "jsonInput": "{\"name\":\"张三\",\"age\":25}",
  "logicSteps": [
    {
      "functionCategory": "direct",
      "mappedField": "name",
      "outputVar": "userName",
      "comment": "映射用户名"
    }
  ]
}
```

**响应**：
```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "id": 1,
    "name": "订单处理逻辑",
    "description": "处理订单的业务逻辑",
    "jsonInput": "{\"name\":\"张三\",\"age\":25}",
    "aviatorExpression": "// 生成的Aviator表达式...",
    "stepCount": 1,
    "createdAt": "2024-01-01 00:00:00",
    "updatedAt": "2024-01-01 00:00:00",
    "logicSteps": [...]
  }
}
```

### 2. 更新业务逻辑
**PUT** `/api/business-logic/{id}`

**请求体**：同保存接口

**响应**：同保存接口

### 3. 查询业务逻辑
**GET** `/api/business-logic/{id}`

**响应**：同保存接口的data部分

### 4. 查询所有业务逻辑
**GET** `/api/business-logic`

**响应**：
```json
{
  "code": 200,
  "message": "操作成功",
  "data": [
    {
      "id": 1,
      "name": "订单处理逻辑",
      ...
    }
  ]
}
```

### 5. 删除业务逻辑
**DELETE** `/api/business-logic/{id}`

**响应**：
```json
{
  "code": 200,
  "message": "操作成功",
  "data": null
}
```

### 6. 执行业务逻辑
**POST** `/api/business-logic/{id}/execute`

**请求体**：
```json
{
  "name": "张三",
  "age": 25
}
```

**响应**：
```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "result": "执行结果"
  }
}
```

## AviatorScript表达式生成

### 支持的函数分类

#### 1. 直接映射（direct）
将输入字段直接映射到输出

**示例**：
```
let userName = name;
```

#### 2. 直接计算（calculation）
对字段进行计算处理

**字符串函数**：
- `string.starts_with(str, prefix)` - 字符串开始于
- `string.ends_with(str, suffix)` - 字符串结束于
- `string.contains(str, substring)` - 字符串包含
- `string.substring(str, start, length)` - 字符串截取
- `string.to_lower_case(str)` - 转小写
- `string.to_upper_case(str)` - 转大写
- `string.trim(str)` - 去除空格
- `string.replace_all(str, old, new)` - 替换
- `string.split(str, separator)` - 分割
- `string.join(str1, str2)` - 拼接
- `string.equals(str1, str2)` - 判断相等
- `string.length(str)` - 字符串长度

**数值函数**：
- `math.abs(num)` - 绝对值
- `math.ceil(num)` - 向上取整
- `math.floor(num)` - 向下取整
- `math.round(num)` - 四舍五入
- `math.max(num1, num2)` - 最大值
- `math.min(num1, num2)` - 最小值
- `math.pow(base, exponent)` - 幂运算
- `math.sqrt(num)` - 平方根
- `math.mod(dividend, divisor)` - 取模

**日期函数**：
- `date.diff_months(date1, date2)` - 月数差
- `date.diff_days(date1, date2)` - 天数差
- `date.diff_years(date1, date2)` - 年数差
- `date.before(date1, date2)` - 日期早于
- `date.after(date1, date2)` - 日期晚于
- `date.equal(date1, date2)` - 日期相等
- `date.add_days(date, days)` - 增加天数
- `date.add_months(date, months)` - 增加月份
- `date.add_years(date, years)` - 增加年份
- `date.year(date)` - 获取年份
- `date.month(date)` - 获取月份
- `date.day(date)` - 获取日期

#### 3. 筛选（filter）
根据条件筛选数据

**示例**：
```
let step1 = if (string.starts_with(name, "张")) then name else nil end;
```

#### 4. 手动输入逻辑（custom）
自定义表达式

**示例**：
```
let step1 = name + "_" + age;
```

## JsonPath使用

### 基本语法
- `$.name` - 获取name字段
- `$.user.name` - 获取user对象的name字段
- `$.items[0]` - 获取数组的第一个元素
- `$.items[*].name` - 获取数组中所有元素的name字段

### 工具类方法

```java
// 读取任意类型
Object result = JsonPathUtil.read(json, "$.name");

// 读取字符串
String name = JsonPathUtil.readString(json, "$.name");

// 读取整数
Integer age = JsonPathUtil.readInt(json, "$.age");

// 读取长整数
Long id = JsonPathUtil.readLong(json, "$.id");

// 读取浮点数
Double price = JsonPathUtil.readDouble(json, "$.price");

// 读取布尔值
Boolean active = JsonPathUtil.readBoolean(json, "$.active");

// 读取列表
List<String> tags = JsonPathUtil.readList(json, "$.tags", String.class);

// 检查路径是否存在
boolean exists = JsonPathUtil.exists(json, "$.name");
```

## 前后端交互

### 1. 保存逻辑流程
1. 前端收集用户配置的业务逻辑
2. 调用 `POST /api/business-logic` 保存
3. 后端生成AviatorScript表达式
4. 保存到数据库并返回结果

### 2. 加载逻辑流程
1. 前端调用 `GET /api/business-logic/{id}`
2. 后端从数据库读取业务逻辑和步骤
3. 返回完整的配置信息
4. 前端反显到编辑器

### 3. 执行逻辑流程
1. 前端收集输入数据
2. 调用 `POST /api/business-logic/{id}/execute`
3. 后端使用AviatorScript引擎执行表达式
4. 返回执行结果

## 部署说明

### 环境要求
- JDK 17+
- Maven 3.6+
- MySQL 5.7+

### 配置步骤

1. **创建数据库**
```bash
mysql -u root -p < src/main/resources/schema.sql
```

2. **修改配置**
编辑 `src/main/resources/application.yml`，修改数据库连接信息：
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/business_logic_editor
    username: root
    password: your_password
```

3. **编译打包**
```bash
mvn clean package
```

4. **运行**
```bash
java -jar target/business-logic-editor-1.0.0.jar
```

### Docker部署

创建 `Dockerfile`：
```dockerfile
FROM openjdk:17-jdk-slim
WORKDIR /app
COPY target/business-logic-editor-1.0.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

构建并运行：
```bash
docker build -t business-logic-editor .
docker run -p 8080:8080 business-logic-editor
```

## 扩展性设计

### 1. 新增函数类型
在 `AviatorExpressionGenerator` 中添加新的函数处理方法：
```java
private String generateNewFunction(String function, List<OperandDTO> operands) {
    // 实现新的函数生成逻辑
}
```

### 2. 新增执行操作
在 `generateFilterLogicExecution` 方法中添加新的执行类型：
```java
case "newExecutionType":
    return generateNewExecution(logic);
```

### 3. 自定义字段类型
扩展实体类和DTO，添加新的字段类型支持。

## 注意事项

1. **步骤数量限制**：最多支持5个逻辑步骤
2. **嵌套层级限制**：筛选条件最多支持2层嵌套
3. **字段引用**：只能引用已解析的字段
4. **类型匹配**：确保字段类型与函数要求匹配
5. **性能优化**：避免配置过于复杂的逻辑

## 常见问题

### Q1: AviatorScript表达式执行失败怎么办？
A: 检查表达式语法是否正确，确保字段名和参数类型匹配。

### Q2: 如何调试生成的表达式？
A: 查看数据库中保存的 `aviator_expression` 字段，或使用日志输出。

### Q3: 支持哪些数据类型？
A: 支持字符串、数值、布尔值、日期、数组、对象等基本类型。

### Q4: 如何扩展新的函数？
A: 在 `AviatorExpressionGenerator` 中添加对应的生成方法。

## 技术支持

- AviatorScript文档：https://www.yuque.com/boyan-avfmj/aviatorscript
- JsonPath文档：https://github.com/json-path/JsonPath
- Spring Boot文档：https://spring.io/projects/spring-boot
- MyBatis Plus文档：https://baomidou.com/

## 许可证

MIT License
