package com.businesslogic.groovy.perf;

import groovy.lang.GroovyClassLoader;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 对比“编译后保留 GroovyClassLoader.classCache”和“编译后立刻清理 classCache”
 * 两种策略下 Metaspace 的回收效果。
 *
 * <p>测试会生成一批彼此不同的 Groovy 脚本，分别用两个独立的 GroovyClassLoader 编译。
 * 第一个 loader 不做任何清理，模拟当前 classCache 无限增长；第二个 loader 在每次
 * 编译完成后删除本次编译产生的 classCache 条目，并调用 {@link InvokerHelper#removeClass}
 * 清理 Groovy 的 MetaClass/ClassInfo 注册。之后触发 GC，比较两者的 Metaspace 保留量。
 */
public class GroovyClassCacheMetaspaceRecycleTest {

    private static final int SCRIPT_COUNT = 140;
    private static final int STATEMENTS_PER_SCRIPT = 160;
    private static final int GC_ROUNDS = 8;
    private static final long GC_PAUSE_MS = 120L;

    private static class CleanableGroovyClassLoader extends GroovyClassLoader {

        CleanableGroovyClassLoader(ClassLoader parent) {
            super(parent);
        }

        int classCacheSize() {
            return getLoadedClasses().length;
        }

        /**
         * 清理本次编译产生的全部类。
         *
         * <p>一次 parseClass 可能生成主脚本类、闭包类、辅助类等多个 Class，它们由同一个
         * InnerLoader 定义。这里以 InnerLoader 身份作为过滤条件，避免误删其他并发编译产生的类。
         */
        void cleanupCompilation(Class<?> scriptClass) {
            ClassLoader compileLoader = scriptClass.getClassLoader();
            for (Class<?> loaded : getLoadedClasses()) {
                if (loaded.getClassLoader() == compileLoader) {
                    removeClassCacheEntry(loaded.getName());
                    InvokerHelper.removeClass(loaded);
                }
            }
        }
    }

    @Test
    public void compareMetaspaceWithAndWithoutClassCacheCleanup() throws Exception {
        warmupGroovy();

        long retainedWithoutCleanup = runScenario(false);
        long retainedWithCleanup = runScenario(true);

        printReport(retainedWithoutCleanup, retainedWithCleanup);

        long minExpectedDifference = 512L * 1024L;
        assertTrue(
                retainedWithoutCleanup - retainedWithCleanup > minExpectedDifference,
                String.format(
                        Locale.ROOT,
                        "期望清理 classCache 后至少减少 %.0f KB Metaspace 保留量，实际未清理=%d bytes，清理后=%d bytes",
                        minExpectedDifference / 1024.0,
                        retainedWithoutCleanup,
                        retainedWithCleanup));
    }

    private void warmupGroovy() throws Exception {
        CleanableGroovyClassLoader warmupLoader =
                new CleanableGroovyClassLoader(getClass().getClassLoader());
        for (int i = 0; i < 16; i++) {
            String source = buildScript(1_000_000 + i, 40);
            Class<?> scriptClass = warmupLoader.parseClass(source, scriptName(source));
            warmupLoader.cleanupCompilation(scriptClass);
        }
        warmupLoader = null;
        forceGc();
    }

    private long runScenario(boolean cleanupAfterCompile) throws Exception {
        CleanableGroovyClassLoader loader =
                new CleanableGroovyClassLoader(getClass().getClassLoader());

        long baseline = measureMetaspaceUsed();

        compileMany(loader, cleanupAfterCompile);

        long after = measureMetaspaceUsed();
        return after - baseline;
    }

    private void compileMany(CleanableGroovyClassLoader loader, boolean cleanupAfterCompile)
            throws Exception {
        for (int i = 0; i < SCRIPT_COUNT; i++) {
            String source = buildScript(i, STATEMENTS_PER_SCRIPT);
            Class<?> scriptClass = loader.parseClass(source, scriptName(source));
            if (cleanupAfterCompile) {
                loader.cleanupCompilation(scriptClass);
            }
        }
    }

    private String buildScript(int index, int statementCount) {
        StringBuilder source = new StringBuilder();
        source.append("// generated script ").append(index).append('\n');
        source.append("def value").append(index).append(" = ").append(index).append('\n');

        for (int i = 0; i < statementCount; i++) {
            source.append("def value").append(index).append('_').append(i)
                    .append(" = \"payload_").append(index).append('_').append(i)
                    .append("\"\n");
            source.append("value").append(index).append(" = value").append(index)
                    .append(" + ").append(i).append('\n');
        }

        source.append("return value").append(index).append('\n');
        return source.toString();
    }

    private String scriptName(String source) throws Exception {
        return "MetaScript_" + md5(source) + ".groovy";
    }

    private String md5(String input) throws Exception {
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) {
            sb.append(String.format(Locale.ROOT, "%02x", b));
        }
        return sb.toString();
    }

    private long measureMetaspaceUsed() {
        forceGc();
        long total = 0L;
        for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
            String name = pool.getName();
            if ("Metaspace".equals(name) || "Compressed Class Space".equals(name)) {
                total += pool.getUsage().getUsed();
            }
        }
        return total;
    }

    private void forceGc() {
        for (int i = 0; i < GC_ROUNDS; i++) {
            System.gc();
            try {
                Thread.sleep(GC_PAUSE_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void printReport(long withoutCleanup, long withCleanup) {
        System.out.println("==== classCache 清理对 Metaspace 回收的影响 ====");
        System.out.printf(Locale.ROOT, "脚本数量: %d%n", SCRIPT_COUNT);
        System.out.printf(Locale.ROOT, "未清理 classCache 保留量: %.2f KB%n", withoutCleanup / 1024.0);
        System.out.printf(Locale.ROOT, "清理 classCache 保留量:   %.2f KB%n", withCleanup / 1024.0);
        System.out.printf(Locale.ROOT, "差异: %.2f KB%n", (withoutCleanup - withCleanup) / 1024.0);
    }

    public static void main(String[] args) throws Exception {
        new GroovyClassCacheMetaspaceRecycleTest()
                .compareMetaspaceWithAndWithoutClassCacheCleanup();
    }
}
