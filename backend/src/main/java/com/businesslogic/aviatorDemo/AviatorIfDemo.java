package com.businesslogic.aviatorDemo;

import com.googlecode.aviator.AviatorEvaluator;
import java.util.HashMap;
import java.util.Map;

/**
 * AviatorScript if判断示例
 * 演示使用AviatorScript进行各种if判断操作
 */
public class AviatorIfDemo {

    public static void main(String[] args) {
        System.out.println("=======================================");
        System.out.println("AviatorScript if判断示例");
        System.out.println("=======================================");
        
        //1. 基本if判断（使用三元运算符
        basicIfDemo();
        
        //2. if-else判断（使用三元运算符
        ifElseDemo();
        
        //3. if-else if-else判断（使用嵌套三元运算符
        ifElseIfDemo();
        
        //4. 嵌套if判断（使用嵌套三元运算符
        nestedIfDemo();
        
        // 5. 结合比较运算符的if判断
        comparisonIfDemo();
        
        // 6. 结合逻辑运算符的if判断
        logicalIfDemo();
        
        // 7. 实际业务场景示例
        businessScenarioDemo();
        
        System.out.println("=======================================");
        System.out.println("示例执行完成");
        System.out.println("=======================================");
    }

    /**
     * 基本if判断示例
     */
    private static void basicIfDemo() {
        System.out.println("\n1. 基本if判断示例");
        System.out.println("---------------------------------------");
        
        // 测试数据
        Map<String, Object> env = new HashMap<>();
        
        // 年龄判断
        env.put("age", 20);
        Object ageResult1 = AviatorEvaluator.execute("age >= 18 ? '成年 : '未成年人'", env);
        System.out.println("年龄20: " + ageResult1);
        
        env.put("age", 15);
        Object ageResult2 = AviatorEvaluator.execute("age >= 18 ? '成年 : '未成年人'", env);
        System.out.println("年龄15: " + ageResult2);
        
        // 分数判断
        env.put("score", 85);
        Object scoreResult1 = AviatorEvaluator.execute("score >= 60 ? '及格' : '不及", env);
        System.out.println("分数85: " + scoreResult1);
        
        env.put("score", 45);
        Object scoreResult2 = AviatorEvaluator.execute("score >= 60 ? '及格' : '不及", env);
        System.out.println("分数45: " + scoreResult2);
    }

    /**
     * if-else判断示例
     */
    private static void ifElseDemo() {
        System.out.println("\n2. if-else判断示例");
        System.out.println("---------------------------------------");
        
        // 测试数据
        Map<String, Object> env = new HashMap<>();
        
        // 性别判断
        env.put("gender", "male");
        Object genderResult1 = AviatorEvaluator.execute("gender == 'male' ? '先生' : '女士'", env);
        System.out.println("性别male: " + genderResult1);
        
        env.put("gender", "female");
        Object genderResult2 = AviatorEvaluator.execute("gender == 'male' ? '先生' : '女士'", env);
        System.out.println("性别female: " + genderResult2);
    }

    /**
     * if-else if-else判断示例
     */
    private static void ifElseIfDemo() {
        System.out.println("\n3. if-else if-else判断示例");
        System.out.println("---------------------------------------");
        
        // 测试数据
        Map<String, Object> env = new HashMap<>();
        
        // 分数评级
        env.put("score", 95);
        Object scoreResult1 = AviatorEvaluator.execute("score >= 90 ? '优秀' : score >= 80 ? '良好' : score >= 60 ? '及格' : '不及", env);
        System.out.println("分数95: " + scoreResult1);
        
        env.put("score", 85);
        Object scoreResult2 = AviatorEvaluator.execute("score >= 90 ? '优秀' : score >= 80 ? '良好' : score >= 60 ? '及格' : '不及", env);
        System.out.println("分数85: " + scoreResult2);
        
        env.put("score", 65);
        Object scoreResult3 = AviatorEvaluator.execute("score >= 90 ? '优秀' : score >= 80 ? '良好' : score >= 60 ? '及格' : '不及", env);
        System.out.println("分数65: " + scoreResult3);
        
        env.put("score", 45);
        Object scoreResult4 = AviatorEvaluator.execute("score >= 90 ? '优秀' : score >= 80 ? '良好' : score >= 60 ? '及格' : '不及", env);
        System.out.println("分数45: " + scoreResult4);
    }

    /**
     * 嵌套if判断示例
     */
    private static void nestedIfDemo() {
        System.out.println("\n4. 嵌套if判断示例");
        System.out.println("---------------------------------------");
        
        // 测试数据
        Map<String, Object> env = new HashMap<>();
        
        //学生身份和年级判
        env.put("isStudent", true);
        env.put("grade", 4);
        Object studentResult1 = AviatorEvaluator.execute("isStudent ? (grade >= 3 ? '高年级学 : '低年级学) : '非学", env);
        System.out.println("学生年级: " + studentResult1);
        
        env.put("isStudent", true);
        env.put("grade", 2);
        Object studentResult2 = AviatorEvaluator.execute("isStudent ? (grade >= 3 ? '高年级学 : '低年级学) : '非学", env);
        System.out.println("学生年级: " + studentResult2);
        
        env.put("isStudent", false);
        Object studentResult3 = AviatorEvaluator.execute("isStudent ? (grade >= 3 ? '高年级学 : '低年级学) : '非学", env);
        System.out.println("非学 " + studentResult3);
    }

