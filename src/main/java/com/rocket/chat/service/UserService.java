package com.rocket.chat.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rocket.chat.exception.RocketChatException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, Long> lastActivityMap = new ConcurrentHashMap<>();
    private final Set<String> autoRepliedRooms = ConcurrentHashMap.newKeySet();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    private static final long INACTIVITY_TIMEOUT_MS = TimeUnit.MINUTES.toMillis(10);

    @Value("${rocketchat.base-url}")
    private String baseUrl;

    private String authToken;
    private String userId;

    public UserService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public void setAuth(String token, String userId) {
        this.authToken = token;
        this.userId = userId;
    }

    public String sendMessage(String roomId, String message) {
        String url = baseUrl + "/chat.postMessage";
        HttpHeaders headers = authHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        String json = String.format("{\"roomId\": \"%s\", \"text\": \"%s\"}", roomId, message);
        HttpEntity<String> request = new HttpEntity<>(json, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
            log.info("Message sent to room {}: {}", roomId, message);
            return response.getBody();
        } catch (Exception e) {
            log.error("Error sending message to room {}", roomId, e);
            throw new RocketChatException("Failed to send message", e);
        }
    }

    public String getMessagesInChannel(String roomId) {
        String url = baseUrl + "/channels.messages?roomId=" + roomId;
        HttpEntity<Void> request = new HttpEntity<>(authHeaders());

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, request, String.class);
            log.info("Fetched messages for room {}", roomId);
            return response.getBody();
        } catch (Exception e) {
            log.error("Error fetching messages for room {}", roomId, e);
            throw new RocketChatException("Failed to fetch messages", e);
        }
    }

    public String listDirectMessages() {
        String url = baseUrl + "/im.list";
        HttpEntity<Void> request = new HttpEntity<>(authHeaders());

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, request, String.class);
            log.info("Fetched direct messages list");
            return response.getBody();
        } catch (Exception e) {
            log.error("Error fetching direct messages", e);
            throw new RocketChatException("Failed to fetch direct messages", e);
        }
    }

    public String createDirectMessage(String username) {
        String url = baseUrl + "/api/v1/im.create";
        HttpHeaders headers = authHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = new HashMap<>();
        body.put("username", username);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
            JsonNode json = objectMapper.readTree(response.getBody());
            log.info("Created DM room with {}", username);
            return json.get("room").get("_id").asText();
        } catch (Exception e) {
            log.error("Error creating DM with {}", username, e);
            throw new RocketChatException("Failed to create direct message", e);
        }
    }

    public void processReceivedMessage(String roomId, String sender, String message) {
        log.info("Received message from roomId={}, sender={}, message={}", roomId, sender, message);
        // Update last activity time
        lastActivityMap.put(roomId, System.currentTimeMillis());
        // Auto-reply only once per session
        if (!autoRepliedRooms.contains(roomId)) {
            String autoReply = "Thank you for your message! An admin will respond shortly.";
            try {
                sendMessage(roomId, autoReply);
                autoRepliedRooms.add(roomId);
                log.info("Sent auto-reply to room {}", roomId);
            } catch (RocketChatException e) {
                log.error("Failed to send auto-reply", e);
            }
        }
        // Schedule session close (resets timer on every message)
        scheduleSessionClose(roomId);
    }

    private void scheduleSessionClose(String roomId) {
        scheduler.schedule(() -> {
            long lastActivity = lastActivityMap.getOrDefault(roomId, 0L);
            long now = System.currentTimeMillis();

            if (now - lastActivity >= INACTIVITY_TIMEOUT_MS) {
                try {
                    String closingMessage = "This session has been closed due to inactivity. Please start a new chat if needed.";
                    sendMessage(roomId, closingMessage);
                    log.info("Closed session for room {} due to inactivity", roomId);
                    lastActivityMap.remove(roomId);
                    autoRepliedRooms.remove(roomId);
                } catch (RocketChatException e) {
                    log.error("Failed to send session close message", e);
                }
            }
        }, INACTIVITY_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Auth-Token", authToken);
        headers.set("X-User-Id", userId);
        return headers;
    }
}