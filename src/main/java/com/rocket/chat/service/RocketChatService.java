package com.rocket.chat.service;

import com.fasterxml.jackson.core.JsonProcessingException;
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

@Service
public class RocketChatService {

    private static final Logger logger = LoggerFactory.getLogger(RocketChatService.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${rocketchat.base-url}")
    private String baseUrl;

    @Value("${rocketchat.admin-username}")
    private String adminUsername;

    @Value("${rocketchat.admin-password}")
    private String adminPassword;

    private String authToken;
    private String userId;

    public RocketChatService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public void login() throws RocketChatException {
        String loginUrl = baseUrl + "/login";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = String.format("{\"user\": \"%s\", \"password\": \"%s\"}", adminUsername, adminPassword);
        HttpEntity<String> request = new HttpEntity<>(body, headers);
        try {
            ResponseEntity<String> response = restTemplate.postForEntity(loginUrl, request, String.class);
            JsonNode json = objectMapper.readTree(response.getBody());

            authToken = json.get("data").get("authToken").asText();
            userId = json.get("data").get("userId").asText();
            logger.info("Successfully logged in to Rocket.Chat");
        } catch (Exception e) {
            logger.error("Error during login to Rocket.Chat", e);
            throw new RocketChatException("Login failed", e);
        }
    }

    public String sendMessage(String roomId, String message) throws RocketChatException {
        String url = baseUrl + "/chat.postMessage";
        HttpHeaders headers = authHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String json = String.format("{\"roomId\": \"%s\", \"text\": \"%s\"}", roomId, message);
        HttpEntity<String> request = new HttpEntity<>(json, headers);
        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
            logger.info("Message sent to room {}: {}", roomId, message);
            return response.getBody();
        } catch (Exception e) {
            logger.error("Error sending message to room {}", roomId, e);
            throw new RocketChatException("Failed to send message", e);
        }
    }

    public String getMessagesInChannel(String roomId) throws RocketChatException {
        String url = baseUrl + "/channels.messages?roomId=" + roomId;
        HttpEntity<Void> request = new HttpEntity<>(authHeaders());
        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, request, String.class);
            logger.info("Retrieved messages for room {}", roomId);
            return response.getBody();
        } catch (Exception e) {
            logger.error("Error retrieving messages for room {}", roomId, e);
            throw new RocketChatException("Failed to retrieve messages", e);
        }
    }

    public String listChannels() throws RocketChatException {
        String url = baseUrl + "/channels.list";
        HttpEntity<Void> request = new HttpEntity<>(authHeaders());
        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, request, String.class);
            logger.info("Retrieved channel list");
            return response.getBody();
        } catch (Exception e) {
            logger.error("Error retrieving channels list", e);
            throw new RocketChatException("Failed to retrieve channels list", e);
        }
    }

    public String listDirectMessages() throws RocketChatException {
        String url = baseUrl + "/im.list";
        HttpEntity<Void> request = new HttpEntity<>(authHeaders());
        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, request, String.class);
            logger.info("Retrieved direct message list");
            return response.getBody();
        } catch (Exception e) {
            logger.error("Error retrieving direct messages list", e);
            throw new RocketChatException("Failed to retrieve direct messages list", e);
        }
    }

    public String getMessageById(String messageId) throws RocketChatException {
        String url = baseUrl + "/chat.getMessage?msgId=" + messageId;
        HttpEntity<Void> request = new HttpEntity<>(authHeaders());
        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, request, String.class);
            logger.info("Retrieved message with ID {}", messageId);
            return response.getBody();
        } catch (Exception e) {
            logger.error("Error retrieving message with ID {}", messageId, e);
            throw new RocketChatException("Failed to retrieve message by ID", e);
        }
    }

    public void processReceivedMessage(String roomId, String sender, String message) {
        logger.info("Processing received message: '{}' from room '{}' and sender: {}", message, roomId, sender);
        // Implement auto-reply and inactivity handling here
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Auth-Token", authToken);
        headers.set("X-User-Id", userId);
        return headers;
    }

    public void createUser(String username, String email, String password) {
        String url = baseUrl + "/api/v1/users.create";
        HttpHeaders headers = authHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> body = new HashMap<>();
        body.put("username", username);
        body.put("email", email);
        body.put("name", username);
        body.put("password", password);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        restTemplate.postForEntity(url, request, String.class);
    }

    public String createDirectMessage(String username) throws JsonProcessingException {
        String url = baseUrl + "/api/v1/im.create";
        HttpHeaders headers = authHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> body = new HashMap<>();
        body.put("username", username);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
        JsonNode json = objectMapper.readTree(response.getBody());
        return json.get("room").get("_id").asText();
    }
}