package com.pointagepro.auth;

import com.pointagepro.auth.dto.*;
import com.pointagepro.notification.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final String TOTP_ALGORITHM = "HmacSHA1";
    private static final int TOTP_DIGITS = 6;
    private static final int TOTP_PERIOD_SECONDS = 30;

    private final AuthenticationManager authenticationManager;
    private final com.pointagepro.security.JwtTokenProvider tokenProvider;
    private final UserRepository userRepository;
    private final UserSessionRepository sessionRepository;
    private final LoginHistoryRepository loginHistoryRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserDetailsService userDetailsService;
    private final EmailService emailService;

    public AuthService(AuthenticationManager authenticationManager,
                       com.pointagepro.security.JwtTokenProvider tokenProvider,
                       UserRepository userRepository,
                       UserSessionRepository sessionRepository,
                       LoginHistoryRepository loginHistoryRepository,
                       PasswordEncoder passwordEncoder,
                       UserDetailsService userDetailsService,
                       EmailService emailService) {
        this.authenticationManager = authenticationManager;
        this.tokenProvider = tokenProvider;
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.loginHistoryRepository = loginHistoryRepository;
        this.passwordEncoder = passwordEncoder;
        this.userDetailsService = userDetailsService;
        this.emailService = emailService;
    }

    public AuthResponse login(LoginRequest request, String ip, String userAgent) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
            );
        } catch (BadCredentialsException e) {
            User user = userRepository.findByUsername(request.getUsername()).orElse(null);
            if (user != null) {
                logLoginAttempt(user.getId(), ip, userAgent, "FAILED");
            }
            throw e;
        }

        User user = (User) authentication.getPrincipal();

        if (Boolean.TRUE.equals(user.getTwoFactorEnabled())) {
            String tempToken = UUID.randomUUID().toString();
            String tempHash = hashToken(tempToken);
            UserSession tempSession = new UserSession();
            tempSession.setUserId(user.getId());
            tempSession.setTokenHash(tempHash);
            tempSession.setDeviceInfo("2FA-temp");
            tempSession.setIpAddress(ip);
            sessionRepository.save(tempSession);

            logLoginAttempt(user.getId(), ip, userAgent, "SUCCESS_2FA_PENDING");
            return new AuthResponse(null, user.getUsername(), user.getFullName(), user.getEmail(),
                    user.getRole().name(), true, tempToken);
        }

        String token = tokenProvider.generateToken(authentication);
        createSession(user.getId(), token, ip, userAgent);
        cleanupOldSessions(user.getId());
        logLoginAttempt(user.getId(), ip, userAgent, "SUCCESS");

        log.info("User logged in: {}", user.getUsername());
        return new AuthResponse(token, user.getUsername(), user.getFullName(), user.getEmail(), user.getRole().name());
    }

    public AuthResponse completeTwoFactorLogin(TwoFactorLoginRequest request, String ip, String userAgent) {
        String tempHash = hashToken(request.getTempToken());
        List<UserSession> tempSessions = sessionRepository.findByTokenHashAndRevokedFalse(tempHash);
        UserSession tempSession = tempSessions.isEmpty() ? null : tempSessions.get(0);

        if (tempSession == null || !"2FA-temp".equals(tempSession.getDeviceInfo())) {
            throw new RuntimeException("Session 2FA invalide ou expirée");
        }

        User user = userRepository.findById(tempSession.getUserId())
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));

        if (!verifyTotpCode(user.getTwoFactorSecret(), request.getCode())) {
            throw new RuntimeException("Code 2FA invalide");
        }

        tempSession.setRevoked(true);
        sessionRepository.save(tempSession);

        if (user.getTwoFactorSecret() != null && user.getTwoFactorSecret().matches("[0-9a-fA-F]{40}")) {
            byte[] secretBytes = HexFormat.of().parseHex(user.getTwoFactorSecret().toLowerCase());
            user.setTwoFactorSecret(base32Encode(secretBytes));
            userRepository.save(user);
        }

        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getUsername());
        Authentication auth = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        String token = tokenProvider.generateToken(auth);

        createSession(user.getId(), token, ip, userAgent);
        cleanupOldSessions(user.getId());
        logLoginAttempt(user.getId(), ip, userAgent, "SUCCESS_2FA");

        return new AuthResponse(token, user.getUsername(), user.getFullName(), user.getEmail(), user.getRole().name());
    }

    public void forgotPassword(String email) {
        User user = userRepository.findByEmail(email)
                .orElse(null);
        if (user == null) return;

        String code = String.format("%06d", new SecureRandom().nextInt(1_000_000));
        String codeHash = hashToken(code);

        user.setPasswordResetToken(codeHash);
        user.setPasswordResetExpires(LocalDateTime.now().plusMinutes(15));
        userRepository.save(user);

        String html = buildResetEmailHtml(user.getFullName(), code);
        emailService.sendHtmlEmail(email, "PointagePro — Réinitialisation du mot de passe", html);

        log.info("Password reset code sent to {}", email);
    }

    public boolean verifyResetCode(String email, String code) {
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null || user.getPasswordResetToken() == null || user.getPasswordResetExpires() == null) {
            return false;
        }
        if (user.getPasswordResetExpires().isBefore(LocalDateTime.now())) {
            user.setPasswordResetToken(null);
            user.setPasswordResetExpires(null);
            userRepository.save(user);
            return false;
        }
        return user.getPasswordResetToken().equals(hashToken(code));
    }

    public void resetPassword(String email, String code, String newPassword) {
        if (!verifyResetCode(email, code)) {
            throw new RuntimeException("Code invalide ou expiré");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setPasswordResetToken(null);
        user.setPasswordResetExpires(null);
        userRepository.save(user);

        revokeAllSessions(user.getId());

        log.info("Password reset for user {}", user.getUsername());
    }

    public void revokeAllSessions(Long userId) {
        sessionRepository.findByUserIdAndRevokedFalseOrderByCreatedAtDesc(userId)
                .forEach(s -> {
                    s.setRevoked(true);
                    sessionRepository.save(s);
                });
    }

    public TwoFactorSetupResponse generateTwoFactorSetup(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));

        byte[] secretBytes = new byte[20];
        new SecureRandom().nextBytes(secretBytes);
        String secret = base32Encode(secretBytes);

        String otpauthUri = String.format("otpauth://totp/PointagePro:%s?secret=%s&issuer=PointagePro&digits=%d&period=%d",
                user.getUsername(), secret, TOTP_DIGITS, TOTP_PERIOD_SECONDS);

        user.setTwoFactorSecret(secret);
        userRepository.save(user);

        return new TwoFactorSetupResponse(secret, otpauthUri);
    }

    public boolean verifyTwoFactorCode(String username, String code) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));

        if (user.getTwoFactorSecret() == null) {
            throw new RuntimeException("2FA non initialisé");
        }

        return verifyTotpCode(user.getTwoFactorSecret(), code);
    }

    public void enableTwoFactor(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));
        user.setTwoFactorEnabled(true);
        userRepository.save(user);
    }

    public void disableTwoFactor(String username, String password, String code) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new RuntimeException("Mot de passe incorrect");
        }

        if (!verifyTotpCode(user.getTwoFactorSecret(), code)) {
            throw new RuntimeException("Code 2FA invalide");
        }

        user.setTwoFactorEnabled(false);
        user.setTwoFactorSecret(null);
        userRepository.save(user);
    }

    public User getCurrentUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));
    }

    public boolean verifyTotpCode(String secret, String code) {
        if (secret == null || code == null) return false;
        long currentTimeSeconds = System.currentTimeMillis() / 1000;
        for (int i = -1; i <= 1; i++) {
            long timeStep = (currentTimeSeconds / TOTP_PERIOD_SECONDS) + i;
            String expectedCode = generateTotpCode(secret, timeStep);
            if (expectedCode.equals(code)) {
                return true;
            }
        }
        return false;
    }

    private void cleanupOldSessions(Long userId) {
        var sessions = sessionRepository.findByUserIdAndRevokedFalseOrderByCreatedAtDesc(userId);
        if (sessions.size() > 3) {
            for (int i = 3; i < sessions.size(); i++) {
                sessions.get(i).setRevoked(true);
                sessionRepository.save(sessions.get(i));
            }
        }
    }

    private String generateTotpCode(String secret, long timeStep) {
        try {
            byte[] secretBytes = decodeSecret(secret);
            byte[] timeStepBytes = new byte[8];
            long t = timeStep;
            for (int i = 7; i >= 0; i--) {
                timeStepBytes[i] = (byte) (t & 0xFF);
                t >>= 8;
            }

            Mac mac = Mac.getInstance(TOTP_ALGORITHM);
            mac.init(new SecretKeySpec(secretBytes, TOTP_ALGORITHM));
            byte[] hash = mac.doFinal(timeStepBytes);

            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24) |
                         ((hash[offset + 1] & 0xFF) << 16) |
                         ((hash[offset + 2] & 0xFF) << 8) |
                         (hash[offset + 3] & 0xFF);

            int otp = binary % (int) Math.pow(10, TOTP_DIGITS);
            return String.format("%0" + TOTP_DIGITS + "d", otp);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate TOTP code", e);
        }
    }

    private void createSession(Long userId, String token, String ip, String userAgent) {
        String tokenHash = hashToken(token);
        UserSession existing = sessionRepository.findTopByTokenHashOrderByCreatedAtDesc(tokenHash);
        if (existing != null) {
            return;
        }
        UserSession session = new UserSession();
        session.setUserId(userId);
        session.setTokenHash(tokenHash);
        session.setDeviceInfo(userAgent != null ? userAgent : "Unknown");
        session.setIpAddress(ip);
        sessionRepository.save(session);
    }

    private void logLoginAttempt(Long userId, String ip, String userAgent, String status) {
        LoginHistory history = new LoginHistory();
        history.setUserId(userId);
        history.setIpAddress(ip);
        history.setUserAgent(userAgent);
        history.setStatus(status);
        loginHistoryRepository.save(history);
    }

    private byte[] decodeSecret(String secret) {
        if (secret != null && secret.matches("[0-9a-fA-F]{40}")) {
            return HexFormat.of().parseHex(secret.toLowerCase());
        }
        return base32Decode(secret);
    }

    private String base32Encode(byte[] bytes) {
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
        StringBuilder sb = new StringBuilder();
        int bits = 0;
        int value = 0;
        for (byte b : bytes) {
            value = (value << 8) | (b & 0xFF);
            bits += 8;
            while (bits >= 5) {
                sb.append(alphabet.charAt((value >>> (bits - 5)) & 0x1F));
                bits -= 5;
            }
        }
        if (bits > 0) {
            sb.append(alphabet.charAt((value << (5 - bits)) & 0x1F));
        }
        return sb.toString();
    }

    private byte[] base32Decode(String secret) {
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
        String normalized = secret.toUpperCase().replaceAll("[^A-Z2-7]", "");
        int bits = 0;
        int value = 0;
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        for (char c : normalized.toCharArray()) {
            int idx = alphabet.indexOf(c);
            if (idx < 0) continue;
            value = (value << 5) | idx;
            bits += 5;
            if (bits >= 8) {
                out.write((value >>> (bits - 8)) & 0xFF);
                bits -= 8;
            }
        }
        return out.toByteArray();
    }

    private String hashToken(String token) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("Failed to hash token", e);
        }
    }

    private String buildResetEmailHtml(String fullName, String code) {
        return """
<!DOCTYPE html>
<html>
<head><meta charset="UTF-8"></head>
<body style="margin:0;padding:0;background:#f0f2f5;font-family:'Segoe UI',Arial,sans-serif;">
<table width="100%" cellpadding="0" cellspacing="0" style="padding:40px 20px;">
<tr><td align="center">
<table width="480" cellpadding="0" cellspacing="0" style="background:#fff;border-radius:16px;overflow:hidden;box-shadow:0 4px 24px rgba(0,0,0,0.06);">
<tr><td style="background:linear-gradient(135deg,#1e3a5f,#0f2640);padding:36px 40px;text-align:center;">
<div style="width:48px;height:48px;border-radius:12px;background:rgba(255,255,255,0.15);display:inline-block;line-height:48px;margin-bottom:12px;">
<span style="color:#fff;font-size:24px;">&#128274;</span>
</div>
<h1 style="color:#fff;font-size:20px;margin:0;font-weight:600;">PointagePro</h1>
<p style="color:rgba(255,255,255,0.6);font-size:13px;margin:4px 0 0;">Système de Gestion de Présence</p>
</td></tr>
<tr><td style="padding:40px;">
<h2 style="color:#0f172a;font-size:18px;margin:0 0 8px;">Réinitialisation du mot de passe</h2>
<p style="color:#64748b;font-size:14px;margin:0 0 24px;">Bonjour {fullName},</p>
<p style="color:#64748b;font-size:14px;margin:0 0 24px;">Vous avez demandé la réinitialisation de votre mot de passe. Utilisez le code ci-dessous dans les <strong style="color:#0f172a;">15 prochaines minutes</strong> :</p>
<table width="100%" cellpadding="0" cellspacing="0"><tr><td align="center" style="padding:8px 0 28px;">
<table cellpadding="0" cellspacing="0"><tr>
<td style="background:#f1f5f9;border-radius:10px;padding:14px 32px;">
<span style="font-family:'Courier New',monospace;font-size:28px;font-weight:700;letter-spacing:8px;color:#2563eb;">{code}</span>
</td>
</tr></table>
</td></tr></table>
<p style="color:#94a3b8;font-size:13px;margin:0 0 20px;">Si vous n'avez pas demandé cette réinitialisation, ignorez simplement cet email.</p>
</td></tr>
<tr><td style="padding:0 40px 32px;">
<table width="100%" cellpadding="0" cellspacing="0" style="border-top:1px solid #f1f5f9;">
<tr><td style="padding:20px 0 0;text-align:center;">
<p style="color:#94a3b8;font-size:11px;margin:0;line-height:1.6;">
Sepab Agro — Rue Farhat Hached, Morneg, Ben Arous<br>
+216 241 466 02
</p>
</td></tr>
</table>
</td></tr>
</table>
</td></tr></table>
</body>
</html>""".replace("{fullName}", fullName).replace("{code}", code);
    }
}
