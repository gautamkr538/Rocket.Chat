package com.rocket.chat.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rocket.chat.service.RocketChatService;
import jakarta.annotation.PostConstruct;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Component
public class RocketChatWebSocketClient extends WebSocketClient {

    private static final Logger log = LoggerFactory.getLogger(RocketChatWebSocketClient.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private RocketChatService rocketChatService;

    @Value("${rocketchat.base-url}")
    private String baseUrl;

    @Value("${rocketchat.admin-username}")
    private String username;

    @Value("${rocketchat.admin-password}")
    private String password;

    private String roomId = "GENERAL";
    private String sessionId;
    private String userId;
    private String authToken;

    public RocketChatWebSocketClient(@Value("${rocketchat.websocket-url}") String wsUrl) throws Exception {
        super(new URI(wsUrl));
    }

    @PostConstruct
    public void init() {
        try {
            this.connectBlocking();
        } catch (Exception e) {
            log.error("WebSocket connection failed", e);
            throw new RuntimeException("Failed to establish WebSocket connection", e);
        }
    }

    @Override
    public void onOpen(ServerHandshake handshakeData) {
        log.info("WebSocket connection opened");
        send("{\"msg\":\"connect\",\"version\":\"1\",\"support\":[\"1\",\"pre2\",\"pre1\"]}");
    }

    @Override
    public void onMessage(String message) {
        try {
            JsonNode json = objectMapper.readTree(message);

            // Handle "connected" event
            if ("connected".equals(json.path("msg").asText())) {
                sessionId = json.path("session").asText();
                log.info("WebSocket connected, session ID: {}", sessionId);
                loginWithCredentials();
            }
            // Handle login response
            else if ("result".equals(json.path("msg").asText()) && "login".equals(json.path("id").asText())) {
                JsonNode result = json.path("result");
                authToken = result.path("token").asText();
                userId = result.path("id").asText();
                log.info("Authenticated via WebSocket: token={}, userId={}", authToken, userId);
                subscribeToAllRooms();
            }
            // Handle new user join event
            else if ("changed".equals(json.path("msg").asText()) &&
                    "stream-room-messages".equals(json.path("collection").asText()) &&
                    json.toString().contains("user joined")) {
                rocketChatService.sendMessage(roomId, "Welcome to the room!");
            }
            // Handle message events
            else if ("changed".equals(json.path("msg").asText()) && "stream-room-messages".equals(json.path("collection").asText())) {
                JsonNode fields = json.path("fields");
                JsonNode args = fields.path("args");
                if (args.isArray() && args.size() > 0) {
                    JsonNode messageData = args.get(0);
                    String msg = messageData.path("msg").asText();
                    String sender = messageData.path("u").path("username").asText();
                    String roomId = messageData.path("rid").asText();
                    log.info("Received message: '{}' from user: {}", msg, sender);
                    // Trigger backend action
                    rocketChatService.sendMessage(roomId, "Echo: " + msg);
                }
            }
        } catch (Exception e) {
            log.error("Error processing WebSocket message", e);
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        log.warn("WebSocket closed: {}, Reason: {}", code, reason);
        // Try to reconnect
        try {
            this.connectBlocking();
        } catch (Exception e) {
            log.error("Reconnection attempt failed", e);
        }
    }

    @Override
    public void onError(Exception ex) {
        log.error("WebSocket error occurred", ex);
    }

    private void loginWithCredentials() {
        String id = "login";
        String method = "login";
        String params = String.format("""
            {
                "msg": "method",
                "method": "%s",
                "id": "%s",
                "params": [{
                    "user": {"username": "%s"},
                    "password": {"digest": "%s", "algorithm": "sha-256"}
                }]
            }
            """, method, id, username, sha256(password));

        send(params);
    }

    private void subscribeToAllRooms() {
        // Subscribe to all messages for a room (use real roomId here)
        String roomId = "GENERAL";
        String subId = UUID.randomUUID().toString();

        String subscribeJson = String.format("""
            {
                "msg": "sub",
                "id": "%s",
                "name": "stream-room-messages",
                "params": ["%s", false]
            }
            """, subId, roomId);

        send(subscribeJson);
        log.info("Subscribed to room messages: {}", roomId);
    }

    private String sha256(String base) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(base.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) hexString.append(String.format("%02x", b));
            return hexString.toString();
        } catch (Exception ex) {
            log.error("Error hashing password", ex);
            throw new RuntimeException("Error hashing password", ex);
        }
    }
}