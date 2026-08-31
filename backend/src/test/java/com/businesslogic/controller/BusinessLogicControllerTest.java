package com.businesslogic.controller;

import com.businesslogic.dto.BuildInputDataRequestDTO;
import com.businesslogic.dto.JsonPathParamDTO;
import com.businesslogic.service.BusinessLogicService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Arrays;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BusinessLogicController 接口测试
 *
 * <p>使用 Standalone MockMvc 装配控制器，重点验证 /build-input-data 接口：
 * 前端传 JsonPathParamDTO 参数列表，后端正确构建出 inputData JSON 字符串。</p>
 */
public class BusinessLogicControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    public void setUp() {
        BusinessLogicService service = mock(BusinessLogicService.class);
        objectMapper = new ObjectMapper();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new BusinessLogicController(service, objectMapper))
                .build();
    }

    /**
     * 测试 /build-input-data：对象/数组节点使用 JSON 字符串
     */
    @Test
    public void testBuildInputData_objectAndArrayNodes() throws Exception {
        BuildInputDataRequestDTO request = new BuildInputDataRequestDTO();
        request.setParams(Arrays.asList(
                new JsonPathParamDTO("$.amount", "500", "number"),
                new JsonPathParamDTO("$.user", "{\"name\":\"Tom\",\"age\":30}", "object"),
                new JsonPathParamDTO("$.tags", "[\"a\",\"b\"]", "array")));

        mockMvc.perform(post("/business-logic/build-input-data")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.inputData").value(
                        "{\"amount\":500,\"user\":{\"name\":\"Tom\",\"age\":30},\"tags\":[\"a\",\"b\"]}"));
    }

    /**
     * 测试 /build-input-data：传了表达式时用 Groovy 执行并附带结果
     */
    @Test
    public void testBuildInputData_withExpression() throws Exception {
        BuildInputDataRequestDTO request = new BuildInputDataRequestDTO();
        request.setExpression("def name = JsonPathUtil.readString(inputData, '$.user.name'); return name;");
        request.setParams(Arrays.asList(new JsonPathParamDTO("$.user.name", "Tom", "string")));

        mockMvc.perform(post("/business-logic/build-input-data")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.inputData").value("{\"user\":{\"name\":\"Tom\"}}"))
                .andExpect(jsonPath("$.data.result").value("Tom"));
    }

    /**
     * 测试 /build-input-data：params 缺失时返回错误
     */
    @Test
    public void testBuildInputData_missingParams() throws Exception {
        BuildInputDataRequestDTO request = new BuildInputDataRequestDTO();
        request.setExpression("def x = 1; return x;");

        mockMvc.perform(post("/business-logic/build-input-data")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500));
    }
}
