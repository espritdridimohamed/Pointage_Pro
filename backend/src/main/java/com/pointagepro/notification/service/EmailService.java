package com.pointagepro.notification.service;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private JavaMailSender mailSender;

    @Autowired(required = false)
    public void setMailSender(JavaMailSender mailSender) {
        this.mailSender = mailSender;
        log.info("EmailService initialized - mailSender available: {}", mailSender != null);
    }

    @Async
    public void sendHtmlEmail(String to, String subject, String htmlBody) {
        log.info("Attempting to send HTML email to '{}' with subject '{}'", to, subject);

        if (mailSender == null) {
            log.warn("JavaMailSender is null - email NOT sent.");
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom("dridi.mohammed01@gmail.com", "Sepab Agro");
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            mailSender.send(message);
            log.info("HTML email sent successfully to '{}'", to);
        } catch (Exception e) {
            log.error("Failed to send email to '{}': {}", to, e.getMessage(), e);
        }
    }
}
