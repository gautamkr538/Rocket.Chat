package com.rocket.chat.config;

import com.rocket.chat.service.RocketChatService;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class RocketChatWebSocketManager {

    @Value("${rocketchat.websocket-url}")
    private String wsUrl;

    @Value("${rocketchat.base-url}")
    private String baseUrl;

    @Value("${rocketchat.admin-username}")
    private String username;

    @Value("${rocketchat.admin-password}")
    private String password;

    @Value("${rocketchat.admin-roomId}")
    private String roomId;

    private final RocketChatService rocketChatService;

    public RocketChatWebSocketManager(RocketChatService rocketChatService) {
        this.rocketChatService = rocketChatService;
    }

    @PostConstruct
    public void startWebSocketClient() {
        try {
            RocketChatWebSocketClient client = new RocketChatWebSocketClient(wsUrl, rocketChatService, username, password, baseUrl, roomId);
            client.connectBlocking();
        } catch (Exception e) {
            throw new RuntimeException("Failed to start WebSocket client", e);
        }
    }
}