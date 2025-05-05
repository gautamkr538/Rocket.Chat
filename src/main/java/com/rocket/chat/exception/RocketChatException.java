package com.rocket.chat.exception;

public class RocketChatException extends RuntimeException {

    public RocketChatException(String message) {
        super(message);
    }

    public RocketChatException(String message, Throwable cause) {
        super(message, cause);
    }
}