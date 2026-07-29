package com.businesslogic.groovy.util;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

/**
 * Groovy 脚本日期工具类
 *
 * <p>提供 Aviator 内置 date.* 函数的等价实现，
 * 供 Groovy 脚本通过 GroovyDateFunctions.xxx() 调用。
 *
 * <p>对应 Aviator 的 date.diff_months、date.before、date.after 等内置函数。
 *
 * <p>关联体系：
 * <ul>
 *   <li>被 {@link com.businesslogic.groovy.security.GroovySandbox} 加入 IMPORTS_WHITELIST 与 ImportCustomizer 预导入，
 *       允许业务脚本直接 `import` 或无 import 使用</li>
 *   <li>被 {@link com.businesslogic.groovy.engine.GroovyExpressionEngine#execute} 以 Class 形式注入到 Binding，
 *       脚本中可写 `GroovyDateFunctions.diffMonths(a, b)` 调用</li>
 *   <li>被 {@link com.businesslogic.groovy.generator.GroovyExpressionGenerator#generateDateFunction} 生成的脚本代码引用</li>
 * </ul>
 *
 * <p>线程安全：所有公共方法均为静态方法、无共享可变状态，可被多线程并发调用。
 * 注意 {@link SimpleDateFormat} 非线程安全，因此每次调用都新建实例（而非共享静态字段）。
 */
public class GroovyDateFunctions {

    /** 默认日期格式（与 Aviator DateFormatUtil 对齐） */
    private static final String DEFAULT_FORMAT = "yyyy-MM-dd";

    /**
     * 计算两个日期之间的月数差（按自然月，非 30 天近似）。
     *
     * <p>对应 Aviator: date.diff_months(a, b) → b - a 的月数。
     *
     * <p>为何基于 Calendar 字段相减而非用 Duration：业务定义"月数差"指自然月跨度
     * （如 2024-01-31 → 2024-02-01 算 1 个月），用 Calendar.MONTH 差值最贴合业务语义。
     *
     * <p>关联：被 {@link com.businesslogic.groovy.generator.GroovyExpressionGenerator} 中
     * `months_between` 函数生成的脚本调用。
     */
    public static int diffMonths(Object date1, Object date2) {
        Date d1 = toDate(date1);
        Date d2 = toDate(date2);
        if (d1 == null || d2 == null) {
            return 0;
        }
        Calendar cal1 = Calendar.getInstance();
        cal1.setTime(d1);
        Calendar cal2 = Calendar.getInstance();
        cal2.setTime(d2);
        int yearDiff = cal2.get(Calendar.YEAR) - cal1.get(Calendar.YEAR);
        int monthDiff = cal2.get(Calendar.MONTH) - cal1.get(Calendar.MONTH);
        return yearDiff * 12 + monthDiff;
    }

    /**
     * 计算两个日期之间的天数差（24 小时为一个整天的截断除法）。
     *
     * <p>对应 Aviator: date.diff_days(a, b) → b - a 的天数。
     *
     * <p>关联：被 {@link com.businesslogic.groovy.generator.GroovyExpressionGenerator} 中
     * `days_between` 函数生成的脚本调用。
     */
    public static int diffDays(Object date1, Object date2) {
        Date d1 = toDate(date1);
        Date d2 = toDate(date2);
        if (d1 == null || d2 == null) {
            return 0;
        }
        long diffMs = d2.getTime() - d1.getTime();
        return (int) (diffMs / (1000 * 60 * 60 * 24));
    }

    /**
     * 计算两个日期之间的年数差（仅按 Calendar.YEAR 字段相减，不按 365 天近似）。
     *
     * <p>对应 Aviator: date.diff_years(a, b) → b - a 的年数。
     *
     * <p>关联：被 {@link com.businesslogic.groovy.generator.GroovyExpressionGenerator} 中
     * `years_between` 函数生成的脚本调用。
     */
    public static int diffYears(Object date1, Object date2) {
        Date d1 = toDate(date1);
        Date d2 = toDate(date2);
        if (d1 == null || d2 == null) {
            return 0;
        }
        Calendar cal1 = Calendar.getInstance();
        cal1.setTime(d1);
        Calendar cal2 = Calendar.getInstance();
        cal2.setTime(d2);
        return cal2.get(Calendar.YEAR) - cal1.get(Calendar.YEAR);
    }

    /**
     * 判断 date1 是否严格在 date2 之前。
     *
     * <p>对应 Aviator: date.before(a, b)。
     *
     * <p>为何 null 返回 false：业务场景中 null 通常表示"未知"，不能断言先后关系，
     * 返回 false 让上层 if 分支统一走"不满足条件"路径，避免 NPE。
     */
    public static boolean before(Object date1, Object date2) {
        Date d1 = toDate(date1);
        Date d2 = toDate(date2);
        if (d1 == null || d2 == null) {
            return false;
        }
        return d1.before(d2);
    }

    /**
     * 判断 date1 是否严格在 date2 之后。
     *
     * <p>对应 Aviator: date.after(a, b)。
     *
     * <p>null 行为与 {@link #before} 一致，返回 false。
     */
    public static boolean after(Object date1, Object date2) {
        Date d1 = toDate(date1);
        Date d2 = toDate(date2);
        if (d1 == null || d2 == null) {
            return false;
        }
        return d1.after(d2);
    }

