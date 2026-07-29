# 业务逻辑编辑器 - 全栈项目说明

## 项目简介

业务逻辑编辑器是一个完整的全栈解决方案，包含前端可视化编辑器和后端服务。用户可以通过图形化界面配置业务逻辑，后端自动生成可执行的AviatorScript表达式，并支持逻辑的保存、加载和执行。

## 系统架构

```
┌─────────────────────────────────────────────────────────────┐
│                    前端（Vue 3）                       │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────┐ │
│  │ JSON输入区   │  │ 逻辑编辑区  │  │ 预览区  │ │
│  └──────────────┘  └──────────────┘  └──────────┘ │
└─────────────────────────────────────────────────────────────┘
                          │ HTTP/REST API
                          ▼
┌─────────────────────────────────────────────────────────────┐
│              后端（Spring Boot 3.2）                    │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────┐ │
│  │ Controller   │  │   Service    │  │ Generator│ │
│  │   层        │  │    层        │  │  层      │ │
│  └──────────────┘  └──────────────┘  └──────────┘ │
│         │                  │                  │          │
│         ▼                  ▼                  ▼          │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────┐ │
│  │  Mapper     │  │ JsonPath工具 │  │Aviator   │ │
│  │   层        │  │             │  │  引擎    │ │
│  └──────────────┘  └──────────────┘  └──────────┘ │
│         │                                             │
│         ▼                                             │
│  ┌──────────────────────────────────────────────┐         │
│  │         MySQL 数据库                    │         │
│  └──────────────────────────────────────────────┘         │
└─────────────────────────────────────────────────────────────┘
```

## 技术选型

### 前端技术栈
- **Vue 3** - 渐进式JavaScript框架
- **Vue Router** - 路由管理
- **Axios** - HTTP客户端
- **Element Plus** - UI组件库（可选）

### 后端技术栈
- **Spring Boot 3.2.0** - Java Web框架
- **MyBatis Plus 3.5.5** - ORM框架
- **AviatorScript 5.3.3** - 表达式引擎
- **JsonPath 2.8.0** - JSON路径查询
- **MySQL 8.0** - 关系型数据库

### 核心技术说明

#### AviatorScript
AviatorScript是一个高性能、轻量级的Java表达式求值引擎，支持：
- 基本算术运算
- 逻辑运算
- 字符串操作
- 日期处理
- 自定义函数

**参考文档**：https://www.yuque.com/boyan-avfmj/aviatorscript

#### JsonPath
JsonPath是一种用于从JSON文档中提取数据的查询语言，类似于XPath用于XML。

**示例**：
```javascript
// JSON数据
{
  "store": {
    "book": [
      { "category": "reference", "author": "Nigel Rees" }
    ]
  }
}

// JsonPath表达式
$.store.book[*].author  // 获取所有书籍的作者
$.store.book[0].author  // 获取第一本书的作者
```

## 核心功能

### 1. 前端功能

#### 1.1 接口入参配置
- JSON格式输入
- 自动解析字段
- 树形结构展示
- 字段类型标识

#### 1.2 业务逻辑编辑
- 步骤化管理（最多5步）
- 四种操作类型：
  - 直接映射
  - 直接计算
  - 筛选
  - 手动输入逻辑
- 支持嵌套条件（最多2层）
- 实时预览逻辑流程

#### 1.3 逻辑预览
- 可视化展示数据流转
- 显示筛选条件
- 显示执行逻辑
- 显示备注说明

### 2. 后端功能

#### 2.1 业务逻辑管理
- 保存业务逻辑
- 更新业务逻辑
- 查询业务逻辑
- 删除业务逻辑
- 查询所有业务逻辑

#### 2.2 AviatorScript生成
- 自动生成表达式
- 支持所有前端配置的操作
- 包含详细注释
- 可直接执行

#### 2.3 逻辑执行
- 使用AviatorScript引擎执行
- 支持动态输入数据
- 返回执行结果
- 异常处理

#### 2.4 数据持久化
- 业务逻辑表
- 逻辑步骤表
- 支持逻辑删除
- 自动时间戳

## 数据流转

### 保存流程
```
前端用户配置
    │
    ▼
收集业务逻辑数据
    │
    ▼
调用保存API
    │
    ▼
后端接收请求
    │
    ▼
生成AviatorScript表达式
    │
    ▼
保存到数据库
    │
    ▼
返回保存结果
    │
    ▼
前端显示成功
```

