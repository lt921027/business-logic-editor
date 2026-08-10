package com.businesslogic.groovy.security;

import com.businesslogic.groovy.engine.GroovyExpressionEngine;
import org.codehaus.groovy.ast.expr.VariableExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.ClosureListExpression;
import org.codehaus.groovy.ast.expr.EmptyExpression;
import org.codehaus.groovy.ast.stmt.DoWhileStatement;
import org.codehaus.groovy.ast.stmt.ForStatement;
import org.codehaus.groovy.ast.stmt.Statement;
import org.codehaus.groovy.ast.stmt.WhileStatement;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.SecureASTCustomizer;
import org.codehaus.groovy.control.customizers.ImportCustomizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Groovy 安全沙箱配置
 *
 * <p>通过 SecureASTCustomizer 在 AST 级别限制脚本能访问的类和方法，
 * 防止恶意脚本执行系统命令、文件操作、反射等危险操作。
 *
 * <p>安全策略：
 * <ul>
 *   <li>白名单导入：仅允许 java.util、java.math 和项目工具类</li>
 *   <li>黑名单接收者：禁止 System、Runtime、ProcessBuilder、Thread、ClassLoader、File 等</li>
 *   <li>黑名单语句：禁止 import 语句</li>
 *   <li>间接导入检查：防止通过别名将黑名单类导入</li>
 * </ul>
 */
public class GroovySandbox {

    private static final Logger logger = LoggerFactory.getLogger(GroovySandbox.class);

    /**
     * 允许导入的类白名单（全限定名）。
     *
     * <p>为何需要：SecureASTCustomizer 默认禁止任何 import，业务脚本所需的基础类型与项目工具类必须在此显式列出，
     * 否则脚本中出现 `new ArrayList()`、`JsonPathUtil.read(...)` 等会编译失败。
     *
     * <p>关联：与 {@link #createSecureCompilerConfiguration()} 中的
     * {@link SecureASTCustomizer#setImportsWhitelist(List)} 联用；与 {@link ImportCustomizer} 的 addImports 配合
     * （ImportCustomizer 负责自动预导入，白名单负责允许业务脚本主动 import）。
     */
    private static final List<String> IMPORTS_WHITELIST = Collections.unmodifiableList(Arrays.asList(
            // java.lang.Object：脚本中 `def x = ...` 的变量静态类型是 Object，
            // 动态接收者上的方法调用（如 list << item、map['k']、list.size()）会被
            // 间接导入检查按接收者类型校验，必须在白名单内才能放行。
            // 安全补偿见 FORBIDDEN_EXPRESSION_PATTERNS（黑名单全限定名扫描）。
            "java.lang.Object",
            // java.lang.String：生成器会对字符串调用 .length()/.contains()/.toUpperCase() 等方法，
            // 间接导入检查按接收者类型校验，必须在白名单内才能放行；String 本身无危险方法。
            "java.lang.String",
            // java.lang.Math：生成器通过静态星号导入生成 Math.max/Math.min，
            // 间接导入检查按接收者类型校验；Math 只有纯函数，无 IO/反射等危险能力。
            "java.lang.Math",
            // 数值包装类型：脚本对整数调用 .times{} 等闭包方法时接收者类型为 Integer/Long
            "java.lang.Integer",
            "java.lang.Long",
            // 基本类型名：int/long/double 等字面量上的方法调用（如 100.times{}）接收者类型是原始类型名，
            // 间接导入检查按该名字校验；它们不是类、无法 import，白名单放行无安全含义
            "int", "long", "double", "float", "boolean", "char", "short", "byte",
            "groovy.lang.Range",
            "groovy.lang.IntRange",
            "groovy.lang.LongRange",
            "java.util.Date",
            "java.util.List",
            "java.util.Map",
            "java.util.ArrayList",
            "java.util.HashMap",
            "java.util.LinkedHashMap",
            "java.util.Set",
            "java.util.HashSet",
            "java.math.BigDecimal",
            "java.math.BigInteger",
            "com.businesslogic.util.JsonPathUtil",
            "com.businesslogic.util.StringUtil",
            "com.businesslogic.groovy.util.GroovyDateFunctions"
    ));

