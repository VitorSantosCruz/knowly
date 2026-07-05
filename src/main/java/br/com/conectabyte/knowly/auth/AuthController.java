package br.com.conectabyte.knowly.auth;

import br.com.conectabyte.knowly.auth.dto.LoginRequestDto;
import br.com.conectabyte.knowly.auth.exception.CaptchaRequiredException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final LoginCodeService loginCodeService;
    private final MailService mailService;
    private final CaptchaService captchaService;

    public AuthController(
            UserRepository userRepository,
            LoginCodeService loginCodeService,
            MailService mailService,
            CaptchaService captchaService) {
        this.userRepository = userRepository;
        this.loginCodeService = loginCodeService;
        this.mailService = mailService;
        this.captchaService = captchaService;
    }

    @PostMapping("/login-request")
    public ResponseEntity<Void> requestLogin(
            @Valid @RequestBody LoginRequestDto request, HttpServletRequest httpRequest) {
        boolean velocityExceeded =
                captchaService.recordRequestAndIsVelocityExceeded(httpRequest.getRemoteAddr());

        if (velocityExceeded && !captchaService.verify(request.captchaToken())) {
            throw new CaptchaRequiredException();
        }

        userRepository
                .findByEmailIgnoreCase(request.email())
                .ifPresent(
                        user -> {
                            String code = loginCodeService.generate(user.getEmail());
                            mailService.sendLoginCode(user.getEmail(), code);
                        });

        return ResponseEntity.ok().build();
    }
}
