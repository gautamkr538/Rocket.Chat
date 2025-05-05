package com.rocket.chat.controller;

import com.rocket.chat.dto.MessageRequest;
import com.rocket.chat.exception.RocketChatException;
import com.rocket.chat.service.AdminService;
import com.rocket.chat.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/chat")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final AdminService adminService;
    private final UserService userService;

    @Autowired
    public ChatController(AdminService adminService, UserService userService) {
        this.adminService = adminService;
        this.userService = userService;
    }

    @PostMapping("/login")
    public ResponseEntity<String> login() {
        try {
            adminService.login();
            // Optionally initialize userService with admin token
            userService.setAuth(adminService.getAdminAuthToken(), adminService.getAdminUserId());
            return ResponseEntity.ok("Admin logged in successfully");
        } catch (RocketChatException e) {
            log.error("Login failed", e);
            return ResponseEntity.status(500).body("Login failed: " + e.getMessage());
        }
    }

    @PostMapping("/create-user")
    public ResponseEntity<String> createUser(@RequestParam String username, @RequestParam String email, @RequestParam String password) {
        try {
            adminService.createUser(username, email, password);
            return ResponseEntity.ok("User created successfully");
        } catch (RocketChatException e) {
            log.error("User creation failed", e);
            return ResponseEntity.status(500).body("User creation failed: " + e.getMessage());
        }
    }

    @PostMapping("/send")
    public ResponseEntity<String> sendMessage(@RequestBody MessageRequest request) {
        try {
            String response = userService.sendMessage(request.getRoomId(), request.getMessage());
            return ResponseEntity.ok(response);
        } catch (RocketChatException e) {
            log.error("Failed to send message", e);
            return ResponseEntity.status(500).body("Failed to send message: " + e.getMessage());
        }
    }

    @GetMapping("/messages")
    public ResponseEntity<String> getMessagesInChannel(@RequestParam String roomId) {
        try {
            String response = userService.getMessagesInChannel(roomId);
            return ResponseEntity.ok(response);
        } catch (RocketChatException e) {
            log.error("Failed to retrieve messages", e);
            return ResponseEntity.status(500).body("Failed to retrieve messages: " + e.getMessage());
        }
    }

    @GetMapping("/direct-messages")
    public ResponseEntity<String> listDirectMessages() {
        try {
            String response = userService.listDirectMessages();
            return ResponseEntity.ok(response);
        } catch (RocketChatException e) {
            log.error("Failed to retrieve direct messages", e);
            return ResponseEntity.status(500).body("Failed to retrieve direct messages: " + e.getMessage());
        }
    }

    @PostMapping("/direct-message")
    public ResponseEntity<String> createDirectMessage(@RequestParam String username) {
        try {
            String roomId = userService.createDirectMessage(username);
            return ResponseEntity.ok("Direct message room created: " + roomId);
        } catch (RocketChatException e) {
            log.error("Failed to create direct message room", e);
            return ResponseEntity.status(500).body("Failed to create direct message room: " + e.getMessage());
        }
    }
}