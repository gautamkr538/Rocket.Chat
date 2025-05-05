package com.rocket.chat.config;

import com.rocket.chat.service.UserService;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class RocketChatWebSocketManager {

    @Value("${rocketchat.websocket-url}")
    private String wsUrl;

    @Value("${rocketchat.admin-username}")
    private String username;

    @Value("${rocketchat.admin-password}")
    private String password;

    @Value("${rocketchat.admin-roomId}")
    private String roomId;

    private final UserService userService;

    public RocketChatWebSocketManager(UserService userService) {
        this.userService = userService;
    }

    @PostConstruct
    public void startWebSocketClient() {
        try {
            RocketChatWebSocketClient client = new RocketChatWebSocketClient(wsUrl, userService, username, password, roomId);
            client.connectBlocking();
        } catch (Exception e) {
            throw new RuntimeException("Failed to start WebSocket client", e);
        }
    }
}