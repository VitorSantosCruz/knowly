package br.com.conectabyte.knowly.onboarding;

import br.com.conectabyte.knowly.audit.AuditLog;
import br.com.conectabyte.knowly.auth.User;
import br.com.conectabyte.knowly.auth.UserRepository;
import br.com.conectabyte.knowly.auth.exception.AuthenticatedUserNotFoundException;
import java.time.Instant;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users/me")
public class OnboardingController {

    private final UserRepository userRepository;

    public OnboardingController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/onboarding-status")
    @AuditLog(action = "onboarding.status.view", resourceType = "User")
    public ResponseEntity<OnboardingStatusDto> getStatus() {
        User user = currentUser();

        return ResponseEntity.ok(new OnboardingStatusDto(user.getOnboardingCompletedAt() != null));
    }

    @PostMapping("/onboarding-complete")
    @AuditLog(action = "onboarding.complete", resourceType = "User")
    public ResponseEntity<Void> markComplete() {
        User user = currentUser();
        user.setOnboardingCompletedAt(Instant.now());
        userRepository.save(user);

        return ResponseEntity.ok().build();
    }

    private User currentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();

        return userRepository
                .findByEmailIgnoreCase(email)
                .orElseThrow(AuthenticatedUserNotFoundException::new);
    }
}
