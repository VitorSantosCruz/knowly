package br.com.conectabyte.knowly.tenancy;

import br.com.conectabyte.knowly.auth.User;
import br.com.conectabyte.knowly.auth.UserRepository;
import br.com.conectabyte.knowly.auth.exception.AuthenticatedUserNotFoundException;
import br.com.conectabyte.knowly.tenancy.dto.NotificationDto;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final UserRepository userRepository;

    public NotificationController(
            NotificationService notificationService, UserRepository userRepository) {
        this.notificationService = notificationService;
        this.userRepository = userRepository;
    }

    @GetMapping
    public ResponseEntity<List<NotificationDto>> listMine() {
        List<NotificationDto> notifications =
                notificationService.listMine(currentUser()).stream()
                        .map(NotificationDto::from)
                        .toList();

        return ResponseEntity.ok(notifications);
    }

    @PostMapping("/{id}/accept")
    public ResponseEntity<Void> accept(@PathVariable Long id) {
        notificationService.accept(currentUser(), id);

        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/decline")
    public ResponseEntity<Void> decline(@PathVariable Long id) {
        notificationService.decline(currentUser(), id);

        return ResponseEntity.ok().build();
    }

    private User currentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();

        return userRepository
                .findByEmailIgnoreCase(email)
                .orElseThrow(AuthenticatedUserNotFoundException::new);
    }
}
