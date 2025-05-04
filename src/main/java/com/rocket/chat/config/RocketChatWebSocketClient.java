package com.rocket.chat.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rocket.chat.service.RocketChatService;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class RocketChatWebSocketClient extends WebSocketClient {

    private static final Logger log = LoggerFactory.getLogger(RocketChatWebSocketClient.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private static final int NORMAL_CLOSURE_CODE = 1000;
    private static final int PING_INTERVAL = 30;

    private boolean shouldReconnect = true;
    private int reconnectAttempts = 0;
    private static final int MAX_RETRIES = 5;

    private final RocketChatService rocketChatService;
    private final String username;
    private final String password;
    private final String baseUrl;

    private String roomId;
    private String sessionId;
    private String userId;
    private String authToken;

    public RocketChatWebSocketClient(String wsUrl,
                                     RocketChatService rocketChatService,
                                     String username,
                                     String password,
                                     String baseUrl,
                                     String roomId) throws Exception {
        super(new URI(wsUrl));
        this.rocketChatService = rocketChatService;
        this.username = username;
        this.password = password;
        this.baseUrl = baseUrl;
        this.roomId = roomId;
        // Schedule periodic ping
        scheduler.scheduleAtFixedRate(this::sendPing, 0, PING_INTERVAL, TimeUnit.SECONDS);
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

            if ("connected".equals(json.path("msg").asText())) {
                sessionId = json.path("session").asText();
                log.info("WebSocket connected, session ID: {}", sessionId);
                loginWithCredentials();
            } else if ("result".equals(json.path("msg").asText()) && "login".equals(json.path("id").asText())) {
                JsonNode result = json.path("result");
                authToken = result.path("token").asText();
                userId = result.path("id").asText();
                log.info("Authenticated: token={}, userId={}", authToken, userId);
                subscribeToRoom();
            } else if ("changed".equals(json.path("msg").asText()) &&
                    "stream-room-messages".equals(json.path("collection").asText())) {
                JsonNode fields = json.path("fields");
                JsonNode args = fields.path("args");
                if (args.isArray() && args.size() > 0) {
                    JsonNode messageData = args.get(0);
                    String msg = messageData.path("msg").asText();
                    String sender = messageData.path("u").path("username").asText();
                    String roomId = messageData.path("rid").asText();
                    log.info("Received message: '{}' from user: {}", msg, sender);
                    // Notifying listeners
                    rocketChatService.processReceivedMessage(roomId, sender, msg);
                }
            }
        } catch (Exception e) {
            log.error("Error processing WebSocket message", e);
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        log.warn("WebSocket closed: {}, Reason: {}", code, reason);
        if (shouldReconnect && code != NORMAL_CLOSURE_CODE) {
            attemptReconnect();
        } else {
            log.info("WebSocket closed normally or reconnection disabled.");
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
                "user": { "username": "%s" },
                "password": { "digest": "%s", "algorithm": "sha-256" }
              }]
            }
            """, method, id, username, sha256(password));
        send(params);
    }

    private void subscribeToRoom() {
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
        log.info("Subscribed to room: {}", roomId);
    }

    private String sha256(String base) {
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(base.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) hexString.append(String.format("%02x", b));
            return hexString.toString();
        } catch (Exception ex) {
            log.error("Hashing failed", ex);
            throw new RuntimeException("SHA-256 hashing failed", ex);
        }
    }

    private void attemptReconnect() {
        if (reconnectAttempts >= MAX_RETRIES) {
            log.error("Max reconnect attempts reached. Giving up.");
            return;
        }
        int delay = (int) Math.pow(2, reconnectAttempts);
        reconnectAttempts++;
        scheduler.schedule(() -> {
            try {
                log.info("Attempting to reconnect to WebSocket (attempt {}/{})...", reconnectAttempts, MAX_RETRIES);
                this.reconnectBlocking();
                reconnectAttempts = 0;
                log.info("Reconnected successfully.");
                loginWithCredentials();
            } catch (Exception e) {
                log.error("Reconnection attempt failed", e);
            }
        }, delay, TimeUnit.SECONDS);
    }

    public void sendPing() {
        if (this.isOpen()) {
            send("{\"msg\":\"ping\"}");
//            log.info("Ping sent to WebSocket to keep connection alive.");
        } else {
            log.warn("WebSocket is closed. Attempting to reconnect...");
            attemptReconnect();
        }
    }
}