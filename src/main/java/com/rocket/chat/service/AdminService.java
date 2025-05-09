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

@Service
public class AdminService {

    private static final Logger log = LoggerFactory.getLogger(AdminService.class);

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

    public AdminService(RestTemplate restTemplate) {
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

            log.info("Admin login successful. Admin userId: {}", userId);
        } catch (Exception e) {
            log.error("Admin login failed", e);
            throw new RocketChatException("Failed to login as admin", e);
        }
    }

    public void createUser(String username, String email, String name, String password) {
        String url = baseUrl + "/users.create";
        HttpHeaders headers = authHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = new HashMap<>();
        body.put("username", username);
        body.put("email", email);
        body.put("name", name);
        body.put("password", password);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
            log.info("User created: {}", username);
        } catch (Exception e) {
            log.error("Failed to create user: {}", username, e);
            throw new RocketChatException("Failed to create user", e);
        }
    }

    public HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Auth-Token", authToken);
        headers.set("X-User-Id", userId);
        return headers;
    }

    public String getAdminAuthToken() {
        return authToken;
    }

    public String getAdminUserId() {
        return userId;
    }
}