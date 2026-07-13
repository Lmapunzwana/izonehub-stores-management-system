package com.izonehub.stores.notification;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationRepository repo;

    public NotificationController(NotificationRepository repo) {
        this.repo = repo;
    }

    /** Returns unread notifications for the current user. */
    @GetMapping
    public List<NotificationDtos.NotificationResponse> unread(
            @AuthenticationPrincipal String email) {
        return repo.findByReadFalse().stream()
                .filter(n -> n.getUser().getEmail().equals(email))
                .map(n -> new NotificationDtos.NotificationResponse(
                        n.getId(), n.getType(), n.getMessage(), n.isRead()))
                .toList();
    }

    /** Mark a single notification as read. */
    @PostMapping("/{id}/read")
    public NotificationDtos.NotificationResponse markRead(
            @PathVariable UUID id,
            @AuthenticationPrincipal String email) {
        Notification n = repo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!n.getUser().getEmail().equals(email))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        n.markRead();
        repo.save(n);
        return new NotificationDtos.NotificationResponse(n.getId(), n.getType(), n.getMessage(), n.isRead());
    }

    /** Mark all of the current user's notifications as read. */
    @PostMapping("/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markAllRead(@AuthenticationPrincipal String email) {
        repo.findByReadFalse().stream()
                .filter(n -> n.getUser().getEmail().equals(email))
                .forEach(n -> { n.markRead(); repo.save(n); });
    }
}
