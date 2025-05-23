package com.rocket.chat.dto;

public class UserSession {
    private final String userId;
    private final String authToken;

    public UserSession(String userId, String authToken) {
        this.userId = userId;
        this.authToken = authToken;
    }

    public String getUserId() {
        return userId;
    }

    public String getAuthToken() {
        return authToken;
    }
}
