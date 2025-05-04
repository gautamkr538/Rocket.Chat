package com.rocket.chat.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class RocketChatService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final Logger log = LoggerFactory.getLogger(RocketChatService.class);

    @Autowired
    public RocketChatService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Value("${rocketchat.base-url}")
    private String baseUrl;

    @Value("${rocketchat.admin-username}")
    private String adminUsername;

    @Value("${rocketchat.admin-password}")
    private String adminPassword;

    private String authToken;
    private String userId;

    public void login() throws Exception {
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
            log.info("Successfully logged in to Rocket.Chat");
            log.info("Your authToken: {} and userId: {}", authToken, userId);
        } catch (Exception e) {
            log.error("Error during login to Rocket.Chat", e);
            throw new Exception("Login failed", e);
        }
    }

    public String sendMessage(String roomId, String message) throws Exception {
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
            throw new Exception("Failed to send message", e);
        }
    }

    public String getMessagesInChannel(String roomId) throws Exception {
        String url = baseUrl + "/channels.messages?roomId=" + roomId;
        HttpEntity<Void> request = new HttpEntity<>(authHeaders());
        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, request, String.class);
            log.info("Retrieved messages for room {}", roomId);
            return response.getBody();
        } catch (Exception e) {
            log.error("Error retrieving messages for room {}", roomId, e);
            throw new Exception("Failed to retrieve messages", e);
        }
    }

    public String listChannels() throws Exception {
        String url = baseUrl + "/channels.list";
        HttpEntity<Void> request = new HttpEntity<>(authHeaders());
        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, request, String.class);
            log.info("Retrieved channel list");
            return response.getBody();
        } catch (Exception e) {
            log.error("Error retrieving channels list", e);
            throw new Exception("Failed to retrieve channels list", e);
        }
    }

    public String listDirectMessages() throws Exception {
        String url = baseUrl + "/im.list";
        HttpEntity<Void> request = new HttpEntity<>(authHeaders());
        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, request, String.class);
            log.info("Retrieved direct message list");
            return response.getBody();
        } catch (Exception e) {
            log.error("Error retrieving direct messages list", e);
            throw new Exception("Failed to retrieve direct messages list", e);
        }
    }

    public String getMessageById(String messageId) throws Exception {
        String url = baseUrl + "/chat.getMessage?msgId=" + messageId;
        HttpEntity<Void> request = new HttpEntity<>(authHeaders());
        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, request, String.class);
            log.info("Retrieved message with ID {}", messageId);
            return response.getBody();
        } catch (Exception e) {
            log.error("Error retrieving message with ID {}", messageId, e);
            throw new Exception("Failed to retrieve message by ID", e);
        }
    }

    public void processReceivedMessage(String roomId, String sender, String message) {
        log.info("Processing received message: '{}' from room '{}' and sender: {}", message, roomId, sender);
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Auth-Token", authToken);
        headers.set("X-User-Id", userId);
        return headers;
    }
}