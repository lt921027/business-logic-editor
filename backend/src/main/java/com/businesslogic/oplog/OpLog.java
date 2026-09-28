package com.businesslogic.oplog;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 特征操作日志开关注解。
 *
 * <p>贴在需要记录操作日志的 Controller 方法上，切面据此决定是否拦截并记录。
 * 记录内容（修改前后的表达式与步骤）由切面自行查库获得，业务代码不需要做任何登记。</p>
 *
 * <p>三种典型用法：</p>
 *
 * <pre>
 * // 新增与修改合并的接口：入参带特征ID 记为 UPDATE，不带记为 INSERT
 * &#64;OpLog(featureIdProperty = "id")
 *
 * // 特征ID 是方法参数（如 &#64;PathVariable Long id 在第 0 位）
 * &#64;OpLog(featureIdArg = 0)
 *
 * // 删除接口：操作类型显式声明；第 0 个参数是集合时自动按批量处理
 * &#64;OpLog(operation = OpLogConsts.OP_DELETE, featureIdArg = 0)
 * </pre>
 *
 * <p><b>批量：</b>当取到的特征ID 是集合（如 {@code List<Long>}）时，切面会为每个ID
 * 单独查一次快照并单独落一行日志，一次请求产生多行。批量删除中途失败时，
 * 已经删除成功的条目记为成功，其余记为失败并带上异常原因。</p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OpLog {

    /**
     * 操作类型，取值见 {@link OpLogConsts}。
     *
     * <p>留空表示由切面自动判定：入参能取到特征ID 记为 UPDATE，取不到记为 INSERT。
     * 新增与修改合并成一个接口时必须留空。</p>
     */
    String operation() default "";

    /**
     * 特征ID 所在的参数下标，从 0 开始；-1 表示入参里没有。
     *
     * <p>该参数既可以是单个ID，也可以是ID集合，切面按类型自动进入单条或批量模式。</p>
     */
    int featureIdArg() default -1;

    /**
     * 特征ID 所在的属性名。
     *
     * <p>两种场景：一是 {@link #featureIdArg()} 指定的参数是对象（如 DTO），
     * 从该对象里取这个属性；二是 {@link #featureIdArg()} 未指定，则逐个参数查找
     * 具有该属性的对象。属性值同样支持单个ID 或ID集合。</p>
     */
    String featureIdProperty() default "";
}
