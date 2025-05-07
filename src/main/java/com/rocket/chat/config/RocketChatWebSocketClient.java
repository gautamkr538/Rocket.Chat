package com.rocket.chat.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rocket.chat.service.UserService;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.*;

public class RocketChatWebSocketClient extends WebSocketClient {

    private static final Logger log = LoggerFactory.getLogger(RocketChatWebSocketClient.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    private static final int NORMAL_CLOSURE_CODE = 1000;
    private static final int PING_INTERVAL = 30;
    private static final int MAX_RETRIES = 5;

    private final UserService userService;
    private final String username;
    private final String password;
    private final String roomId;

    private boolean shouldReconnect = true;
    private int reconnectAttempts = 0;

    private String sessionId;
    private String userId;
    private String authToken;

    public RocketChatWebSocketClient(String wsUrl,
                                     UserService userService,
                                     String username,
                                     String password,
                                     String roomId) throws Exception {
        super(new URI(wsUrl));
        this.userService = userService;
        this.username = username;
        this.password = password;
        this.roomId = roomId;
        scheduler.scheduleAtFixedRate(this::sendPing, 0, PING_INTERVAL, TimeUnit.SECONDS);
    }

    @Override
    public void onOpen(ServerHandshake handshakeData) {
        log.info("WebSocket connection opened");
        send(buildConnectPayload());
    }

    @Override
    public void onMessage(String message) {
        try {
            JsonNode json = objectMapper.readTree(message);
            String msgType = json.path("msg").asText();

            switch (msgType) {
                case "connected" -> {
                    sessionId = json.path("session").asText();
                    log.info("Connected to WebSocket, session ID: {}", sessionId);
                    loginWithCredentials();
                }
                case "result" -> handleResultMessage(json);
                case "changed" -> handleChangedMessage(json);
                case "ping" -> {
                    send("{\"msg\":\"pong\"}");
                    log.debug("Received ping, sent pong");
                }
                default -> log.debug("Unhandled WebSocket message: {}", message);
            }
        } catch (Exception e) {
            log.error("Error parsing WebSocket message", e);
        }
    }

    private void handleResultMessage(JsonNode json) {
        String id = json.path("id").asText();
        JsonNode result = json.path("result");

        if ("login".equals(id)) {
            if (result == null || result.isNull()) {
                log.error("Login failed: {}", json.toPrettyString());
                return;
            }
            authToken = result.path("token").asText(null);
            userId = result.path("id").asText(null);
            if (authToken == null || userId == null) {
                log.error("Login response missing auth fields: {}", json.toPrettyString());
                return;
            }
            log.info("Login successful. User ID: {}, Token: {}", userId, authToken);
            subscribeToRoom();
            subscribeToUserNotify();  // Subscribe to private notifications
        } else {
            log.debug("Received result for ID {}: {}", id, json.toPrettyString());
        }
    }

    private void handleChangedMessage(JsonNode json) {
        String collection = json.path("collection").asText();
        if ("stream-room-messages".equals(collection)) {
            JsonNode args = json.path("fields").path("args");
            if (args.isArray() && !args.isEmpty()) {
                JsonNode messageData = args.get(0);
                String message = messageData.path("msg").asText();
                String sender = messageData.path("u").path("username").asText();
                String roomId = messageData.path("rid").asText();
                log.info("Received message: '{}' from user: {}", message, sender);
                userService.processReceivedMessage(roomId, sender, message);
            }
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        log.warn("WebSocket closed (code={}): {}", code, reason);
        if (shouldReconnect && code != NORMAL_CLOSURE_CODE) {
            attemptReconnect();
        }
    }

    @Override
    public void onError(Exception ex) {
        log.error("WebSocket error", ex);
    }

    private void loginWithCredentials() {
        String hashedPassword = sha256(password);
        String loginPayload = String.format("""
            {
              "msg": "method",
              "method": "login",
              "id": "login",
              "params": [
                {
                  "user": { "username": "%s" },
                  "password": { "digest": "%s", "algorithm": "sha-256" }
                }
              ]
            }
            """, username, hashedPassword);
        send(loginPayload);
    }

    private void subscribeToRoom() {
        String subId = UUID.randomUUID().toString();
        String subscribePayload = String.format("""
            {
              "msg": "sub",
              "id": "%s",
              "name": "stream-room-messages",
              "params": [ "%s", false ]
            }
            """, subId, roomId);
        send(subscribePayload);
        log.info("Subscribed to room: {}", roomId);
    }

    private void subscribeToUserNotify() {
        if (userId == null) return;

        String subId = UUID.randomUUID().toString();
        String payload = String.format("""
        {
          "msg": "sub",
          "id": "%s",
          "name": "stream-notify-user",
          "params": [ "%s/message", false ]
        }
        """, subId, userId);
        send(payload);
        log.info("Subscribed to direct messages for user: {}", userId);
    }

    private String buildConnectPayload() {
        return """
            {
              "msg": "connect",
              "version": "1",
              "support": ["1"]
            }
            """;
    }

    public void sendPing() {
        if (isOpen()) {
            send("{\"msg\":\"ping\"}");
        } else {
            log.warn("WebSocket is closed. Attempting reconnect...");
            attemptReconnect();
        }
    }

    private void attemptReconnect() {
        if (reconnectAttempts >= MAX_RETRIES) {
            log.error("Max reconnect attempts reached. Giving up.");
            return;
        }

        int delay = (int) Math.pow(2, reconnectAttempts++);
        scheduler.schedule(() -> {
            try {
                log.info("Reconnecting WebSocket (attempt {}/{})...", reconnectAttempts, MAX_RETRIES);
                reconnectBlocking();
                reconnectAttempts = 0;
            } catch (InterruptedException e) {
                log.error("Reconnect attempt interrupted", e);
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.error("Reconnect failed", e);
            }
        }, delay, TimeUnit.SECONDS);
    }

    private String sha256(String base) {
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(base.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            log.error("SHA-256 hashing failed", e);
            throw new RuntimeException("SHA-256 hashing failed", e);
        }
    }
}