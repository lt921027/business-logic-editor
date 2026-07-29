package com.businesslogic.sync;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;

@Component
public class BroadcastAckProcessor implements MessageListener {

    private static final Logger logger = LoggerFactory.getLogger(BroadcastAckProcessor.class);

    private static final String BROADCAST_CHANNEL = "business-logic:broadcast";

    private final RedisMessageListenerContainer listenerContainer;
    private final BroadcastSyncCoordinator coordinator;
    private final ObjectMapper objectMapper;

    public BroadcastAckProcessor(RedisMessageListenerContainer listenerContainer,
                                 BroadcastSyncCoordinator coordinator,
                                 ObjectMapper objectMapper) {
        this.listenerContainer = listenerContainer;
        this.coordinator = coordinator;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void register() {
        listenerContainer.addMessageListener(this, new ChannelTopic(BROADCAST_CHANNEL));
        logger.info("BroadcastAckProcessor 已注册监听频 {}", BROADCAST_CHANNEL);
    }

    @PreDestroy
    public void unregister() {
        listenerContainer.removeMessageListener(this);
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String body = new String(message.getBody(), StandardCharsets.UTF_8);
            JsonNode node = objectMapper.readTree(body);

            if (!node.has("version")) {
                return;
            }

            long version = node.get("version").asLong();
            if (version >= 0) {
                coordinator.sendAck(version);
            }

        } catch (Exception e) {
            logger.debug("解析广播消息确认字段失败: {}", e.getMessage());
        }
    }
}