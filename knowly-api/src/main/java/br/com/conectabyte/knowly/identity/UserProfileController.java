package br.com.conectabyte.knowly.identity;

import br.com.conectabyte.knowly.auth.User;
import br.com.conectabyte.knowly.auth.UserRepository;
import br.com.conectabyte.knowly.auth.exception.AuthenticatedUserNotFoundException;
import br.com.conectabyte.knowly.identity.dto.ContactChangeDto;
import br.com.conectabyte.knowly.identity.dto.MandatoryProfileFieldsDto;
import br.com.conectabyte.knowly.identity.dto.ProfileEditRequestDto;
import br.com.conectabyte.knowly.identity.dto.ProfileEditRequestFieldsDto;
import br.com.conectabyte.knowly.identity.dto.UserProfileDto;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * {@code /api/users/{me|id}/profile}, per specify/features/identity-profile-model-v2/PLAN.md's API
 * contracts table.
 */
@RestController
@RequestMapping("/api/users")
public class UserProfileController {

    private final UserProfileService userProfileService;
    private final ProfileEditRequestService profileEditRequestService;
    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;

    public UserProfileController(
            UserProfileService userProfileService,
            ProfileEditRequestService profileEditRequestService,
            UserRepository userRepository,
            UserProfileRepository userProfileRepository) {
        this.userProfileService = userProfileService;
        this.profileEditRequestService = profileEditRequestService;
        this.userRepository = userRepository;
        this.userProfileRepository = userProfileRepository;
    }

    @GetMapping("/me/profile")
    public ResponseEntity<UserProfileDto> getOwnProfile() {
        return ResponseEntity.ok(userProfileService.getOwnProfile(currentUser()));
    }

    @GetMapping("/{id}/profile")
    public ResponseEntity<UserProfileDto> getProfile(@PathVariable Long id) {
        return ResponseEntity.ok(userProfileService.getProfile(currentUser(), id));
    }

    @PutMapping("/{id}/profile")
    public ResponseEntity<UserProfileDto> directEdit(
            @PathVariable Long id, @RequestBody ProfileEditRequestFieldsDto body) {
        return ResponseEntity.ok(
                userProfileService.directEdit(
                        currentUser(), id, body.fields(), body.contactChanges()));
    }

    @PostMapping("/me/profile/avatar")
    public ResponseEntity<UserProfileDto> updateOwnAvatar(
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(userProfileService.updateOwnAvatar(currentUser(), file));
    }

    /** REQ-6: the bootstrap account's one-time, no-approval self-completion endpoint. */
    @PostMapping("/me/profile/complete")
    public ResponseEntity<UserProfileDto> completeOwnProfile(
            @Valid @RequestBody MandatoryProfileFieldsDto body) {
        return ResponseEntity.ok(userProfileService.completeOwnProfile(currentUser(), body));
    }

    @PostMapping("/me/profile/edit-requests")
    public ResponseEntity<ProfileEditRequestDto> submitEditRequest(
            @RequestBody ProfileEditRequestFieldsDto body) {
        ProfileEditRequest request =
                profileEditRequestService.submitEditRequest(currentUser(), body);

        return ResponseEntity.status(HttpStatus.CREATED).body(toDto(request));
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

        User requester = request.getRequester();
        String requesterName =
                userProfileRepository
                        .findById(requester.getId())
                        .map(UserProfile::getFullName)
                        .orElse(null);

        return new ProfileEditRequestDto(
                request.getId(),
                requester.getId(),
                requesterName,
                requester.getEmail(),
                profileEditRequestService.proposedFieldsOf(request),
                contactChanges,
                request.getStatus(),
                request.getCreatedAt());
    }

    private User currentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();

        return userRepository
                .findByEmailIgnoreCase(email)
                .orElseThrow(AuthenticatedUserNotFoundException::new);
    }
}
