package br.com.conectabyte.knowly.auth;

import br.com.conectabyte.knowly.audit.AuditEvent;
import br.com.conectabyte.knowly.audit.AuditEventRepository;
import br.com.conectabyte.knowly.audit.AuditLog;
import br.com.conectabyte.knowly.audit.AuditOutcome;
import br.com.conectabyte.knowly.auth.dto.LoginRequestDto;
import br.com.conectabyte.knowly.auth.dto.VerifyCodeRequestDto;
import br.com.conectabyte.knowly.auth.dto.VerifyPasswordRequestDto;
import br.com.conectabyte.knowly.auth.exception.AccountLockedException;
import br.com.conectabyte.knowly.auth.exception.CaptchaRequiredException;
import br.com.conectabyte.knowly.auth.exception.InvalidCredentialsException;
import br.com.conectabyte.knowly.observability.PiiMasker;
import br.com.conectabyte.knowly.tenancy.GlobalRole;
import br.com.conectabyte.knowly.tenancy.PermissionService;
import br.com.conectabyte.knowly.tenancy.TenantAuthorityFactory;
import br.com.conectabyte.knowly.tenancy.TenantService;
import br.com.conectabyte.knowly.tenancy.TenantSessionKeys;
import br.com.conectabyte.knowly.tenancy.TenantSessionOutcome;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
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
    private final LoginRequestPublisher loginRequestPublisher;
    private final LoginRequestThrottleService loginRequestThrottleService;
    private final AuthProperties properties;
    private final TenantService tenantService;
    private final PermissionService permissionService;
    private final AuditEventRepository auditEventRepository;

    public AuthController(
            UserRepository userRepository,
            LoginCodeService loginCodeService,
            OneTimePasswordService oneTimePasswordService,
            FailedAttemptService failedAttemptService,
            MailService mailService,
            CaptchaService captchaService,
            LoginRequestPublisher loginRequestPublisher,
            LoginRequestThrottleService loginRequestThrottleService,
            AuthProperties properties,
            TenantService tenantService,
            PermissionService permissionService,
            AuditEventRepository auditEventRepository) {
        this.userRepository = userRepository;
        this.loginCodeService = loginCodeService;
        this.oneTimePasswordService = oneTimePasswordService;
        this.failedAttemptService = failedAttemptService;
        this.mailService = mailService;
        this.captchaService = captchaService;
        this.loginRequestPublisher = loginRequestPublisher;
        this.loginRequestThrottleService = loginRequestThrottleService;
        this.properties = properties;
        this.tenantService = tenantService;
        this.permissionService = permissionService;
        this.auditEventRepository = auditEventRepository;
    }

    @AuditLog(
            action = "auth.login_request",
            resourceType = "auth-email",
            resourceIdExpression =
                    "T(br.com.conectabyte.knowly.observability.PiiMasker).maskEmail(#request.email())",
            captureSourceIp = true)
    @PostMapping("/login-request")
    public ResponseEntity<Void> requestLogin(
            @Valid @RequestBody LoginRequestDto request, HttpServletRequest httpRequest) {
        boolean velocityExceeded =
                captchaService.recordRequestAndIsVelocityExceeded(
                        httpRequest.getRemoteAddr(),
                        "login-request",
                        properties.captcha().velocityThreshold());

        if (velocityExceeded && !captchaService.verify(request.captchaToken())) {
            log.warn(
                    "auth.login_request email={} outcome=captcha_required",
                    PiiMasker.maskEmail(request.email()));
            throw new CaptchaRequiredException();
        }

        if (!failedAttemptService.isLocked(request.email())
                && !loginRequestThrottleService.isInCooldown(request.email())) {
            loginRequestThrottleService.recordRequest(request.email());
            loginRequestPublisher.publish(request.email());
        }

        return ResponseEntity.ok().build();
    }

    @AuditLog(
            action = "auth.login_code_verify",
            resourceType = "auth-email",
            resourceIdExpression =
                    "T(br.com.conectabyte.knowly.observability.PiiMasker).maskEmail(#request.email())",
            captureSourceIp = true)
    @PostMapping("/login-code/verify")
    public ResponseEntity<Void> verifyCode(
            @Valid @RequestBody VerifyCodeRequestDto request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        loginRequestThrottleService.recordVerifyAttempt(request.email());

        boolean velocityExceeded =
                captchaService.recordRequestAndIsVelocityExceeded(
                        httpRequest.getRemoteAddr(),
                        "login-code-verify",
                        properties.captcha().verifyVelocityThreshold());

        if (velocityExceeded && !captchaService.verify(request.captchaToken())) {
            log.warn(
                    "auth.login_code_verify email={} outcome=captcha_required",
                    PiiMasker.maskEmail(request.email()));
            throw new CaptchaRequiredException();
        }

        if (failedAttemptService.isLocked(request.email())) {
            log.warn(
                    "auth.login_code_verify email={} outcome=locked",
                    PiiMasker.maskEmail(request.email()));
            throw new AccountLockedException();
        }

        if (!loginCodeService.verify(request.email(), request.code())) {
            if (failedAttemptService.recordFailure(request.email())) {
                recordLockoutEvent(request.email(), httpRequest);
            }
            log.warn(
                    "auth.login_code_verify email={} outcome=invalid_code",
                    PiiMasker.maskEmail(request.email()));
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
        log.info(
                "auth.login_code_verify email={} outcome=success",
                PiiMasker.maskEmail(request.email()));

        return ResponseEntity.ok().build();
    }

    @AuditLog(
            action = "auth.login_password_verify",
            resourceType = "auth-email",
            resourceIdExpression =
                    "T(br.com.conectabyte.knowly.observability.PiiMasker).maskEmail(#request.email())",
            captureSourceIp = true)
    @PostMapping("/login-password/verify")
    public ResponseEntity<Void> verifyPassword(
            @Valid @RequestBody VerifyPasswordRequestDto request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        loginRequestThrottleService.recordVerifyAttempt(request.email());

        boolean velocityExceeded =
                captchaService.recordRequestAndIsVelocityExceeded(
                        httpRequest.getRemoteAddr(),
                        "login-password-verify",
                        properties.captcha().verifyVelocityThreshold());

        if (velocityExceeded && !captchaService.verify(request.captchaToken())) {
            log.warn(
                    "auth.login_password_verify email={} outcome=captcha_required",
                    PiiMasker.maskEmail(request.email()));
            throw new CaptchaRequiredException();
        }

        if (failedAttemptService.isLocked(request.email())) {
            log.warn(
                    "auth.login_password_verify email={} outcome=locked",
                    PiiMasker.maskEmail(request.email()));
            throw new AccountLockedException();
        }

        User user = userRepository.findByEmailIgnoreCase(request.email()).orElse(null);
        Optional<String> newPassword =
                oneTimePasswordService.verifyAndRotate(user, request.password());

        if (newPassword.isEmpty()) {
            if (failedAttemptService.recordFailure(request.email())) {
                recordLockoutEvent(request.email(), httpRequest);
            }
            log.warn(
                    "auth.login_password_verify email={} outcome=invalid_password",
                    PiiMasker.maskEmail(request.email()));
            throw new InvalidCredentialsException();
        }

        failedAttemptService.recordSuccess(request.email());
        mailService.sendNewOneTimePassword(request.email(), newPassword.get());
        establishSession(request.email(), httpRequest, httpResponse);
        log.info(
                "auth.login_password_verify email={} outcome=success",
                PiiMasker.maskEmail(request.email()));

        return ResponseEntity.ok().build();
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        Long actorUserId =
                userRepository
                        .findByEmailIgnoreCase(
                                SecurityContextHolder.getContext().getAuthentication().getName())
                        .map(User::getId)
                        .orElse(null);

        AuditEvent event =
                new AuditEvent(actorUserId, null, "auth.logout", null, null, AuditOutcome.SUCCESS);
        event.setMetadata(resolveSourceIpMetadata(request));
        auditEventRepository.save(event);

        new SecurityContextLogoutHandler()
                .logout(request, response, SecurityContextHolder.getContext().getAuthentication());
        return ResponseEntity.ok().build();
    }

    private void recordLockoutEvent(String email, HttpServletRequest request) {
        AuditEvent event =
                new AuditEvent(
                        null,
                        null,
                        "auth.login.lockout",
                        "auth-email",
                        PiiMasker.maskEmail(email),
                        AuditOutcome.DENIED);
        event.setMetadata(resolveSourceIpMetadata(request));
        auditEventRepository.save(event);
    }

    private String resolveSourceIpMetadata(HttpServletRequest request) {
        String maskedIp = PiiMasker.maskIp(request.getRemoteAddr());
        return maskedIp.isEmpty() ? null : "{\"sourceIp\": \"" + maskedIp + "\"}";
    }

    private void establishSession(
            String email, HttpServletRequest request, HttpServletResponse response) {
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }

        User user = userRepository.findByEmailIgnoreCase(email).orElse(null);
        List<GrantedAuthority> authorities = List.of();
        TenantSessionOutcome outcome =
                user == null
                        ? new TenantSessionOutcome.SelectionPending()
                        : tenantService.resolveSessionOutcome(user);

        if (outcome instanceof TenantSessionOutcome.Staff) {
            authorities = TenantAuthorityFactory.forStaff(user.getGlobalRole());
        } else if (outcome instanceof TenantSessionOutcome.AutoSelected autoSelected) {
            var membership = tenantService.requireActiveMembership(user, autoSelected.tenantId());
            authorities =
                    TenantAuthorityFactory.forMembership(
                            membership, permissionService.effectivePermissions(membership));
        }

        var authentication = new UsernamePasswordAuthenticationToken(email, null, authorities);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        new HttpSessionSecurityContextRepository().saveContext(context, request, response);

        HttpSession session = request.getSession(true);

        if (outcome instanceof TenantSessionOutcome.Staff) {
            session.setAttribute(TenantSessionKeys.STAFF, true);
            session.setAttribute(
                    TenantSessionKeys.STAFF_ADMIN, user.getGlobalRole() == GlobalRole.STAFF_ADMIN);
        } else if (outcome instanceof TenantSessionOutcome.AutoSelected autoSelected) {
            session.setAttribute(TenantSessionKeys.ACTIVE_TENANT_ID, autoSelected.tenantId());
        } else {
            session.setAttribute(TenantSessionKeys.SELECTION_PENDING, true);
        }
    }
}
