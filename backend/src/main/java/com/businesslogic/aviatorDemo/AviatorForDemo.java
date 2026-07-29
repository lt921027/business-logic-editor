package com.businesslogic.aviatorDemo;

import com.googlecode.aviator.AviatorEvaluator;
import java.util.HashMap;
import java.util.Map;

/**
 * AviatorScript for循环示例
 * 演示使用AviatorScript的各种循环操
 */
public class AviatorForDemo {

    public static void main(String[] args) {
        System.out.println("=======================================");
        System.out.println("AviatorScript for循环示例");
        System.out.println("=======================================");
        
        mapLoopDemo();
        /*listLoopDemo();
        arrayLoopDemo();
        rangeLoopDemo();
        conditionalLoopDemo();
        sumLoopDemo();
        filterListDemo();
        filterObjectListDemo();*/
        
        System.out.println("=======================================");
        System.out.println("示例执行完成");
        System.out.println("=======================================");
    }

    private static void mapLoopDemo() {
        System.out.println("\n1. Map遍历示例");
        System.out.println("---------------------------------------");
        
        Map<String, Object> env = new HashMap<>();
        Map<String, Object> map = new HashMap<>();
        map.put("a", 1);
        map.put("b", 2);
        map.put("c", 3);
        env.put("m", map);
        
        // 使用Aviator的for循环遍历Map
        String expression = "let result = ''; for x in m { result = result + x.key + '=' + x.value + ', '; } return result;";
        Object result = AviatorEvaluator.execute(expression, env);
        System.out.println("Map遍历结果: " + result);
    }

    private static void listLoopDemo() {
        System.out.println("\n2. List遍历示例");
        System.out.println("---------------------------------------");
        
        Map<String, Object> env = new HashMap<>();
        env.put("list", java.util.Arrays.asList(1, 2, 3, 4, 5));
        
        // 使用Aviator的for循环遍历List并求
        String expression = "let sum = 0; for x in list { sum = sum + x; } return sum;";
        Object result = AviatorEvaluator.execute(expression, env);
        System.out.println("List元素之和: " + result);
    }

    private static void arrayLoopDemo() {
        System.out.println("\n3. 数组遍历示例");
        System.out.println("---------------------------------------");
        
        Map<String, Object> env = new HashMap<>();
        int[] numbers = {10, 20, 30, 40, 50};
        env.put("numbers", numbers);
        
        // 使用Aviator的for循环遍历数组
        String expression = "let result = ''; for x in numbers { result = result + x + ', '; } return result;";
        Object result = AviatorEvaluator.execute(expression, env);
        System.out.println("数组元素: " + result);
    }

    private static void rangeLoopDemo() {
        System.out.println("\n4. 范围循环示例");
        System.out.println("---------------------------------------");
        
        Map<String, Object> env = new HashMap<>();
        env.put("start", 1);
        env.put("max", 10);
        
        // 使用简单的循环方式
        String expression = "let sum = 0; let i = start; while (i < max) { sum = sum + i; i = i + 1; } return sum;";
        Object result = AviatorEvaluator.execute(expression, env);
        System.out.println("1的和: " + result);
    }

    private static void conditionalLoopDemo() {
        System.out.println("\n5. 条件循环示例");
        System.out.println("---------------------------------------");
        
        Map<String, Object> env = new HashMap<>();
        env.put("list", java.util.Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10));
        
