package br.com.conectabyte.knowly.identity;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProfileEditRequestContactRepository
        extends JpaRepository<ProfileEditRequestContact, Long> {

    List<ProfileEditRequestContact> findByProfileEditRequest(ProfileEditRequest request);
}
