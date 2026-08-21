package com.businesslogic.groovy.perf;

import groovy.lang.Binding;
import groovy.lang.GroovyClassLoader;
import groovy.lang.GroovyShell;
import groovy.lang.Script;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * 直接绕过 GroovyExpressionEngine，用 GroovyShell / GroovyClassLoader
 * 尝试执行一个超过 65536 字符的脚本，观察具体失败原因。
 */
public class LongGroovyScriptDirectTest {

    @Test
    public void tryDirectExecution() throws Exception {
        String source = readResource("groovy/benchmarks/script_12_long_over_65536.groovy");
        System.out.println("脚本字符长度: " + source.length());

        System.out.println("==== 方式 A: GroovyShell ====");
        tryGroovyShell(source);

        System.out.println("==== 方式 B: GroovyClassLoader ====");
        tryGroovyClassLoader(source);
    }

    private void tryGroovyShell(String source) {
        try {
            GroovyShell shell = new GroovyShell();
            Object result = shell.evaluate(source);
            System.out.println("GroovyShell 成功, result=" + result);
        } catch (Throwable t) {
            System.out.println("GroovyShell 失败: " + t.getClass().getName() + ": " + t.getMessage());
        }
    }

    private void tryGroovyClassLoader(String source) {
        try {
            GroovyClassLoader loader = new GroovyClassLoader();
            Class<?> clazz = loader.parseClass(source, "LongScript");
            Script script = (Script) clazz.getDeclaredConstructor().newInstance();
            script.setBinding(new Binding());
            Object result = script.run();
            System.out.println("GroovyClassLoader 成功, result=" + result);
        } catch (Throwable t) {
            System.out.println("GroovyClassLoader 失败: " + t.getClass().getName() + ": " + t.getMessage());
        }
    }

    private String readResource(String path) throws Exception {
        InputStream input = getClass().getClassLoader().getResourceAsStream(path);
        if (input == null) {
            throw new IllegalStateException("找不到资源: " + path);
        }
        try (BufferedReader reader =
                     new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append('\n');
            }
            return builder.toString();
        }
    }
}