    /**
     * 结合比较运算符的if判断示例
     */
    private static void comparisonIfDemo() {
        System.out.println("\n5. 结合比较运算符的if判断示例");
        System.out.println("---------------------------------------");
        
        // 测试数据
        Map<String, Object> env = new HashMap<>();
        
        // 薪资水平判断
        env.put("salary", 8000);
        Object salaryResult1 = AviatorEvaluator.execute("salary > 5000 ? '高薪' : salary >= 3000 ? '中等薪资' : '低薪'", env);
        System.out.println("薪资8000: " + salaryResult1);
        
        env.put("salary", 4000);
        Object salaryResult2 = AviatorEvaluator.execute("salary > 5000 ? '高薪' : salary >= 3000 ? '中等薪资' : '低薪'", env);
        System.out.println("薪资4000: " + salaryResult2);
        
        env.put("salary", 2000);
        Object salaryResult3 = AviatorEvaluator.execute("salary > 5000 ? '高薪' : salary >= 3000 ? '中等薪资' : '低薪'", env);
        System.out.println("薪资2000: " + salaryResult3);
        
        // 姓名判断
        env.put("name", "张三");
        Object nameResult1 = AviatorEvaluator.execute("name == '张三' ? '找到了张 : '不是张三'", env);
        System.out.println("姓名张三: " + nameResult1);
        
        env.put("name", "李四");
        Object nameResult2 = AviatorEvaluator.execute("name == '张三' ? '找到了张 : '不是张三'", env);
        System.out.println("姓名李四: " + nameResult2);
    }

    /**
     * 结合逻辑运算符的if判断示例
     */
    private static void logicalIfDemo() {
        System.out.println("\n6. 结合逻辑运算符的if判断示例");
        System.out.println("---------------------------------------");
        
        // 测试数据
        Map<String, Object> env = new HashMap<>();
        
        // 进入权限判断
        env.put("age", 20);
        env.put("hasID", true);
        Object accessResult1 = AviatorEvaluator.execute("(age >= 18 && hasID) ? '可以进入' : '不能进入'", env);
        System.out.println("年龄20，有身份 " + accessResult1);
        
        env.put("age", 20);
        env.put("hasID", false);
        Object accessResult2 = AviatorEvaluator.execute("(age >= 18 && hasID) ? '可以进入' : '不能进入'", env);
        System.out.println("年龄20，无身份 " + accessResult2);
        
        env.put("age", 15);
        env.put("hasID", true);
        Object accessResult3 = AviatorEvaluator.execute("(age >= 18 && hasID) ? '可以进入' : '不能进入'", env);
        System.out.println("年龄15，有身份 " + accessResult3);
        
        // VIP待遇判断
        env.put("isVIP", true);
        env.put("totalSpent", 5000);
        Object vipResult1 = AviatorEvaluator.execute("(isVIP || totalSpent >= 10000) ? '享受VIP待遇' : '普通客", env);
        System.out.println("是VIP，消000: " + vipResult1);
        
        env.put("isVIP", false);
        env.put("totalSpent", 15000);
        Object vipResult2 = AviatorEvaluator.execute("(isVIP || totalSpent >= 10000) ? '享受VIP待遇' : '普通客", env);
        System.out.println("不是VIP，消5000: " + vipResult2);
        
        env.put("isVIP", false);
        env.put("totalSpent", 5000);
        Object vipResult3 = AviatorEvaluator.execute("(isVIP || totalSpent >= 10000) ? '享受VIP待遇' : '普通客", env);
        System.out.println("不是VIP，消000: " + vipResult3);
    }

    /**
     * 实际业务场景示例
     */
    private static void businessScenarioDemo() {
        System.out.println("\n7. 实际业务场景示例");
        System.out.println("---------------------------------------");
        
        // 测试数据
        Map<String, Object> env = new HashMap<>();
        
        // 优惠规则判断
        env.put("userLevel", "VIP");
        env.put("orderAmount", 1500);
        Object discountResult1 = AviatorEvaluator.execute("userLevel == 'VIP' ? (orderAmount >= 1000 ? 'VIP优惠 + 满减优惠' : 'VIP优惠') : (orderAmount >= 1000 ? '满减优惠' : '无优)", env);
        System.out.println("VIP用户，订单金500: " + discountResult1);
        
        env.put("userLevel", "VIP");
        env.put("orderAmount", 500);
        Object discountResult2 = AviatorEvaluator.execute("userLevel == 'VIP' ? (orderAmount >= 1000 ? 'VIP优惠 + 满减优惠' : 'VIP优惠') : (orderAmount >= 1000 ? '满减优惠' : '无优)", env);
        System.out.println("VIP用户，订单金00: " + discountResult2);
        
        env.put("userLevel", "普通");
        env.put("orderAmount", 1500);
        Object discountResult3 = AviatorEvaluator.execute("userLevel == 'VIP' ? (orderAmount >= 1000 ? 'VIP优惠 + 满减优惠' : 'VIP优惠') : (orderAmount >= 1000 ? '满减优惠' : '无优)", env);
        System.out.println("普通用户，订单金额1500: " + discountResult3);
        
        env.put("userLevel", "普通");
        env.put("orderAmount", 500);
        Object discountResult4 = AviatorEvaluator.execute("userLevel == 'VIP' ? (orderAmount >= 1000 ? 'VIP优惠 + 满减优惠' : 'VIP优惠') : (orderAmount >= 1000 ? '满减优惠' : '无优)", env);
        System.out.println("普通用户，订单金额500: " + discountResult4);
    }
}
