# Delivery Security & Access Control

## 1. REST Endpoint Security & Ownership Verification

Prior to Feature 02.3, `NotificationController.deleteNotification(id)` allowed any authenticated user to delete notifications belonging to other patients or doctors simply by guessing or passing the notification ID.

### Ownership Enforcement
All notification deletion and update operations now strictly resolve the caller's identity via Spring Security's authenticated principal:

```java
@DeleteMapping("/{notificationId}")
@PreAuthorize("isAuthenticated()")
public ResponseEntity<Map<String, String>> deleteNotification(
        @PathVariable String notificationId,
        Authentication authentication
) {
    String currentUserId = userService.getUserIdByEmail(authentication.getName());
    notificationService.deleteNotification(notificationId, currentUserId);
    return ResponseEntity.ok(Map.of("message", "Notification deleted"));
}
```

In `NotificationServiceImpl`, if the target notification does not exist, a 404 is thrown. If the caller's ID does not match `notification.getUserId()`, an `AccessDeniedException` (HTTP 403) is raised, completely preventing cross-tenant information manipulation.

---

## 2. WebSocket & STOMP Security

### Principal Alignment
Spring Security's JWT authentication sets `UserDetails.getUsername()` (user's email address) as the STOMP `Principal.getName()`. If notifications are broadcast exclusively to `userId`, STOMP user session registries fail to route messages.

PulseCare implements dual-dispatch in `NotificationServiceImpl` and `OutboxProcessor`:
1. Sends to `convertAndSendToUser(userId, "/queue/notifications", dto)`
2. Sends to `convertAndSendToUser(userEmail, "/queue/notifications", dto)`

### Channel Interception & Broadcast Protection
In `WebSocketConfig`, an inbound channel interceptor inspects every frame:
- Validates that users can only subscribe to `/user/queue/notifications` corresponding to their own session.
- Strictly blocks any client `SEND` frame targeting broker destinations (`/topic`, `/queue`), preventing malicious clients from injecting fake alerts or spoofing hospital staff.

---

## 3. Email Security & Anti-Phishing Safeguards

- Branded headers and footers standardizing on PulseCare HMS.
- Configurable `app.frontend.url` prevents host-header injection or hardcoded localhost URLs in production emails.
- SMTP credentials managed exclusively through environment variables.
- Mail dispatch failures are captured in outbox error metadata without logging sensitive credentials or tokens.
