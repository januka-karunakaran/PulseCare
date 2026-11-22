package com.hospital.backend.config;

import com.hospital.backend.model.User;
import com.hospital.backend.repository.UserRepository;
import com.hospital.backend.security.CustomUserDetailsService;
import com.hospital.backend.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.security.Principal;
import java.util.Arrays;

@Configuration
@EnableWebSocketMessageBroker
@Order(Ordered.HIGHEST_PRECEDENCE + 99)
@RequiredArgsConstructor
@Slf4j
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Value("${spring.websocket.allowed-origins:${cors.allowed-origins:http://localhost:3000,http://localhost:3001}}")
    private String allowedOrigins;

    private final JwtService jwtService;
    private final CustomUserDetailsService customUserDetailsService;
    private final UserRepository userRepository;

    @Override
    public void configureMessageBroker(@NonNull MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic", "/queue");
        config.setUserDestinationPrefix("/user");
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(@NonNull StompEndpointRegistry registry) {
        String[] origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty() && !s.equals("*"))
                .toArray(String[]::new);

        if (origins.length == 0) {
            origins = new String[]{"http://localhost:3000"};
        }

        registry.addEndpoint("/ws", "/ws/notifications")
                .setAllowedOrigins(origins)
                .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(createChannelInterceptor());
    }

    ChannelInterceptor createChannelInterceptor() {
        return new ChannelInterceptor() {
            @Override
            public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
                StompHeaderAccessor accessor =
                        MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

                if (accessor != null) {
                    if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                        authenticateConnect(accessor);
                    } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
                        authorizeSubscribe(accessor);
                    } else if (StompCommand.SEND.equals(accessor.getCommand())) {
                        authorizeSend(accessor);
                    }
                }
                return message;
            }
        };
    }

    private void authenticateConnect(StompHeaderAccessor accessor) {
        String authHeader = accessor.getFirstNativeHeader("Authorization");
        if (authHeader == null || authHeader.isBlank()) {
            authHeader = accessor.getFirstNativeHeader("token");
        }

        if (authHeader != null && !authHeader.isBlank()) {
            String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
            try {
                String username = jwtService.extractUsername(token);
                if (username != null) {
                    UserDetails userDetails = customUserDetailsService.loadUserByUsername(username);
                    if (jwtService.isTokenValid(token, userDetails)) {
                        UsernamePasswordAuthenticationToken auth =
                                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                        accessor.setUser(auth);
                        log.debug("WebSocket STOMP connection authenticated for user: {}", username);
                        return;
                    }
                }
            } catch (Exception ex) {
                log.warn("WebSocket STOMP authentication failed: {}", ex.getMessage());
            }
        }
    }

    private void authorizeSubscribe(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        Principal principal = accessor.getUser();

        if (destination == null || destination.isBlank()) {
            throw new AccessDeniedException("Destination is required for subscription");
        }

        // Enforce strict rules on private user queue subscriptions
        if (destination.startsWith("/user/")) {
            if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
                log.warn("Unauthenticated attempt to subscribe to private destination: {}", destination);
                throw new AccessDeniedException("Authentication required to subscribe to private user destinations");
            }

            // Canonical pattern for private notifications: /user/queue/notifications
            if (destination.equals("/user/queue/notifications")) {
                return;
            }

            // If destination targets an explicit user prefix: /user/{id-or-email}/queue/notifications
            String username = principal.getName();
            String userEmailQueue = "/user/" + username + "/queue/notifications";
            if (destination.equals(userEmailQueue)) {
                return;
            }

            String userId = userRepository.findByEmail(username).map(User::getId).orElse(null);
            if (userId != null) {
                String userIdQueue = "/user/" + userId + "/queue/notifications";
                if (destination.equals(userIdQueue)) {
                    return;
                }
            }

            // Any other destination (including substring attempts or other user IDs) is rejected
            log.warn("Security Alert: User {} attempted unauthorized subscription to {}", username, destination);
            throw new AccessDeniedException("Unauthorized subscription to private user destination");
        }
    }

    private void authorizeSend(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination != null) {
            // Clients must never send directly to broker destinations or private user queues
            if (destination.startsWith("/topic") || destination.startsWith("/queue") || destination.startsWith("/user")) {
                log.warn("Security Alert: Unauthorized attempt by client to SEND directly to protected destination: {}", destination);
                throw new AccessDeniedException("Direct SEND to broker or private user destinations is forbidden");
            }
        }
    }
}
