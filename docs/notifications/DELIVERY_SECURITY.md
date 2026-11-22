# Delivery Security & Access Control

## 1. REST Endpoint Security & Ownership Verification

All notification deletion and update operations strictly resolve the caller's identity via Spring Security's authenticated principal:

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

### Canonical Destination Enforcement
PulseCare enforces `/user/queue/notifications` as the canonical destination for private client subscriptions.
- When an authenticated user subscribes to `/user/queue/notifications`, Spring's `UserDestinationMessageHandler` translates the destination to the user's specific session.
- Subscribing to arbitrary user-targeted destinations (e.g. `/user/someoneElse/queue/notifications`) is strictly forbidden.
- Substring destination matching (`contains()`) was eliminated to prevent prefix/suffix domain spoofing.

### Interceptor Validation Rules
1. `destination.equals("/user/queue/notifications")`:
   Allowed ONLY if `principal != null`. Unauthenticated requests throw `AccessDeniedException`.
2. Explicit user destination `/user/{username}/queue/notifications`:
   Must strictly match `username == principal.getName()` or `userId == principal.userId` via exact equality.
3. Client `SEND` Protection:
   Clients are forbidden from issuing `SEND` frames to `/topic/**`, `/queue/**`, or `/user/**`. Only `/app/**` endpoints are permissible for client messages.

---

## 3. Email Security, Masking & Privacy

- **Email Masking**: In logs and error messages, recipient emails are masked (e.g., `j***@gmail.com`) to protect personal data from exposure in log aggregation platforms.
- **Error Sanitization**: Regex sanitization strips sensitive tokens, secrets, or passwords from `lastErrorMessage` before persisting to outbox records.
- **Fail-Closed SMTP**: When `app.mail.enabled` is `false`, outbox email items are marked `CANCELLED` with `SMTP_DISABLED`, explicitly preventing false claims of external delivery.
