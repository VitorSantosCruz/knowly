package br.com.conectabyte.knowly.identity;

import br.com.conectabyte.knowly.auth.User;
import br.com.conectabyte.knowly.auth.UserRepository;
import br.com.conectabyte.knowly.identity.dto.ProfileEditRequestDto;
import br.com.conectabyte.knowly.identity.dto.ProfileFieldsDto;
import br.com.conectabyte.knowly.identity.dto.UserProfileDto;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code /api/users/{me|id}/profile}, per specify/features/identity-profile-model/PLAN.md's API
 * contracts table.
 */
@RestController
@RequestMapping("/api/users")
public class UserProfileController {

    private final UserProfileService userProfileService;
    private final ProfileEditRequestService profileEditRequestService;
    private final UserRepository userRepository;

    public UserProfileController(
            UserProfileService userProfileService,
            ProfileEditRequestService profileEditRequestService,
            UserRepository userRepository) {
        this.userProfileService = userProfileService;
        this.profileEditRequestService = profileEditRequestService;
        this.userRepository = userRepository;
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
            @PathVariable Long id, @RequestBody ProfileFieldsDto fields) {
        return ResponseEntity.ok(userProfileService.directEdit(currentUser(), id, fields));
    }

    @PostMapping("/me/profile/edit-requests")
    public ResponseEntity<ProfileEditRequestDto> submitEditRequest(
            @RequestBody ProfileFieldsDto fields) {
        ProfileEditRequest request =
                profileEditRequestService.submitEditRequest(currentUser(), fields);

        return ResponseEntity.status(HttpStatus.CREATED).body(toDto(request));
    }

    private ProfileEditRequestDto toDto(ProfileEditRequest request) {
        return new ProfileEditRequestDto(
                request.getId(),
                request.getRequester().getId(),
                new ProfileFieldsDto(
                        request.getProposedFullName(),
                        request.getProposedAddress(),
                        request.getProposedRg(),
                        request.getProposedCpf(),
                        request.getProposedPhone()),
                request.getStatus(),
                request.getCreatedAt());
    }

    private User currentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();

        return userRepository.findByEmailIgnoreCase(email).orElseThrow();
    }
}
