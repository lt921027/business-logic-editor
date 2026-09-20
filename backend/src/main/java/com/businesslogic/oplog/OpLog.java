package com.businesslogic.oplog;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作日志开关注解。
 *
 * <p>贴在需要记录操作日志的方法上，切面据此决定是否记录。哪些方法要记、埋在哪一行，
 * 完全由开发人员在代码里控制。</p>
 *
 * <p>典型用法：注解只负责"这个方法要记"，具体的一条日志由开发人员在方法内调用
 * {@link OpLogContext#set(String, String, String, String, String)} 登记：</p>
 *
 * <pre>
 * &#64;OpLog(bizType = OpLogConsts.BIZ_SOURCE, operation = OpLogConsts.OP_INSERT)
 * &#64;PostMapping("/publish")
 * public Result&lt;?&gt; publish(RequestBody Map&lt;String, String&gt; body) {
 *     String sourceNo = body.get("sourceNo");
 *     OpLogContext.set(OpLogConsts.BIZ_SOURCE, OpLogConsts.OP_INSERT, sourceNo,
 *             "新增源报文 " + sourceNo, body.get("operator"));
 *     ...
 * }
 * </pre>
 *
 * <p>{@link #bizType()} 与 {@link #operation()} 是可选的兜底声明：方法抛异常、
 * 但开发人员忘记登记时，切面用这两个值记一条"对象ID为空"的兜底日志，保证失败不被静默丢失。
 * 不填则不做兜底记录。</p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OpLog {

    /** 兜底用的对象类型，取值参考 {@link OpLogConsts}，留空表示不做兜底记录。 */
    String bizType() default "";

    /** 兜底用的操作类型，取值参考 {@link OpLogConsts}，留空表示不做兜底记录。 */
    String operation() default "";
}
