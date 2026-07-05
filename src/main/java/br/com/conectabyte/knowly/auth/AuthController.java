package br.com.conectabyte.knowly.auth;

import br.com.conectabyte.knowly.auth.dto.LoginRequestDto;
import br.com.conectabyte.knowly.auth.dto.VerifyCodeRequestDto;
import br.com.conectabyte.knowly.auth.dto.VerifyPasswordRequestDto;
import br.com.conectabyte.knowly.auth.exception.AccountLockedException;
import br.com.conectabyte.knowly.auth.exception.CaptchaRequiredException;
import br.com.conectabyte.knowly.auth.exception.InvalidCredentialsException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final UserRepository userRepository;
    private final LoginCodeService loginCodeService;
    private final OneTimePasswordService oneTimePasswordService;
    private final FailedAttemptService failedAttemptService;
    private final MailService mailService;
    private final CaptchaService captchaService;

    public AuthController(
            UserRepository userRepository,
            LoginCodeService loginCodeService,
            OneTimePasswordService oneTimePasswordService,
            FailedAttemptService failedAttemptService,
            MailService mailService,
            CaptchaService captchaService) {
        this.userRepository = userRepository;
        this.loginCodeService = loginCodeService;
        this.oneTimePasswordService = oneTimePasswordService;
        this.failedAttemptService = failedAttemptService;
        this.mailService = mailService;
        this.captchaService = captchaService;
    }

    @PostMapping("/login-request")
    public ResponseEntity<Void> requestLogin(
            @Valid @RequestBody LoginRequestDto request, HttpServletRequest httpRequest) {
        boolean velocityExceeded =
                captchaService.recordRequestAndIsVelocityExceeded(httpRequest.getRemoteAddr());

        if (velocityExceeded && !captchaService.verify(request.captchaToken())) {
            log.warn("auth.login_request email={} outcome=captcha_required", request.email());
            throw new CaptchaRequiredException();
        }

        boolean accountExists =
                userRepository
                        .findByEmailIgnoreCase(request.email())
                        .map(
                                user -> {
                                    String code = loginCodeService.generate(user.getEmail());
                                    mailService.sendLoginCode(user.getEmail(), code);
                                    return true;
                                })
                        .orElse(false);

        log.info("auth.login_request email={} accountExists={}", request.email(), accountExists);

        return ResponseEntity.ok().build();
    }

    @PostMapping("/login-code/verify")
    public ResponseEntity<Void> verifyCode(
            @Valid @RequestBody VerifyCodeRequestDto request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        if (failedAttemptService.isLocked(request.email())) {
            log.warn("auth.login_code_verify email={} outcome=locked", request.email());
            throw new AccountLockedException();
        }

        if (!loginCodeService.verify(request.email(), request.code())) {
            failedAttemptService.recordFailure(request.email());
            log.warn("auth.login_code_verify email={} outcome=invalid_code", request.email());
            throw new InvalidCredentialsException();
        }

        failedAttemptService.recordSuccess(request.email());

        userRepository
                .findByEmailIgnoreCase(request.email())
                .ifPresent(
                        user -> {
                            if (!oneTimePasswordService.hasValidPassword(user)) {
                                String newPassword = oneTimePasswordService.generateFor(user);
                                mailService.sendNewOneTimePassword(user.getEmail(), newPassword);
                            }
                        });

        establishSession(request.email(), httpRequest, httpResponse);
        log.info("auth.login_code_verify email={} outcome=success", request.email());

        return ResponseEntity.ok().build();
    }

    @PostMapping("/login-password/verify")
    public ResponseEntity<Void> verifyPassword(
            @Valid @RequestBody VerifyPasswordRequestDto request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        if (failedAttemptService.isLocked(request.email())) {
            log.warn("auth.login_password_verify email={} outcome=locked", request.email());
            throw new AccountLockedException();
        }

        Optional<String> newPassword =
                userRepository
                        .findByEmailIgnoreCase(request.email())
                        .flatMap(
                                user ->
                                        oneTimePasswordService.verifyAndRotate(
                                                user, request.password()));

        if (newPassword.isEmpty()) {
            failedAttemptService.recordFailure(request.email());
            log.warn(
                    "auth.login_password_verify email={} outcome=invalid_password",
                    request.email());
            throw new InvalidCredentialsException();
        }

        failedAttemptService.recordSuccess(request.email());
        mailService.sendNewOneTimePassword(request.email(), newPassword.get());
        establishSession(request.email(), httpRequest, httpResponse);
        log.info("auth.login_password_verify email={} outcome=success", request.email());

        return ResponseEntity.ok().build();
    }

    private void establishSession(
            String email, HttpServletRequest request, HttpServletResponse response) {
        var authentication = new UsernamePasswordAuthenticationToken(email, null, List.of());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        new HttpSessionSecurityContextRepository().saveContext(context, request, response);
    }
}
