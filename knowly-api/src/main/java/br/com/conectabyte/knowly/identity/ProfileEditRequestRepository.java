package br.com.conectabyte.knowly.identity;

import br.com.conectabyte.knowly.auth.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProfileEditRequestRepository extends JpaRepository<ProfileEditRequest, Long> {

    Optional<ProfileEditRequest> findByRequesterAndStatus(
            User requester, ProfileEditRequestStatus status);

    List<ProfileEditRequest> findByStatus(ProfileEditRequestStatus status);
}
