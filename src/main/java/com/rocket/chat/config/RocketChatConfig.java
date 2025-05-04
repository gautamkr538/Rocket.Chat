package com.rocket.chat.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RocketChatConfig {
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
    @Bean
    public RocketChatWebSocketClient rocketChatWebSocketClient(@Value("${rocketchat.websocket-url}") String wsUrl) throws Exception {
        RocketChatWebSocketClient client = new RocketChatWebSocketClient(wsUrl);
        client.connectBlocking();
        return client;
    }
}
