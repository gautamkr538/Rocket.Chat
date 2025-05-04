package com.rocket.chat.controller;

import com.rocket.chat.service.RocketChatService;
import com.rocket.chat.dto.MessageRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/chat")
public class ChatController {

    private final RocketChatService chatService;

    @Autowired
    public ChatController(RocketChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping("/login")
    public ResponseEntity<String> login() {
        try {
            chatService.login();
            return ResponseEntity.ok("Logged in successfully");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Login failed: " + e.getMessage());
        }
    }

    @PostMapping("/send")
    public ResponseEntity<String> sendMessage(@RequestBody MessageRequest request) {
        try {
            String response = chatService.sendMessage(request.getRoomId(), request.getMessage());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Failed to send message: " + e.getMessage());
        }
    }

    @GetMapping("/messages")
    public ResponseEntity<String> getMessagesInChannel(@RequestParam String roomId) {
        try {
            String response = chatService.getMessagesInChannel(roomId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Failed to retrieve messages: " + e.getMessage());
        }
    }

    @GetMapping("/channels")
    public ResponseEntity<String> listChannels() {
        try {
            String response = chatService.listChannels();
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Failed to retrieve channels list: " + e.getMessage());
        }
    }

    @GetMapping("/direct-messages")
    public ResponseEntity<String> listDirectMessages() {
        try {
            String response = chatService.listDirectMessages();
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Failed to retrieve direct messages list: " + e.getMessage());
        }
    }

    @GetMapping("/message")
    public ResponseEntity<String> getMessageById(@RequestParam String messageId) {
        try {
            String response = chatService.getMessageById(messageId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Failed to retrieve message: " + e.getMessage());
        }
    }
}