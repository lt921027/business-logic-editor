package com.businesslogic.groovy.security;

import com.businesslogic.groovy.engine.GroovyExpressionEngine;
import org.codehaus.groovy.ast.stmt.Statement;
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
     * 与白名单形成"双保险"——白名单管 import，黑名单管直接调用。a
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
     * <p>为何使用空列表：SecureASTCustomizer 的 import 白名单 + indirectImportCheck 已经能在 AST 层拦截
     * 非法 import 语句，无需再通过语句黑名单重复限制。Groovy 3.0.9 中 SecureASTCustomizer 的
     * setStatementsBlacklist 签名要求 {@code List<Class<? extends Statement>>}（不是 List<String>），
     * 因此使用 {@link Collections#emptyList()} 占位以满足类型约束。
     *
     * <p>关联：被 {@link #createSecureCompilerConfiguration()} 通过
     * {@link SecureASTCustomizer#setStatementsBlacklist(List)} 调用。
     */
    private static final List<Class<? extends Statement>> STATEMENTS_BLACKLIST = Collections.emptyList();

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

        // 黑名单接收者：禁止直接调用这些类的方法，详见 RECEIVERS_BLACKLIST 注释
        secureCustomizer.setReceiversBlackList(RECEIVERS_BLACKLIST);

        // 语句黑名单（空列表：import 已由 importsWhitelist + indirectImportCheck 拦截）
        secureCustomizer.setStatementsBlacklist(STATEMENTS_BLACKLIST);

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

        config.addCompilationCustomizers(secureCustomizer, importCustomizer);

        return config;
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