### 加载流程
```
用户选择业务逻辑
    │
    ▼
调用查询API
    │
    ▼
后端查询数据库
    │
    ▼
返回业务逻辑详情
    │
    ▼
前端反显到编辑器
    │
    ▼
用户可以继续编辑
```

### 执行流程
```
用户输入数据
    │
    ▼
调用执行API
    │
    ▼
后端加载AviatorScript表达式
    │
    ▼
使用AviatorScript引擎执行
    │
    ▼
返回执行结果
    │
    ▼
前端显示结果
```

## API接口文档

### 基础信息
- **Base URL**: `http://localhost:8080/api`
- **Content-Type**: `application/json`
- **响应格式**: JSON

### 接口列表

#### 1. 保存业务逻辑
**接口**: `POST /business-logic`

**请求参数**：
```json
{
  "name": "业务逻辑名称",
  "description": "业务逻辑描述",
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

**响应示例**：
```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "id": 1,
    "name": "业务逻辑名称",
    "description": "业务逻辑描述",
    "jsonInput": "{\"name\":\"张三\",\"age\":25}",
    "aviatorExpression": "// 生成的AviatorScript表达式...",
    "stepCount": 1,
    "createdAt": "2024-01-01 00:00:00",
    "updatedAt": "2024-01-01 00:00:00",
    "logicSteps": [...]
  }
}
```

#### 2. 更新业务逻辑
**接口**: `PUT /business-logic/{id}`

**请求参数**：同保存接口

**响应示例**：同保存接口

#### 3. 查询业务逻辑
**接口**: `GET /business-logic/{id}`

**响应示例**：同保存接口的data部分

#### 4. 查询所有业务逻辑
**接口**: `GET /business-logic`

**响应示例**：
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

#### 5. 删除业务逻辑
**接口**: `DELETE /business-logic/{id}`

**响应示例**：
```json
{
  "code": 200,
  "message": "操作成功",
  "data": null
}
```

#### 6. 执行业务逻辑
**接口**: `POST /business-logic/{id}/execute`

**请求参数**：
```json
{
  "name": "张三",
  "age": 25
}
```

**响应示例**：
```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "result": "执行结果"
  }
}
```

## AviatorScript表达式示例

### 示例1：直接映射
```
// 前端配置
{
  "functionCategory": "direct",
  "mappedField": "name",
  "outputVar": "userName"
}

// 生成的AviatorScript表达式
let userName = name;
```

### 示例2：字符串处理
```
// 前端配置
{
  "functionCategory": "calculation",
  "calculationSteps": [
    {
      "functionCategory": "string",
      "filterFunction": "toUpperCase",
      "operands": [
        {
          "type": "field",
          "field": "name"
        }
      ]
    }
  ],
  "outputVar": "upperName"
}

// 生成的AviatorScript表达式
let upperName = string.to_upper_case(name);
```

### 示例3：数值计算
```
// 前端配置
{
  "functionCategory": "calculation",
  "calculationSteps": [
    {
      "functionCategory": "number",
      "filterFunction": "greaterThan",
      "operands": [
        {
          "type": "field",
          "field": "age"
        },
        {
          "type": "value",
          "value": "18"
        }
      ]
    }
  ],
  "outputVar": "isAdult"
}

// 生成的AviatorScript表达式
let isAdult = (age > 18);
```

### 示例4：筛选逻辑
```
// 前端配置
{
  "functionCategory": "filter",
  "filterScope": "orders",
  "filterItems": [
    {
      "type": "condition",
      "functionCategory": "number",
      "filterFunction": "greaterThan",
      "operands": [
        {
          "type": "field",
          "field": "amount"
        },
        {
          "type": "value",
          "value": "1000"
        }
      ]
    }
  ],
  "filterLogic": [
    {
      "executionType": "returnValue",
      "returnType": "fixed",
      "fixedValue": "高价值订单"
    }
  ],
  "reverseLogic": [
    {
      "executionType": "returnValue",
      "returnType": "fixed",
      "fixedValue": "普通订单"
    }
  ],
  "outputVar": "orderType"
}

// 生成的AviatorScript表达式
let orderType = if (amount > 1000) then "高价值订单" else "普通订单" end;
```

### 示例5：自定义表达式
```
// 前端配置
{
  "functionCategory": "custom",
  "customExpression": "name + '_' + age",
  "outputVar": "combined"
}

// 生成的AviatorScript表达式
let combined = name + "_" + age;
```

## 部署指南

### 前端部署

#### 开发环境
```bash
# 安装依赖
npm install