    /**
     * 判断两个日期是否相等（精确到毫秒）。
     *
     * <p>对应 Aviator: date.equal(a, b)。
     *
     * <p>为何 null == null 返回 true：与 Java Objects.equals 语义一致，
     * 简化业务脚本中"两值都缺省时视为相等"的判断。
     */
    public static boolean equal(Object date1, Object date2) {
        Date d1 = toDate(date1);
        Date d2 = toDate(date2);
        if (d1 == null && d2 == null) {
            return true;
        }
        if (d1 == null || d2 == null) {
            return false;
        }
        return d1.equals(d2);
    }

    /**
     * 判断日期是否在最近 N 个月内（以当前系统时间为基准）。
     *
     * <p>为何以系统时间而非外部传入基准：业务规则"近 N 个月"通常指相对于"今天"，
     * 与 currentDate 内置变量含义一致；如需自定义基准可在脚本中组合 before/after。
     *
     * <p>关联：被 {@link #withinLast3Months} / {@link #withinLast6Months} /
     * {@link #withinLast9Months} / {@link #withinLast12Months} 包装调用；
     * 被 {@link com.businesslogic.groovy.generator.GroovyExpressionGenerator} 的
     * `withinLast3Months` 等函数生成代码调用。
     */
    public static boolean withinLastMonths(Object date, int months) {
        Date d = toDate(date);
        if (d == null) {
            return false;
        }
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.MONTH, -months);
        Date threshold = cal.getTime();
        return d.after(threshold);
    }

    /** 判断日期是否在最近 3 个月内。关联：委托 {@link #withinLastMonths}。 */
    public static boolean withinLast3Months(Object date) {
        return withinLastMonths(date, 3);
    }

    /** 判断日期是否在最近 6 个月内。关联：委托 {@link #withinLastMonths}。 */
    public static boolean withinLast6Months(Object date) {
        return withinLastMonths(date, 6);
    }

    /** 判断日期是否在最近 9 个月内。关联：委托 {@link #withinLastMonths}。 */
    public static boolean withinLast9Months(Object date) {
        return withinLastMonths(date, 9);
    }

    /** 判断日期是否在最近 12 个月内。关联：委托 {@link #withinLastMonths}。 */
    public static boolean withinLast12Months(Object date) {
        return withinLastMonths(date, 12);
    }

    /**
     * 格式化日期为默认格式字符串（yyyy-MM-dd）。
     *
     * <p>对应 Aviator: DateFormatUtil.format(date)。
     *
     * <p>关联：委托 {@link #format(Object, String)}，传 DEFAULT_FORMAT。
     */
    public static String format(Object date) {
        return format(date, DEFAULT_FORMAT);
    }

    /**
     * 按指定 pattern 格式化日期。
     *
     * <p>为何每次新建 SimpleDateFormat：SimpleDateFormat 非线程安全，
     * 不能作为静态字段共享。每次新建虽然略有开销，但日期格式化通常不在热路径上，可接受。
     *
     * @param date    日期值（Date/Long/String 均可）
     * @param pattern SimpleDateFormat 格式，null 时回退到 DEFAULT_FORMAT
     */
    public static String format(Object date, String pattern) {
        Date d = toDate(date);
        if (d == null) {
            return null;
        }
        return new SimpleDateFormat(pattern != null ? pattern : DEFAULT_FORMAT).format(d);
    }

    /**
     * 将各种类型的日期值统一转为 Date 对象。
     *
     * <p>为何接受 Object 类型：业务脚本传入的日期值类型不可控——
     * 来自 JSON 反序列化的可能是 String，来自数据库的可能是 java.sql.Date，
     * 来自外部 API 的可能是 Long 时间戳。统一在入口处转换，简化上层方法签名。
     *
     * <p>支持的类型与尝试顺序：
     * <ul>
     *   <li>Date 及其子类（java.sql.Date 等）— 直接返回</li>
     *   <li>Number — 视为毫秒时间戳</li>
     *   <li>String — 依次尝试 yyyy-MM-dd / yyyy-MM-dd HH:mm:ss / yyyy/MM/dd / yyyyMMdd</li>
     * </ul>
     *
     * <p>关联：被本类所有公共方法调用，是日期处理的统一入口。
     *
     * @return 转换后的 Date；null 输入或解析失败返回 null
     */
    private static Date toDate(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Date) {
            return (Date) value;
        }
        if (value instanceof Number) {
            return new Date(((Number) value).longValue());
        }
        if (value instanceof String) {
            String str = ((String) value).trim();
            String[] patterns = {DEFAULT_FORMAT, "yyyy-MM-dd HH:mm:ss", "yyyy/MM/dd", "yyyyMMdd"};
            for (String pattern : patterns) {
                try {
                    return new SimpleDateFormat(pattern).parse(str);
                } catch (ParseException ignored) {
                    // 尝试下一个格式
                }
            }
        }
        return null;
    }
}
