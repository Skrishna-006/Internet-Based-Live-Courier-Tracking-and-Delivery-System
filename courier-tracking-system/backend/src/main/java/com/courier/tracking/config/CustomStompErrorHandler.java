package com.courier.tracking.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.StompSubProtocolErrorHandler;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Custom STOMP sub-protocol error handler that unwraps security and validation exceptions
 * and transmits detailed STOMP ERROR frames to clients with meaningful error messages.
 */
@Component
public class CustomStompErrorHandler extends StompSubProtocolErrorHandler {

    private static final Logger log = LoggerFactory.getLogger(CustomStompErrorHandler.class);
    private final ObjectMapper objectMapper;

    public CustomStompErrorHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Message<byte[]> handleClientMessageProcessingError(@Nullable Message<byte[]> clientMessage, Throwable ex) {
        Throwable cause = ex;
        while (cause.getCause() != null && cause != cause.getCause()) {
            if (cause instanceof org.springframework.security.access.AccessDeniedException
                    || cause instanceof org.springframework.security.core.AuthenticationException) {
                break;
            }
            cause = cause.getCause();
        }

        String errorMessage = (cause != null && cause.getMessage() != null)
                ? cause.getMessage()
                : "An internal security error occurred during STOMP frame processing";

        log.warn("STOMP processing error returned to client: {}", errorMessage);

        StompHeaderAccessor errorAccessor = StompHeaderAccessor.create(StompCommand.ERROR);
        errorAccessor.setMessage(errorMessage);
        errorAccessor.setLeaveMutable(true);

        byte[] payloadBytes;
        try {
            Map<String, Object> errorPayload = Map.of(
                    "success", false,
                    "message", errorMessage
            );
            payloadBytes = objectMapper.writeValueAsBytes(errorPayload);
        } catch (Exception jsonEx) {
            payloadBytes = errorMessage.getBytes(StandardCharsets.UTF_8);
        }

        return MessageBuilder.createMessage(payloadBytes, errorAccessor.getMessageHeaders());
    }
}