        // 使用Aviator的for循环和条件判
        String expression = "let evenSum = 0; for x in list { if (x % 2 == 0) { evenSum = evenSum + x; } } return evenSum;";
        Object result = AviatorEvaluator.execute(expression, env);
        System.out.println("10的偶数和: " + result);
    }

    private static void sumLoopDemo() {
        System.out.println("\n6. 循环求和示例");
        System.out.println("---------------------------------------");
        
        Map<String, Object> env = new HashMap<>();
        env.put("limit", 100);
        
        // 使用while循环计算100的和
        String expression = "let sum = 0; let i = 1; while (i <= limit) { sum = sum + i; i = i + 1; } return sum;";
        Object result = AviatorEvaluator.execute(expression, env);
        System.out.println("100的和: " + result);
        
        // 计算100的奇数和
        String expression2 = "let oddSum = 0; let i = 1; while (i <= limit) { if (i % 2 != 0) { oddSum = oddSum + i; } i = i + 1; } return oddSum;";
        Object result2 = AviatorEvaluator.execute(expression2, env);
        System.out.println("100的奇数和: " + result2);
    }

    private static void filterListDemo() {
        System.out.println("\n7. List筛过滤示例");
        System.out.println("---------------------------------------");
        
        Map<String, Object> env = new HashMap<>();
        env.put("list", java.util.Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10));
        
        // 筛选出大于5的元
        String expression = "let result = []; for x in list { if (x > 5) { result.add(x); } } return result;";
        Object result = AviatorEvaluator.execute(expression, env);
        System.out.println("大于5的元 " + result);
        
        // 筛选出偶数元素
        String expression2 = "let evens = []; for x in list { if (x % 2 == 0) { evens.add(x); } } return evens;";
        Object result2 = AviatorEvaluator.execute(expression2, env);
        System.out.println("偶数元素: " + result2);
        
        // 筛选出在指定范围内的元素（3之间
        String expression3 = "let filtered = []; let min = 3; let max = 8; for x in list { if (x >= min && x <= max) { filtered.add(x); } } return filtered;";
        Object result3 = AviatorEvaluator.execute(expression3, env);
        System.out.println("3之间的元 " + result3);
        
        Map<String, Object> env2 = new HashMap<>();
        env2.put("scores", java.util.Arrays.asList(85, 92, 78, 95, 60, 88, 73, 96));
        
        // 筛选出成绩大于等于80
        String expression4 = "let passed = []; for score in scores { if (score >= 80) { passed.add(score); } } return passed;";
        Object result4 = AviatorEvaluator.execute(expression4, env2);
        System.out.println("成绩>=80 " + result4);
    }

    private static void filterObjectListDemo() {
        System.out.println("\n8. 对象列表筛过滤示例");
        System.out.println("---------------------------------------");
        
        Map<String, Object> env = new HashMap<>();
        
        // 使用Map模拟对象列表（每个元素是一个包含多个字段的对象
        java.util.List<Map<String, Object>> students = new java.util.ArrayList<>();
        
        Map<String, Object> s1 = new HashMap<>();
        s1.put("name", "张三");
        s1.put("age", 20);
        s1.put("score", 85);
        s1.put("gender", "男");
        students.add(s1);
        
        Map<String, Object> s2 = new HashMap<>();
        s2.put("name", "李四");
        s2.put("age", 22);
        s2.put("score", 92);
        s2.put("gender", "女");
        students.add(s2);
        
        Map<String, Object> s3 = new HashMap<>();
        s3.put("name", "王五");
        s3.put("age", 19);
        s3.put("score", 78);
        s3.put("gender", "男");
        students.add(s3);
        
        Map<String, Object> s4 = new HashMap<>();
        s4.put("name", "赵六");
        s4.put("age", 21);
        s4.put("score", 95);
        s4.put("gender", "男");
        students.add(s4);
        
        Map<String, Object> s5 = new HashMap<>();
        s5.put("name", "钱七");
        s5.put("age", 23);
        s5.put("score", 60);
        s5.put("gender", "女");
        students.add(s5);
        
        env.put("students", students);
        
        // 筛选成绩大于等0的学
        String expression = "let result = []; for s in students { if (s.score >= 80) { result.add(s); } } return result;";
        Object result = AviatorEvaluator.execute(expression, env);
        System.out.println("成绩>=80的学 " + result);
        
        // 筛选年龄小1的学
        String expression2 = "let youngStudents = []; for s in students { if (s.age < 21) { youngStudents.add(s.name); } } return youngStudents;";
        Object result2 = AviatorEvaluator.execute(expression2, env);
        System.out.println("年龄<21的学生姓 " + result2);
        
        // 筛选女生且成绩大于85
        String expression3 = "let topFemaleStudents = []; for s in students { if (s.gender == ' && s.score > 85) { topFemaleStudents.add(s.name + ':' + s.score); } } return topFemaleStudents;";
        Object result3 = AviatorEvaluator.execute(expression3, env);
        System.out.println("女生且成85姓名:成绩): " + result3);
        
        // 提取所有学生的姓名和成
        String expression4 = "let infoList = []; for s in students { infoList.add(s.name + '-' + s.score + '); } return infoList;";
        Object result4 = AviatorEvaluator.execute(expression4, env);
        System.out.println("所有学生信 " + result4);
        
        Map<String, Object> env2 = new HashMap<>();
        java.util.List<Map<String, Object>> products = new java.util.ArrayList<>();
        
        Map<String, Object> p1 = new HashMap<>();
        p1.put("name", "手机");
        p1.put("price", 2999);
        p1.put("category", "电子产品");
        p1.put("stock", 100);
        products.add(p1);
        
        Map<String, Object> p2 = new HashMap<>();
        p2.put("name", "电脑");
        p2.put("price", 5999);
        p2.put("category", "电子产品");
        p2.put("stock", 50);
        products.add(p2);
        
        Map<String, Object> p3 = new HashMap<>();
        p3.put("name", "书籍");
        p3.put("price", 59);
        p3.put("category", "图书");
        p3.put("stock", 200);
        products.add(p3);
        
        Map<String, Object> p4 = new HashMap<>();
        p4.put("name", "耳机");
        p4.put("price", 399);
        p4.put("category", "电子产品");
        p4.put("stock", 0);
        products.add(p4);
        
        env2.put("products", products);
        
        // 筛选价格在100-5000之间且有库存的商
        String expression5 = "let filteredProducts = []; for p in products { if (p.price >= 100 && p.price <= 5000 && p.stock > 0) { filteredProducts.add(p.name + ':¥' + p.price); } } return filteredProducts;";
        Object result5 = AviatorEvaluator.execute(expression5, env2);
        System.out.println("价格100-5000且有库存的商 " + result5);
        
        // 按分类筛
        String expression6 = "let electronics = []; for p in products { if (p.category == '电子产品') { electronics.add(p.name); } } return electronics;";
        Object result6 = AviatorEvaluator.execute(expression6, env2);
        System.out.println("电子产品类商 " + result6);
    }
}
