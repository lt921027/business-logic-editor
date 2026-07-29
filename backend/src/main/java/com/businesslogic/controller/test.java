package com.businesslogic.controller;

import com.businesslogic.executor.AviatorExecutor;
import com.businesslogic.util.JsonPathUtil;
import com.businesslogic.util.StringUtil;

import java.util.HashMap;
import java.util.Map;

public class test {

    public static void main(String[] args) {
        String inputData = "{\n" +
                "            \"PH010R01\": [\n" +
                "                {\n" +
                "                    \"PH010RA1\": 11,\n" +
                "                    \"PH010RB1\": \"abc\"\n" +
                "                },\n" +
                "                {\n" +
                "                    \"PH010RA1\": 112,\n" +
                "                    \"PH010RB1\": \"abc\"\n" +
                "                },\n" +
                "                {\n" +
                "                    \"PH010RA1\": 113,\n" +
                "                    \"PH010RB1\": \"abc\"\n" +
                "                }\n" +
                "            ],\n" +
                "            \"PH010R02\": \"abc\",\n" +
                "            \"PH010R03\": \"abc\"\n" +
                "        }";


/*        String expression = "let step1true = seq.list();let step1false = seq.list();" +
                "for item in JsonPathUtil.read(inputData, '$.PH010R01') {" +
                "  if (StringUtil.equals(item['PH010RA1'],'abc')) {" +
                "  seq.step1true(step1List, item);" +
                "  }else{seq.step1false(step1List, item);}" +
                "}" +
                "return count(step1true);";
            //循环校验，判断某个字段的值是否与abc相等*/




       /* String expression = "let step1false = seq.list();\n" +
                "for item in JsonPathUtil.read(inputData, '$.PH010R01') {\n" +
                "  if (!(((StringUtil.equals(item['PH010RA1'], item['PH010RB1']))) && (StringUtil.equals(item['PH010RA1'], 'abc')))) {\n" +
                "    seq.add(step1false, item);\n" +
                "  }\n" +
                "}\n"
                //+"distinct(seq.map(step1false, lambda(x) -> JsonPathUtil.read(inputData, '$.PH010R01[0].PH010RA1') end))\n";
        +"distinct(step1false)\n";*/

/*        String expression = "let step1true = seq.list();\n" +
                "for item in JsonPathUtil.read(inputData, '$.PH010R01') {\n" +
                "  if ((StringUtil.equals(item['PH010RA1'], 'aaa')) && ((StringUtil.equals(item['PH010RA1'], item['PH010RB1'])))) {\n" +
                "    seq.add(step1true, item);\n" +
                "  }\n" +
                "}\n" +
                "let step1 = 0; " +
                "step1 = count(step1true);"
                + "let step2 = JsonPathUtil.read(inputData, '$.PH010R01[0].PH010RA1');\n";*/


/*        String expression = "let step1true = seq.list();\n" +
                "for item in JsonPathUtil.read(inputData, '$.PH010R01') {\n" +
                "  if ((StringUtil.equals(item['PH010RB1'], 'abc')) || ((StringUtil.equals(item['PH010RA1'], item['PH010RB1'])))) {\n" +
                "    seq.add(step1true, item);\n" +
                "  }\n" +
                "}\n" +
                "let step1 = 0;\n" +
                "let temp_step1_1 = distinct(step1true);\n"
                //+"let temp_step1_0 = count(temp_step1_1);\n";
                +"step1 = reduce(temp_step1_1, 0, lambda(x, y) -> x + java.lang.Long.parseLong(string(y['PH010RA1'])) end);\n";*/


        String expression = "let step1true = JsonPathUtil.read(inputData, '$.PH010R01');\n" +
            "for item in JsonPathUtil.read(inputData, '$.PH010R01') {\n" +
            "  if ((StringUtil.equals(item['PH010RB1'], 'abc')) || ((StringUtil.equals(item['PH010RA1'], item['PH010RB1'])))) {\n" +
            "    seq.add(step1true, item);\n" +
            "  }\n" +
            "}\n" +
            "let step1 = 0;\n" +
            "let temp_step1_0 = distinct(step1true);"
             +"step1 = reduce(temp_step1_0, lambda(x, y) -> x + y['PH010RA1'] end,0);\n"
            +"return step1;";


        Map<String, Object> additionalData = new HashMap<>();
        try {
            Object execute = AviatorExecutor.execute(expression, inputData, additionalData);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }


    }
}
