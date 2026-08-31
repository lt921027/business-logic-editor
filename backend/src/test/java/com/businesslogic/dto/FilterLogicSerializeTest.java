package com.businesslogic.dto;

import com.businesslogic.vo.FilterLogicVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FilterLogicDTO / FilterLogicVO 字段重命名（value → typeValue）后的序列化兼容性测试。
 *
 * <p>验证：旧字段名 "value" 仍能反序列化；序列化统一输出新字段名 "typeValue"。</p>
 */
public class FilterLogicSerializeTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void testDtoDeserializeOldValueKey() throws Exception {
        FilterLogicDTO dto = objectMapper.readValue(
                "{\"type\":\"count\",\"value\":\"all\"}", FilterLogicDTO.class);

        assertEquals("count", dto.getType());
        assertEquals("all", dto.getTypeValue());
    }

    @Test
    public void testDtoDeserializeNewTypeValueKey() throws Exception {
        FilterLogicDTO dto = objectMapper.readValue(
                "{\"type\":\"sum\",\"typeValue\":\"amount\"}", FilterLogicDTO.class);

        assertEquals("sum", dto.getType());
        assertEquals("amount", dto.getTypeValue());
    }

    @Test
    public void testDtoSerializeUsesTypeValueKey() throws Exception {
        FilterLogicDTO dto = new FilterLogicDTO();
        dto.setTypeValue("all");

        String json = objectMapper.writeValueAsString(dto);

        assertTrue(json.contains("\"typeValue\":\"all\""));
        assertFalse(json.contains("\"value\":"));
    }

    @Test
    public void testVoDeserializeOldValueKey() throws Exception {
        FilterLogicVO vo = objectMapper.readValue(
                "{\"type\":\"count\",\"value\":\"all\"}", FilterLogicVO.class);

        assertEquals("count", vo.getType());
        assertEquals("all", vo.getTypeValue());
    }
}
