package com.pointagepro.notification;

import com.pointagepro.auth.User;
import com.pointagepro.auth.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
@Transactional
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final NotificationRepository repository;
    private final UserRepository userRepository;
    private EmailService emailService;

    public NotificationService(NotificationRepository repository, UserRepository userRepository) {
        this.repository = repository;
        this.userRepository = userRepository;
    }

    @Autowired(required = false)
    public void setEmailService(EmailService emailService) {
        this.emailService = emailService;
    }

    public void notify(String type, String title, String message, String priority) {
        notify(type, title, message, priority, null, null);
    }

    public void notify(String type, String title, String message, String priority,
                       String relatedEntityType, Long relatedEntityId) {
        Notification n = new Notification(type, title, message, priority);
        n.setRelatedEntityType(relatedEntityType);
        n.setRelatedEntityId(relatedEntityId);
        repository.save(n);
        log.debug("Notification created: [{}] {}", type, title);

        if (emailService != null) {
            try {
                User admin = userRepository.findByUsername("admin").orElse(null);
                if (admin != null && Boolean.TRUE.equals(admin.getNotificationsEmail()) && admin.getEmail() != null) {
                    String htmlBody = buildEmailHtml(type, title, message, priority);
                    emailService.sendHtmlEmail(admin.getEmail(), "PointagePro - " + title, htmlBody);
                }
            } catch (Exception e) {
                log.debug("Could not send email notification", e);
            }
        }
    }

    private String buildEmailHtml(String type, String title, String message, String priority) {
        String priorityColor;
        String priorityLabel;
        switch (priority != null ? priority : "MEDIUM") {
            case "HIGH": priorityColor = "#DC2626"; priorityLabel = "HAUTE"; break;
            case "LOW": priorityColor = "#2563EB"; priorityLabel = "BASSE"; break;
            default: priorityColor = "#D97706"; priorityLabel = "MOYENNE"; break;
        }

        String icon;
        switch (type != null ? type : "") {
            case "UNKNOWN_BADGE": icon = "badge"; break;
            case "TERMINAL_OFFLINE": icon = "wifi_off"; break;
            case "TERMINAL_ONLINE": icon = "wifi"; break;
            case "LEAVE_REQUEST": icon = "event_busy"; break;
            case "LEAVE_APPROVED_INFO": icon = "check_circle"; break;
            case "LEAVE_REFUSED": icon = "cancel"; break;
            case "LEAVE_LOW_BALANCE": icon = "warning"; break;
            case "LEAVE_ENDED": icon = "event_available"; break;
            case "PAYROLL_GENERATED": icon = "receipt_long"; break;
            case "PAYROLL_ITEM_PAID": icon = "payments"; break;
            case "PAYROLL_ALL_PAID": icon = "paid"; break;
            case "DAILY_SUMMARY": icon = "summarize"; break;
            case "WEEKLY_SUMMARY": icon = "date_range"; break;
            case "MONTHLY_SUMMARY": icon = "calendar_month"; break;
            case "CHECK_IN": icon = "login"; break;
            case "CHECK_OUT": icon = "logout"; break;
            case "LATE_ARRIVAL": icon = "schedule"; break;
            case "EARLY_DEPARTURE": icon = "timelapse"; break;
            case "INCOMPLETE_SCAN": icon = "scan"; break;
            case "EMPLOYEE_CREATED": icon = "person_add"; break;
            case "EMPLOYEE_STATUS_CHANGE": icon = "swap_horiz"; break;
            case "EMPLOYEE_RFID_ASSIGNED": icon = "credit_card"; break;
            case "AUTO_RESTORE": icon = "restore"; break;
            case "SETTINGS_CHANGED": icon = "settings"; break;
            case "INACTIVE_SCAN": icon = "block"; break;
            default: icon = "notifications"; break;
        }

        String now = LocalDateTime.now().format(DT_FMT);

        return "<!DOCTYPE html>" +
            "<html><head><meta charset='UTF-8'>" +
            "<link href='https://fonts.googleapis.com/icon?family=Material+Icons' rel='stylesheet'>" +
            "</head><body style='margin:0;padding:0;background:#f3f4f6;font-family:Arial,Helvetica,sans-serif;'>" +
            "<div style='max-width:600px;margin:0 auto;padding:32px 16px;'>" +

            // Header
            "<div style='background:linear-gradient(135deg,#1e3a5f 0%,#2563eb 100%);border-radius:12px 12px 0 0;padding:32px;text-align:center;'>" +
            "<div style='font-size:28px;color:#fff;font-weight:700;letter-spacing:1px;'>POINTAGEPRO</div>" +
            "<div style='color:rgba(255,255,255,0.7);font-size:13px;margin-top:4px;'>Système de Gestion de Pointage</div>" +
            "</div>" +

            // Body card
            "<div style='background:#fff;border-radius:0 0 12px 12px;box-shadow:0 2px 8px rgba(0,0,0,0.08);overflow:hidden;'>" +

            // Priority bar
            "<div style='background:" + priorityColor + ";padding:8px 24px;'>" +
            "<span style='color:#fff;font-size:12px;font-weight:600;letter-spacing:1px;'>PRIORITÉ " + priorityLabel + "</span>" +
            "</div>" +

            // Content
            "<div style='padding:32px;'>" +

            // Icon + title
            "<div style='text-align:center;margin-bottom:24px;'>" +
            "<div style='display:inline-block;background:#EBF5FF;border-radius:50%;width:64px;height:64px;line-height:64px;text-align:center;'>" +
            "<span class='material-icons' style='font-size:28px;color:#2563eb;vertical-align:middle;'>" + icon + "</span>" +
            "</div>" +
            "<h2 style='color:#1e293b;margin:16px 0 8px;font-size:22px;'>" + escapeHtml(title) + "</h2>" +
            "</div>" +

            // Message box
            "<div style='background:#f8fafc;border-left:4px solid #2563eb;border-radius:4px;padding:16px 20px;margin-bottom:24px;'>" +
            "<p style='color:#475569;margin:0;font-size:15px;line-height:1.6;'>" + escapeHtml(message) + "</p>" +
            "</div>" +

            // Details table
            "<table style='width:100%;border-collapse:collapse;margin-bottom:24px;'>" +
            "<tr><td style='padding:10px 0;color:#94a3b8;font-size:13px;width:120px;'>Type</td>" +
            "<td style='padding:10px 0;color:#1e293b;font-size:14px;font-weight:500;'>" + escapeHtml(type != null ? type : "") + "</td></tr>" +
            "<tr><td style='padding:10px 0;color:#94a3b8;font-size:13px;'>Date</td>" +
            "<td style='padding:10px 0;color:#1e293b;font-size:14px;'>" + now + "</td></tr>" +
            "<tr><td style='padding:10px 0;color:#94a3b8;font-size:13px;'>Priorité</td>" +
            "<td style='padding:10px 0;'><span style='background:" + priorityColor + "15;color:" + priorityColor + ";padding:3px 10px;border-radius:12px;font-size:12px;font-weight:600;'>" + priorityLabel + "</span></td></tr>" +
            "</table>" +

            "<div style='text-align:center;color:#94a3b8;font-size:12px;padding-top:16px;border-top:1px solid #e2e8f0;'>" +
            "Ceci est un email automatique généré par PointagePro. Ne pas répondre." +
            "</div>" +

            "</div></div></div>" +

            // Footer
            "<div style='text-align:center;padding:24px 16px;color:#94a3b8;font-size:12px;'>" +
            "Sepab Agro — Rue Farhat Hached, Morneg, Ben Arous | +216 241 466 02" +
            "</div>" +

            "</div></body></html>";
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    @Transactional(readOnly = true)
    public Page<Notification> getAll(int page, int size) {
        return repository.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size));
    }

    @Transactional(readOnly = true)
    public long getUnreadCount() {
        return repository.countByReadFalse();
    }

    public void markAsRead(Long id) {
        repository.findById(id).ifPresent(n -> {
            n.setRead(true);
            repository.save(n);
        });
    }

    public void markAllAsRead() {
        repository.markAllAsRead();
    }
}
