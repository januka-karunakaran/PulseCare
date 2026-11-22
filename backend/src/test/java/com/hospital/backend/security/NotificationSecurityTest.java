package com.hospital.backend.security;

import com.hospital.backend.config.WebSocketConfig;
import com.hospital.backend.controller.NotificationController;
import com.hospital.backend.model.Notification;
import com.hospital.backend.model.User;
import com.hospital.backend.repository.NotificationRepository;
import com.hospital.backend.repository.UserRepository;
import com.hospital.backend.service.NotificationService;
import com.hospital.backend.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationSecurityTest {

    @Mock
    private NotificationService notificationService;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private UserService userService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtService jwtService;

    @Mock
    private CustomUserDetailsService customUserDetailsService;

    @Mock
    private Authentication authentication;

    @Mock
    private MessageChannel messageChannel;

    @InjectMocks
    private NotificationController notificationController;

    private ChannelInterceptor webSocketInterceptor;

    @BeforeEach
    void setUpWebSocket() {
        WebSocketConfig webSocketConfig = new WebSocketConfig(jwtService, customUserDetailsService, userRepository);
        this.webSocketInterceptor = (ChannelInterceptor) org.springframework.test.util.ReflectionTestUtils.invokeMethod(
                webSocketConfig, "createChannelInterceptor"
        );
    }

    // ==========================================
    // REST Access Control Tests
    // ==========================================

    @Test
    @DisplayName("Should successfully delete notification when authenticated user is the owner")
    void testDeleteNotificationByOwnerSucceeds() {
        when(authentication.getName()).thenReturn("owner@example.com");
        when(userService.getUserIdByEmail("owner@example.com")).thenReturn("user-owner-123");

        ResponseEntity<Map<String, String>> response = notificationController.deleteNotification("notif-1", authentication);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("message", "Notification deleted");
        verify(notificationService).deleteNotification("notif-1", "user-owner-123");
    }

    @Test
    @DisplayName("Should reject deleting notification when service detects non-owner (AccessDenied)")
    void testDeleteNotificationByNonOwnerThrowsAccessDenied() {
        when(authentication.getName()).thenReturn("attacker@example.com");
        when(userService.getUserIdByEmail("attacker@example.com")).thenReturn("user-attacker-999");

        doThrow(new AccessDeniedException("You are not authorized to delete this notification"))
                .when(notificationService).deleteNotification("notif-1", "user-attacker-999");

        assertThatThrownBy(() -> notificationController.deleteNotification("notif-1", authentication))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("You are not authorized");
    }

    @Test
    @DisplayName("Should reject markAsRead when authenticated user does not own the notification")
    void testMarkAsReadNonOwnerThrowsAccessDenied() {
        when(authentication.getName()).thenReturn("intruder@example.com");
        when(userService.getUserIdByEmail("intruder@example.com")).thenReturn("intruder-id");

        Notification notification = Notification.builder()
                .id("notif-5")
                .userId("victim-id")
                .title("Confidential Test Result")
                .build();

        when(notificationRepository.findById("notif-5")).thenReturn(Optional.of(notification));

        assertThatThrownBy(() -> notificationController.markAsRead("notif-5", authentication))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("You are not authorized");

        verify(notificationService, never()).markAsRead(anyString());
    }

    @Test
    @DisplayName("Should allow markAsRead when authenticated user owns the notification")
    void testMarkAsReadOwnerSucceeds() {
        when(authentication.getName()).thenReturn("owner@example.com");
        when(userService.getUserIdByEmail("owner@example.com")).thenReturn("owner-id");

        Notification notification = Notification.builder()
                .id("notif-6")
                .userId("owner-id")
                .title("Your Appointment is Confirmed")
                .build();

        when(notificationRepository.findById("notif-6")).thenReturn(Optional.of(notification));

        ResponseEntity<Map<String, String>> response = notificationController.markAsRead("notif-6", authentication);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(notificationService).markAsRead("notif-6");
    }

    // ==========================================
    // STOMP WebSocket Destination Security Tests
    // ==========================================

    @Test
    @DisplayName("Authenticated user subscribing to canonical /user/queue/notifications should succeed")
    void testWebSocketSubscribeCanonicalQueueSucceeds() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/user/queue/notifications");
        Principal principal = new UsernamePasswordAuthenticationToken("patient@hospital.com", null, List.of());
        accessor.setUser(principal);

        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        Message<?> result = webSocketInterceptor.preSend(message, messageChannel);

        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("Unauthenticated user subscribing to /user/queue/notifications should be rejected")
    void testWebSocketSubscribeUnauthenticatedThrowsAccessDenied() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/user/queue/notifications");
        // No principal set

        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        assertThatThrownBy(() -> webSocketInterceptor.preSend(message, messageChannel))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Authentication required");
    }

    @Test
    @DisplayName("User subscribing to another user's private queue should be rejected")
    void testWebSocketSubscribeAnotherUserQueueThrowsAccessDenied() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/user/victim@hospital.com/queue/notifications");
        Principal principal = new UsernamePasswordAuthenticationToken("attacker@hospital.com", null, List.of());
        accessor.setUser(principal);

        when(userRepository.findByEmail("attacker@hospital.com")).thenReturn(Optional.of(User.builder().id("attacker-id").build()));

        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        assertThatThrownBy(() -> webSocketInterceptor.preSend(message, messageChannel))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Unauthorized subscription to private user destination");
    }

    @Test
    @DisplayName("Substring destination match attempts should be strictly rejected")
    void testWebSocketSubscribeSubstringUserThrowsAccessDenied() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        // Attacker appends domain to spoof substring match
        accessor.setDestination("/user/attacker@hospital.com.evil.com/queue/notifications");
        Principal principal = new UsernamePasswordAuthenticationToken("attacker@hospital.com", null, List.of());
        accessor.setUser(principal);

        when(userRepository.findByEmail("attacker@hospital.com")).thenReturn(Optional.of(User.builder().id("attacker-id").build()));

        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        assertThatThrownBy(() -> webSocketInterceptor.preSend(message, messageChannel))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Unauthorized subscription to private user destination");
    }

    @Test
    @DisplayName("Client SEND to protected broker destination (/topic or /queue) should be rejected")
    void testWebSocketClientSendToProtectedBrokerThrowsAccessDenied() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setDestination("/topic/emergency-alerts");
        Principal principal = new UsernamePasswordAuthenticationToken("patient@hospital.com", null, List.of());
        accessor.setUser(principal);

        Message<?> message = MessageBuilder.createMessage("Fake Emergency".getBytes(), accessor.getMessageHeaders());
        assertThatThrownBy(() -> webSocketInterceptor.preSend(message, messageChannel))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Direct SEND to broker or private user destinations is forbidden");
    }

    @Test
    @DisplayName("Client SEND to private user destination (/user/**) should be rejected")
    void testWebSocketClientSendToUserDestinationThrowsAccessDenied() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setDestination("/user/queue/notifications");
        Principal principal = new UsernamePasswordAuthenticationToken("patient@hospital.com", null, List.of());
        accessor.setUser(principal);

        Message<?> message = MessageBuilder.createMessage("Fake Notification".getBytes(), accessor.getMessageHeaders());
        assertThatThrownBy(() -> webSocketInterceptor.preSend(message, messageChannel))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Direct SEND to broker or private user destinations is forbidden");
    }
}