    /**
     * 允许的 star import 包白名单。
     *
     * <p>为何需要：业务脚本中常用 `java.util.*` 下的集合类型，开启 star import 后可避免逐个声明。
     * 仅放开 java.util 一个包，避免意外暴露其他包中的危险类。
     */
    private static final List<String> STAR_IMPORTS_WHITELIST = Collections.unmodifiableList(Arrays.asList(
            "java.util"
    ));

    /**
     * 禁止作为方法调用接收者的类（全限定名）。
     *
     * <p>为何需要：即使 import 白名单收紧，业务脚本仍可能直接通过全限定名（如 `java.lang.Runtime.getRuntime()`）
     * 调用危险方法。黑名单在 AST 级别拦截这些类作为接收者，确保无法执行系统命令、文件 IO、网络访问、反射等。
     *
     * <p>关联：由 {@link SecureASTCustomizer#setReceiversBlackList(List)} 启用；
     * 与白名单形成"双保险"——白名单管 import，黑名单管直接调用。
     */
    private static final List<String> RECEIVERS_BLACKLIST = Collections.unmodifiableList(Arrays.asList(
            "java.lang.System",
            "java.lang.Runtime",
            "java.lang.ProcessBuilder",
            "java.lang.Thread",
            "java.lang.ClassLoader",
            "java.lang.Class",
            "java.lang.Process",
            "java.io.File",
            "java.io.FileInputStream",
            "java.io.FileOutputStream",
            "java.io.FileReader",
            "java.io.FileWriter",
            "java.io.BufferedReader",
            "java.io.BufferedWriter",
            "java.io.PrintWriter",
            "java.io.InputStream",
            "java.io.OutputStream",
            "java.net.URL",
            "java.net.Socket",
            "java.net.ServerSocket",
            "java.net.HttpURLConnection",
            "java.lang.reflect.Method",
            "java.lang.reflect.Field",
            "java.lang.reflect.Constructor",
            "groovy.lang.GroovyClassLoader",
            "groovy.lang.GroovyShell",
            "groovy.lang.GroovyObject",
            "groovy.util.GroovyScriptEngine",
            "groovy.util.Eval"
    ));

    /**
     * 禁止的语句类型。
     *
     * <p>为何禁止 while/do-while：这是 `while(true){}` 挂死请求线程（DoS）最直接的入口，
     * 表达式生成器从不产出这两种语句；配合 {@link LoopBudgetCustomizer} 的循环预算注入，
     * 即使未来放开也仍受执行次数限制。for 循环保留给筛选步骤使用。
     *
     * <p>关联：被 {@link #createSecureCompilerConfiguration()} 通过
     * {@link SecureASTCustomizer#setStatementsBlacklist(List)} 调用。
     */
    private static final List<Class<? extends Statement>> STATEMENTS_BLACKLIST = Collections.unmodifiableList(
            Arrays.asList(WhileStatement.class, DoWhileStatement.class));

    /**
     * 表达式黑名单模式（全限定名/包前缀）。
     *
     * <p>为何需要：间接导入检查 + imports 白名单 + 接收者黑名单能拦截"直接"调用黑名单类，
     * 但脚本仍可通过别名绕过静态类型检查，例如
     * <pre>def R = java.lang.Runtime; R.getRuntime().exec('calc')</pre>
     * 其中 R 的静态类型是 Object，接收者黑名单（按类型匹配）无法命中。
     * 这里在表达式 AST 层扫描文本，凡出现黑名单类的全限定名或危险包前缀（java.io/java.net 等）
     * 一律拒绝，堵住 `def X = 黑名单类; X.xxx()` 这类别名绕过。
     *
     * <p>为何用文本扫描：AST 阶段拿不到运行时类型，无法区分 `def x = []` 与 `def R = Runtime`；
     * 按全限定名/包前缀匹配能覆盖所有直接书写黑名单类的路径（普通业务脚本不会引用这些名字）。
     *
     * <p>关联：通过 {@link SecureASTCustomizer#addExpressionCheckers} 注入，
     * 对脚本中每个表达式节点执行；与 RECEIVERS_BLACKLIST 形成双保险。
     */
    private static final List<String> FORBIDDEN_EXPRESSION_PATTERNS = Collections.unmodifiableList(Arrays.asList(
            "java.lang.System",
            "java.lang.Runtime",
            "java.lang.ProcessBuilder",
            "java.lang.Thread",
            "java.lang.ClassLoader",
            "java.lang.Class",
            "java.lang.Process",
            "java.io.File",
            "java.io.FileInputStream",
            "java.io.FileOutputStream",
            "java.io.FileReader",
            "java.io.FileWriter",
            "java.io.BufferedReader",
            "java.io.BufferedWriter",
            "java.io.PrintWriter",
            "java.io.InputStream",
            "java.io.OutputStream",
            "java.net.URL",
            "java.net.Socket",
            "java.net.ServerSocket",
            "java.net.HttpURLConnection",
            "java.lang.reflect.Method",
            "java.lang.reflect.Field",
            "java.lang.reflect.Constructor",
            "groovy.lang.GroovyClassLoader",
            "groovy.lang.GroovyShell",
            "groovy.lang.GroovyObject",
            "groovy.util.GroovyScriptEngine",
            "groovy.util.Eval",
            // 包前缀：同包下其他危险类（如 java.io.FileOutputStream）也一并拦截
            "java.io.",
            "java.net.",
            "java.lang.reflect."
    ));

