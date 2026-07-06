package br.com.conectabyte.knowly.auth;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class OneTimePasswordService {

    private static final String ALPHABET =
            "23456789" + "ABCDEFGHJKLMNPQRSTUVWXYZ" + "abcdefghijkmnopqrstuvwxyz";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthProperties properties;
    private final SecureRandom random = new SecureRandom();
    private final String dummyHash;

    public OneTimePasswordService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AuthProperties properties) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
        this.dummyHash = passwordEncoder.encode("dummy-constant-for-timing-safety");
    }

    public String generateFor(User user) {
        String password = generateRandomPassword(properties.oneTimePassword().length());

        user.setOneTimePasswordHash(passwordEncoder.encode(password));
        user.setOneTimePasswordIssuedAt(Instant.now());
        userRepository.save(user);

        return password;
    }

    public boolean hasValidPassword(User user) {
        return user.getOneTimePasswordHash() != null
                && user.getOneTimePasswordIssuedAt() != null
                && Duration.between(user.getOneTimePasswordIssuedAt(), Instant.now())
                                .compareTo(properties.oneTimePassword().ttl())
                        < 0;
    }

    public Optional<String> verifyAndRotate(User user, String password) {
        boolean valid = user != null && hasValidPassword(user);
        String hashToCheck = valid ? user.getOneTimePasswordHash() : dummyHash;

        // Always compare — real or dummy — so response time doesn't reveal whether the
        // account/password exists.
        boolean matches = passwordEncoder.matches(password, hashToCheck);

        if (!valid || !matches) {
            return Optional.empty();
        }

        return Optional.of(generateFor(user));
    }

    private String generateRandomPassword(int length) {
        StringBuilder password = new StringBuilder(length);

        for (int i = 0; i < length; i++) {
            password.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }

        return password.toString();
    }
}
