package com.hospital.backend.security;

import com.hospital.backend.controller.NotificationController;
import com.hospital.backend.model.Notification;
import com.hospital.backend.repository.NotificationRepository;
import com.hospital.backend.service.NotificationService;
import com.hospital.backend.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;

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
    private Authentication authentication;

    @InjectMocks
    private NotificationController notificationController;

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
}
