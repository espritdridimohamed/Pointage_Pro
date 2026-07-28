package com.pointagepro.auth;

import com.pointagepro.auth.dto.*;
import com.pointagepro.shared.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    private final UserRepository userRepository;
    private final UserSessionRepository sessionRepository;
    private final LoginHistoryRepository loginHistoryRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthController(AuthService authService, UserRepository userRepository,
                          UserSessionRepository sessionRepository,
                          LoginHistoryRepository loginHistoryRepository,
                          PasswordEncoder passwordEncoder) {
        this.authService = authService;
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.loginHistoryRepository = loginHistoryRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request,
                                                           HttpServletRequest httpRequest) {
        String ip = getClientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");
        AuthResponse response = authService.login(request, ip, userAgent);
        return ResponseEntity.ok(ApiResponse.success("Login successful", response));
    }

    @PostMapping("/login/2fa")
    public ResponseEntity<ApiResponse<AuthResponse>> login2FA(@Valid @RequestBody TwoFactorLoginRequest request,
                                                               HttpServletRequest httpRequest) {
        String ip = getClientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");
        AuthResponse response = authService.completeTwoFactorLogin(request, ip, userAgent);
        return ResponseEntity.ok(ApiResponse.success("Login 2FA successful", response));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<String>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request.getEmail());
        return ResponseEntity.ok(ApiResponse.success("Si un compte existe avec cet email, un code de réinitialisation a été envoyé"));
    }

    @PostMapping("/verify-reset-code")
    public ResponseEntity<ApiResponse<String>> verifyResetCode(@Valid @RequestBody VerifyResetCodeRequest request) {
        boolean valid = authService.verifyResetCode(request.getEmail(), request.getCode());
        if (!valid) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Code invalide ou expiré"));
        }
        return ResponseEntity.ok(ApiResponse.success("Code vérifié"));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<String>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request.getEmail(), request.getCode(), request.getNewPassword());
        return ResponseEntity.ok(ApiResponse.success("Mot de passe réinitialisé avec succès"));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getProfile() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(401).body(ApiResponse.error("Not authenticated"));
        }

        String username = auth.getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        UserProfileResponse profile = new UserProfileResponse(
                user.getId(),
                user.getUsername(),
                user.getFullName(),
                user.getEmail(),
                user.getPhone(),
                user.getRole().name()
        );

        return ResponseEntity.ok(ApiResponse.success("Profile retrieved", profile));
    }

    @PutMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(401).body(ApiResponse.error("Not authenticated"));
        }

        String username = auth.getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setFullName(request.getFullName());
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        userRepository.save(user);

        UserProfileResponse profile = new UserProfileResponse(
                user.getId(),
                user.getUsername(),
                user.getFullName(),
                user.getEmail(),
                user.getPhone(),
                user.getRole().name()
        );

        return ResponseEntity.ok(ApiResponse.success("Profile updated successfully", profile));
    }

    @PostMapping("/change-password")
    public ResponseEntity<ApiResponse<String>> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(401).body(ApiResponse.error("Not authenticated"));
        }

        String username = auth.getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Current password is incorrect"));
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        return ResponseEntity.ok(ApiResponse.success("Password changed successfully"));
    }

    @PostMapping("/2fa/setup")
    public ResponseEntity<ApiResponse<TwoFactorSetupResponse>> setup2FA() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        TwoFactorSetupResponse response = authService.generateTwoFactorSetup(username);
        return ResponseEntity.ok(ApiResponse.success("2FA setup", response));
    }

    @PostMapping("/2fa/verify")
    public ResponseEntity<ApiResponse<String>> verify2FA(@Valid @RequestBody TwoFactorVerifyRequest request) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        boolean valid = authService.verifyTwoFactorCode(username, request.getCode());
        if (!valid) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Code invalide"));
        }
        return ResponseEntity.ok(ApiResponse.success("Code vérifié"));
    }

    @PostMapping("/2fa/enable")
    public ResponseEntity<ApiResponse<String>> enable2FA() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        authService.enableTwoFactor(username);
        return ResponseEntity.ok(ApiResponse.success("2FA activé"));
    }

    @PostMapping("/2fa/disable")
    public ResponseEntity<ApiResponse<String>> disable2FA(@Valid @RequestBody TwoFactorDisableRequest request) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        authService.disableTwoFactor(username, request.getPassword(), request.getCode());
        return ResponseEntity.ok(ApiResponse.success("2FA désactivé"));
    }

    @GetMapping("/sessions")
    public ResponseEntity<ApiResponse<List<SessionResponse>>> getSessions() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        String currentTokenHash = "";
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth.getCredentials() != null) {
            currentTokenHash = auth.getCredentials().toString();
        }

        String finalCurrentTokenHash = currentTokenHash;
        List<SessionResponse> sessions = sessionRepository.findByUserIdAndRevokedFalseOrderByCreatedAtDesc(user.getId())
                .stream()
                .filter(s -> !"2FA-temp".equals(s.getDeviceInfo()))
                .map(s -> {
                    SessionResponse resp = new SessionResponse();
                    resp.setId(s.getId());
                    resp.setDeviceInfo(s.getDeviceInfo());
                    resp.setIpAddress(s.getIpAddress());
                    resp.setCreatedAt(s.getCreatedAt());
                    resp.setLastAccessedAt(s.getLastAccessedAt());
                    resp.setCurrent(s.getTokenHash().equals(finalCurrentTokenHash));
                    return resp;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success("Sessions retrieved", sessions));
    }

    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<ApiResponse<String>> revokeSession(@PathVariable Long sessionId) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        UserSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Session not found"));

        if (!session.getUserId().equals(user.getId())) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Session non autorisée"));
        }

        session.setRevoked(true);
        sessionRepository.save(session);
        return ResponseEntity.ok(ApiResponse.success("Session révoquée"));
    }

    @DeleteMapping("/sessions")
    public ResponseEntity<ApiResponse<String>> revokeAllSessions() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        authService.revokeAllSessions(user.getId());
        return ResponseEntity.ok(ApiResponse.success("Toutes les sessions ont été révoquées"));
    }

    @GetMapping("/login-history")
    public ResponseEntity<ApiResponse<List<LoginHistoryResponse>>> getLoginHistory() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        List<LoginHistoryResponse> history = loginHistoryRepository.findTop20ByUserIdOrderByAttemptedAtDesc(user.getId())
                .stream()
                .map(h -> {
                    LoginHistoryResponse resp = new LoginHistoryResponse();
                    resp.setId(h.getId());
                    resp.setIpAddress(h.getIpAddress());
                    resp.setUserAgent(h.getUserAgent());
                    resp.setStatus(h.getStatus());
                    resp.setAttemptedAt(h.getAttemptedAt());
                    return resp;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success("Login history retrieved", history));
    }

    @GetMapping("/preferences")
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> getPreferences() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Map<String, Boolean> prefs = Map.of(
                "emailNotifications", Boolean.TRUE.equals(user.getNotificationsEmail()),
                "browserNotifications", Boolean.TRUE.equals(user.getNotificationsBrowser()),
                "dailySummary", Boolean.TRUE.equals(user.getNotificationsDailySummary()),
                "twoFactorEnabled", Boolean.TRUE.equals(user.getTwoFactorEnabled())
        );

        return ResponseEntity.ok(ApiResponse.success("Preferences retrieved", prefs));
    }

    @PutMapping("/preferences")
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> updatePreferences(
            @Valid @RequestBody NotificationPreferencesRequest request) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setNotificationsEmail(request.isEmailNotifications());
        user.setNotificationsBrowser(request.isBrowserNotifications());
        user.setNotificationsDailySummary(request.isDailySummary());
        userRepository.save(user);

        Map<String, Boolean> prefs = Map.of(
                "emailNotifications", user.getNotificationsEmail(),
                "browserNotifications", user.getNotificationsBrowser(),
                "dailySummary", user.getNotificationsDailySummary(),
                "twoFactorEnabled", Boolean.TRUE.equals(user.getTwoFactorEnabled())
        );

        return ResponseEntity.ok(ApiResponse.success("Preferences updated", prefs));
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String addr = request.getRemoteAddr();
        if ("0:0:0:0:0:0:0:1".equals(addr)) {
            return "127.0.0.1";
        }
        return addr;
    }

}