    /**
     * 黑名单类的裸类名（取自 {@link #RECEIVERS_BLACKLIST} 的简单名）。
     *
     * <p>为何需要：Groovy 脚本自动导入 java.lang.* / java.util.* / java.io.* 等包，
     * 用户可写 `def R = Runtime; R.getRuntime()`——RHS 只是裸名 "Runtime"，
     * 全限定名文本扫描（FORBIDDEN_EXPRESSION_PATTERNS）无法命中；
     * 变量 R 的静态类型是 Object，接收者黑名单按类型匹配也拦不住。
     * 因此对变量表达式名做精确匹配：凡变量名等于黑名单类的简单名（如 Runtime/File/Class）即拒绝。
     *
     * <p>关联：被 {@link #isForbidden(Expression)} 使用，与全限定名扫描一起构成表达式检查器。
     */
    private static final java.util.Set<String> FORBIDDEN_BARE_CLASS_NAMES = Collections.unmodifiableSet(
            RECEIVERS_BLACKLIST.stream()
                    .map(fqn -> fqn.substring(fqn.lastIndexOf('.') + 1))
                    .collect(java.util.stream.Collectors.toSet()));

    /** 安全编译器配置，由 {@link #createSecureCompilerConfiguration()} 一次性构建，供外部 GroovyClassLoader 使用 */
    private final CompilerConfiguration compilerConfiguration;

    /**
     * 构造沙箱：构建安全编译器配置。
     *
     * <p>关联：被 {@link com.businesslogic.groovy.engine.GroovyExpressionEngine#GroovyExpressionEngine()}
     * 调用以初始化 GroovyClassLoader；后者在 {@link GroovyExpressionEngine#compile(String)} 中
     * 实际执行带沙箱的解析。
     */
    public GroovySandbox() {
        this.compilerConfiguration = createSecureCompilerConfiguration();
        logger.info("[GroovySandbox] 安全沙箱初始化完成，白名单类数={}, 黑名单接收者数={}",
                IMPORTS_WHITELIST.size(), RECEIVERS_BLACKLIST.size());
    }

