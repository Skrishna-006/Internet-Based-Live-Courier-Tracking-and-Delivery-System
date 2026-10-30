package com.courier.tracking.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.Arrays;

/**
 * WebSocket and STOMP message broker configuration.
 * Configures an in-memory message broker with destination prefix /topic,
 * application destination prefix /app, and STOMP handshake endpoint /ws.
 * Registers STOMP authentication and authorization interceptors on inbound channels.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Value("${app.cors.allowed-origins:http://localhost:5173}")
    private String allowedOrigins;

    private final StompChannelInterceptor stompChannelInterceptor;
    private final CustomStompErrorHandler customStompErrorHandler;

    public WebSocketConfig(StompChannelInterceptor stompChannelInterceptor,
                           CustomStompErrorHandler customStompErrorHandler) {
        this.stompChannelInterceptor = stompChannelInterceptor;
        this.customStompErrorHandler = customStompErrorHandler;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // In-memory simple broker for broadcasting to topics
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        String[] origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);

        String[] originPatterns = (origins.length > 0) ? origins : new String[]{"*"};

        // SockJS fallback endpoint
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(originPatterns)
                .withSockJS();

        // Standard WebSocket endpoint
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(originPatterns);

        registry.setErrorHandler(customStompErrorHandler);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompChannelInterceptor);
    }
}
