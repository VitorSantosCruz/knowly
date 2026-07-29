package br.com.conectabyte.knowly.identity;

import br.com.conectabyte.knowly.auth.User;
import br.com.conectabyte.knowly.auth.UserRepository;
import br.com.conectabyte.knowly.identity.dto.ContactChangeDto;
import br.com.conectabyte.knowly.identity.dto.ProfileEditRequestDto;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code /api/profile-edit-requests}, per specify/features/identity-profile-model-v2/PLAN.md's API
 * contracts table.
 */
@RestController
@RequestMapping("/api/profile-edit-requests")
public class ProfileEditRequestController {

    private final ProfileEditRequestService profileEditRequestService;
    private final UserRepository userRepository;

    public ProfileEditRequestController(
            ProfileEditRequestService profileEditRequestService, UserRepository userRepository) {
        this.profileEditRequestService = profileEditRequestService;
        this.userRepository = userRepository;
    }

    @GetMapping
    public ResponseEntity<List<ProfileEditRequestDto>> listPending() {
        List<ProfileEditRequestDto> requests =
                profileEditRequestService.listPendingForApprover(currentUser()).stream()
                        .map(this::toDto)
                        .toList();

        return ResponseEntity.ok(requests);
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<Void> approve(@PathVariable Long id) {
        profileEditRequestService.approveEditRequest(currentUser(), id);

        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<Void> reject(@PathVariable Long id) {
        profileEditRequestService.rejectEditRequest(currentUser(), id);

        return ResponseEntity.ok().build();
    }

    private ProfileEditRequestDto toDto(ProfileEditRequest request) {
        List<ContactChangeDto> contactChanges =
                profileEditRequestService.proposedContactChangesOf(request).stream()
                        .map(
                                change ->
                                        new ContactChangeDto(
                                                change.getAction(),
                                                change.getContactId(),
                                                change.getType(),
                                                change.getValue(),
                                                change.getLabel(),
                                                change.getPrimary()))
                        .toList();

        return new ProfileEditRequestDto(
                request.getId(),
                request.getRequester().getId(),
                profileEditRequestService.proposedFieldsOf(request),
                contactChanges,
                request.getStatus(),
                request.getCreatedAt());
    }

    private User currentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();

        return userRepository.findByEmailIgnoreCase(email).orElseThrow();
    }
}