    /**
     * 组装带安全限制的编译器配置：SecureASTCustomizer + ImportCustomizer。
     *
     * <p>为何分两层 Customizer：
     * <ul>
     *   <li>SecureASTCustomizer：在 AST 解析阶段拦截非法 import / 黑名单接收者 / 间接导入，"事前防御"</li>
     *   <li>ImportCustomizer：在脚本编译前自动注入常用工具类（Date/ArrayList/JsonPathUtil 等），
     *       让业务脚本无需写 import 也能直接用，同时只在白名单范围内预导入，不会引入风险</li>
     * </ul>
     *
     * <p>关联：返回的 {@link CompilerConfiguration} 被
     * {@link com.businesslogic.groovy.engine.GroovyExpressionEngine} 传入
     * {@link groovy.lang.GroovyClassLoader} 构造器，所有后续 parseClass 都受此配置约束。
     *
     * @return 已注入安全策略的编译器配置
     */
    private CompilerConfiguration createSecureCompilerConfiguration() {
        CompilerConfiguration config = new CompilerConfiguration();

        // 1. SecureASTCustomizer - AST 级别安全限制
        SecureASTCustomizer secureCustomizer = new SecureASTCustomizer();

        // 白名单导入：限制业务脚本可主动 import 的类，详见 IMPORTS_WHITELIST 注释
        secureCustomizer.setImportsWhitelist(IMPORTS_WHITELIST);
        secureCustomizer.setStarImportsWhitelist(STAR_IMPORTS_WHITELIST);

        // 间接导入检查（防止通过别名绕过白名单，如 def R = java.lang.Runtime; R.getRuntime()）
        secureCustomizer.setIndirectImportCheckEnabled(true);

        // 表达式检查器：扫描黑名单全限定名/危险包前缀，堵住 def 别名绕过（见 FORBIDDEN_EXPRESSION_PATTERNS）
        secureCustomizer.addExpressionCheckers(expression -> !isForbidden(expression));

        // 黑名单接收者：禁止直接调用这些类的方法，详见 RECEIVERS_BLACKLIST 注释
        secureCustomizer.setReceiversBlackList(RECEIVERS_BLACKLIST);

        // 语句黑名单：禁止 while/do-while（见 STATEMENTS_BLACKLIST 注释）
        secureCustomizer.setStatementsBlacklist(STATEMENTS_BLACKLIST);

        // 语句检查器：拒绝 for(;;)（无迭代集合的 for 等价于无限循环）；
        // 语句总数上限在 LoopBudgetCustomizer 中按单次编译统计
        // 语句检查器：拒绝 C 风格无限 for（for(;;) / for(i=0;;i++)，条件位是 EmptyExpression）
        secureCustomizer.addStatementCheckers(statement -> {
            if (statement instanceof ForStatement) {
                Expression collection = ((ForStatement) statement).getCollectionExpression();
                if (collection instanceof ClosureListExpression) {
                    java.util.List<Expression> parts = ((ClosureListExpression) collection).getExpressions();
                    if (parts.size() == 3 && parts.get(1) instanceof EmptyExpression) {
                        return false;
                    }
                }
            }
            return true;
        });

        // 2. ImportCustomizer - 预导入安全类（业务脚本无需写 import 即可直接用）
        ImportCustomizer importCustomizer = new ImportCustomizer();
        importCustomizer.addImports(
                "java.util.Date",
                "java.util.List",
                "java.util.Map",
                "java.util.ArrayList",
                "java.util.HashMap",
                "com.businesslogic.util.JsonPathUtil",
                "com.businesslogic.util.StringUtil",
                "com.businesslogic.groovy.util.GroovyDateFunctions"
        );
        // 预导入 Math 静态方法用于 Math.max / Math.min（GroovyExpressionGenerator 生成的脚本会用到）
        importCustomizer.addStaticStars("java.lang.Math");

        // 3. LoopBudgetCustomizer - 编译期向循环体/闭包体注入执行次数预算检查
        // 注册在 SecureASTCustomizer 之后，保证注入的节点不会再被其复查
        config.addCompilationCustomizers(secureCustomizer, importCustomizer, new LoopBudgetCustomizer());

        return config;
    }

    /**
     * 判断表达式是否命中黑名单：
     * - 变量表达式名等于黑名单类的裸类名（如 Runtime/File/Class）
     * - 表达式文本包含黑名单类的全限定名或危险包前缀
     *
     * <p>关联：被 {@link #createSecureCompilerConfiguration()} 注入的表达式检查器调用。
     *
     * @param expression 待检查的 AST 表达式节点
     * @return true 表示命中黑名单（应拒绝该表达式）
     */
    private static boolean isForbidden(Expression expression) {
        if (expression instanceof VariableExpression) {
            String name = ((VariableExpression) expression).getName();
            if (FORBIDDEN_BARE_CLASS_NAMES.contains(name)) {
                return true;
            }
        }
        String text = expression.getText();
        if (text == null) {
            return false;
        }
        for (String pattern : FORBIDDEN_EXPRESSION_PATTERNS) {
            if (text.contains(pattern)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取安全编译器配置。
     *
     * <p>关联：被 {@link com.businesslogic.groovy.engine.GroovyExpressionEngine} 的构造器调用以创建
     * {@link groovy.lang.GroovyClassLoader}；所有 parseClass 调用最终都会走这份配置。
     *
     * @return 安全编译器配置（不可变引用）
     */
    public CompilerConfiguration getCompilerConfiguration() {
        return compilerConfiguration;
    }
}