# 启动开发服务器
npm run dev
```

#### 生产环境
```bash
# 构建生产版本
npm run build

# 部署到Nginx或其他Web服务器
# 将dist目录下的文件部署到服务器
```

### 后端部署

#### 开发环境
```bash
# 编译项目
mvn clean compile

# 运行项目
mvn spring-boot:run
```

#### 生产环境
```bash
# 打包项目
mvn clean package

# 运行JAR包
java -jar target/business-logic-editor-1.0.0.jar
```

#### Docker部署
```bash
# 构建Docker镜像
docker build -t business-logic-editor .

# 运行容器
docker run -d -p 8080:8080 --name business-logic business-logic-editor
```

## 扩展开发

### 新增函数类型

1. 在前端添加新的函数选项
2. 在后端 `AviatorExpressionGenerator` 中添加对应的生成方法
3. 更新文档

### 新增执行操作

1. 在前端添加新的操作类型选项
2. 在后端 `generateFilterLogicExecution` 方法中添加处理逻辑
3. 测试验证

### 自定义字段类型

1. 扩展数据库表结构
2. 更新实体类和DTO
3. 修改生成器和执行逻辑

## 性能优化

### 前端优化
- 使用虚拟滚动处理大量数据
- 懒加载组件
- 防抖和节流
- 缓存常用数据

### 后端优化
- 数据库索引优化
- 查询结果缓存
- 异步处理
- 连接池配置

## 安全考虑

### 前端安全
- 输入验证
- XSS防护
- CSRF防护
- 敏感数据加密

### 后端安全
- 参数校验
- SQL注入防护
- 权限控制
- 日志审计

## 监控和日志

### 前端监控
- 错误日志收集
- 性能监控
- 用户行为分析

### 后端监控
- 应用日志
- 性能指标
- 异常告警
- 数据库监控

## 常见问题

### Q1: 前端如何与后端集成？
A: 参考 `frontend-api-example.js` 文件，使用fetch或axios调用后端API。

### Q2: AviatorScript表达式如何调试？
A: 查看数据库中保存的 `aviator_expression` 字段，或使用日志输出。

### Q3: 如何支持更多的函数？
A: 在 `AviatorExpressionGenerator` 中添加对应的生成方法，并在前端添加选项。

### Q4: 数据库迁移如何处理？
A: 使用Flyway或Liquibase进行数据库版本管理。

### Q5: 如何实现权限控制？
A: 集成Spring Security或Shiro实现认证和授权。

## 技术支持

- **AviatorScript文档**: https://www.yuque.com/boyan-avfmj/aviatorscript
- **JsonPath文档**: https://github.com/json-path/JsonPath
- **Spring Boot文档**: https://spring.io/projects/spring-boot
- **MyBatis Plus文档**: https://baomidou.com/
- **Vue 3文档**: https://vuejs.org/

## 项目结构

```
业务逻辑编辑/
├── business-logic-editor.html    # 前端HTML文件
├── 功能说明文档.md                # 前端功能说明
├── backend/                       # 后端项目
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/
│   │   │   │   └── com/businesslogic/
│   │   │   │       ├── BusinessLogicEditorApplication.java
│   │   │   │       ├── common/
│   │   │   │       ├── config/
│   │   │   │       ├── controller/
│   │   │   │       ├── dto/
│   │   │   │       ├── entity/
│   │   │   │       ├── exception/
│   │   │   │       ├── generator/
│   │   │   │       ├── mapper/
│   │   │   │       ├── service/
│   │   │   │       ├── util/
│   │   │   │       └── vo/
│   │   │   └── resources/
│   │   │       ├── application.yml
│   │   │       └── schema.sql
│   │   └── test/
│   ├── pom.xml
│   ├── README.md
│   └── frontend-api-example.js
└── README.md                         # 项目总说明
```

## 总结

业务逻辑编辑器是一个功能完整的全栈解决方案，具有以下特点：

1. **可视化操作**：通过图形化界面配置业务逻辑，无需编写代码
2. **灵活扩展**：支持多种数据处理方式，易于扩展新功能
3. **高性能**：使用AviatorScript引擎，表达式执行高效
4. **易维护**：前后端分离，代码结构清晰
5. **生产就绪**：包含完整的异常处理、日志记录和部署方案

通过合理使用各个功能模块，可以快速实现数据转换、筛选、统计等常见业务需求，大大提高开发效率。
