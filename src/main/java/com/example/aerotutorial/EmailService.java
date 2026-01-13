package com.example.aerotutorial;

import jakarta.mail.*;
import jakarta.mail.internet.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;


public class EmailService {
    private static EmailService instance;
    private final Map<String, VerificationData> verificationCodes = new ConcurrentHashMap<>();
    private Properties mailProperties;
    private boolean mockMode = false;

    private EmailService() {
        setupMailProperties();
        checkMockMode();
    }

    public static EmailService getInstance() {
        if (instance == null) {
            instance = new EmailService();
        }
        return instance;
    }

    private void setupMailProperties() {
        mailProperties = new Properties();
        mailProperties.put("mail.smtp.host", ConfigLoader.getProperty("mail.smtp.host", "smtp.gmail.com"));
        mailProperties.put("mail.smtp.port", ConfigLoader.getProperty("mail.smtp.port", "587"));
        mailProperties.put("mail.smtp.auth", "true");
        mailProperties.put("mail.smtp.starttls.enable", "true");
        mailProperties.put("mail.smtp.ssl.trust", ConfigLoader.getProperty("mail.smtp.host", "smtp.gmail.com"));
    }


    private void checkMockMode() {
        String username = ConfigLoader.getProperty("mail.username", "");
        String password = ConfigLoader.getProperty("mail.password", "");


        if (username.isEmpty() || password.isEmpty() ||
            username.equals("your-email@gmail.com") ||
            password.equals("your-app-password")) {
            mockMode = true;
            System.out.println("⚠️ Email not configured - Running in MOCK MODE");
            System.out.println("📧 Verification codes will be displayed in console");
        } else {
            mockMode = false;
            System.out.println("✅ Email service configured - Using real SMTP");
        }
    }


    public boolean sendVerificationEmail(String toEmail, String userName) {
        String verificationCode = generateVerificationCode();
        verificationCodes.put(toEmail, new VerificationData(verificationCode, System.currentTimeMillis()));

        System.out.println("📧 Sending verification email to: " + toEmail);
        System.out.println("🔑 Verification code: " + verificationCode + " (expires in 30 seconds)");
        if (mockMode) {
            System.out.println("========================================");
            System.out.println("🎭 MOCK MODE - EMAIL NOT SENT");
            System.out.println("📧 To: " + toEmail);
            System.out.println("👤 User: " + userName);
            System.out.println("🔑 VERIFICATION CODE: " + verificationCode);
            System.out.println("⏱️  Expires in 30 seconds");
            System.out.println("========================================");
            scheduleCodeExpiration(toEmail);
            return true;
        }


        try {
            Session session = createMailSession();
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(
                ConfigLoader.getProperty("mail.username"),
                ConfigLoader.getProperty("mail.from.name", "AeroSafe Team")
            ));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail));
            message.setSubject("AeroSafe - Email Verification");

            String htmlContent = String.format(
                "<html><body style='font-family: Arial, sans-serif; margin: 0; padding: 0;'>" +
                "<div style='background: linear-gradient(135deg, #667eea 0%%, #764ba2 100%%); padding: 30px; text-align: center;'>" +
                "<h1 style='color: white; margin: 0;'>🌍 AeroSafe</h1>" +
                "<p style='color: white; margin: 5px 0 0 0;'>Air Quality Monitoring System</p>" +
                "</div>" +
                "<div style='padding: 40px 20px; background-color: #f9f9f9;'>" +
                "<div style='max-width: 600px; margin: 0 auto; background-color: white; padding: 30px; border-radius: 10px; box-shadow: 0 2px 10px rgba(0,0,0,0.1);'>" +
                "<h2 style='color: #333; margin-top: 0;'>Hello %s! 👋</h2>" +
                "<p style='color: #666; line-height: 1.6;'>Thank you for registering with AeroSafe. To complete your registration, please verify your email address.</p>" +
                "<p style='color: #666; line-height: 1.6;'>Your verification code is:</p>" +
                "<div style='background: linear-gradient(135deg, #667eea 0%%, #764ba2 100%%); color: white; padding: 20px; text-align: center; font-size: 32px; font-weight: bold; letter-spacing: 8px; border-radius: 8px; margin: 20px 0;'>" +
                "%s" +
                "</div>" +
                "<p style='color: #999; font-size: 14px; text-align: center;'>⏱️ This code will expire in 30 seconds</p>" +
                "<hr style='border: none; border-top: 1px solid #eee; margin: 30px 0;'>" +
                "<p style='color: #999; font-size: 12px; line-height: 1.6;'>If you didn't request this verification, please ignore this email. Your account will not be created without email verification.</p>" +
                "</div>" +
                "</div>" +
                "<div style='text-align: center; padding: 20px; color: #999; font-size: 12px;'>" +
                "<p>© 2026 AeroSafe - Air Quality Monitoring System</p>" +
                "</div>" +
                "</body></html>",
                userName, verificationCode
            );

            message.setContent(htmlContent, "text/html; charset=utf-8");
            Transport.send(message);

            System.out.println("✅ Verification email sent successfully to: " + toEmail);


            scheduleCodeExpiration(toEmail);

            return true;
        } catch (Exception e) {
            System.err.println("❌ Failed to send verification email: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }


    public boolean sendPasswordResetEmail(String toEmail, String userName) {
        String resetCode = generateVerificationCode();
        verificationCodes.put(toEmail, new VerificationData(resetCode, System.currentTimeMillis()));

        System.out.println("📧 Sending password reset email to: " + toEmail);
        System.out.println("🔑 Reset code: " + resetCode + " (expires in 30 seconds)");


        try {
            Session session = createMailSession();
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(
                ConfigLoader.getProperty("mail.username"),
                ConfigLoader.getProperty("mail.from.name", "AeroSafe Team")
            ));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail));
            message.setSubject("AeroSafe - Password Reset Request");

            String htmlContent = String.format(
                "<html><body style='font-family: Arial, sans-serif; margin: 0; padding: 0;'>" +
                "<div style='background: linear-gradient(135deg, #f093fb 0%%, #f5576c 100%%); padding: 30px; text-align: center;'>" +
                "<h1 style='color: white; margin: 0;'>🔐 AeroSafe</h1>" +
                "<p style='color: white; margin: 5px 0 0 0;'>Password Reset Request</p>" +
                "</div>" +
                "<div style='padding: 40px 20px; background-color: #f9f9f9;'>" +
                "<div style='max-width: 600px; margin: 0 auto; background-color: white; padding: 30px; border-radius: 10px; box-shadow: 0 2px 10px rgba(0,0,0,0.1);'>" +
                "<h2 style='color: #333; margin-top: 0;'>Hello %s! 👋</h2>" +
                "<p style='color: #666; line-height: 1.6;'>We received a request to reset your password. Use the code below to reset your password:</p>" +
                "<div style='background: linear-gradient(135deg, #f093fb 0%%, #f5576c 100%%); color: white; padding: 20px; text-align: center; font-size: 32px; font-weight: bold; letter-spacing: 8px; border-radius: 8px; margin: 20px 0;'>" +
                "%s" +
                "</div>" +
                "<p style='color: #999; font-size: 14px; text-align: center;'>⏱️ This code will expire in 30 seconds</p>" +
                "<hr style='border: none; border-top: 1px solid #eee; margin: 30px 0;'>" +
                "<p style='color: #999; font-size: 12px; line-height: 1.6;'><strong>⚠️ Security Note:</strong> If you didn't request this password reset, please ignore this email and ensure your account is secure.</p>" +
                "</div>" +
                "</div>" +
                "<div style='text-align: center; padding: 20px; color: #999; font-size: 12px;'>" +
                "<p>© 2026 AeroSafe - Air Quality Monitoring System</p>" +
                "</div>" +
                "</body></html>",
                userName, resetCode
            );

            message.setContent(htmlContent, "text/html; charset=utf-8");
            Transport.send(message);

            System.out.println("✅ Password reset email sent successfully to: " + toEmail);


            scheduleCodeExpiration(toEmail);

            return true;
        } catch (Exception e) {
            System.err.println("❌ Failed to send password reset email: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }


    public boolean verifyCode(String email, String code) {
        VerificationData data = verificationCodes.get(email);
        if (data == null) {
            System.out.println("❌ No verification code found for: " + email);
            return false;
        }


        long currentTime = System.currentTimeMillis();
        long elapsedSeconds = (currentTime - data.timestamp) / 1000;

        if (elapsedSeconds > 30) {
            System.out.println("⏱️ Verification code expired for: " + email);
            verificationCodes.remove(email);
            return false;
        }

        boolean isValid = data.code.equals(code);
        if (isValid) {
            System.out.println("✅ Verification successful for: " + email);
            verificationCodes.remove(email);
        } else {
            System.out.println("❌ Invalid verification code for: " + email);
        }

        return isValid;
    }


    private Session createMailSession() {
        return Session.getInstance(mailProperties, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(
                    ConfigLoader.getProperty("mail.username"),
                    ConfigLoader.getProperty("mail.password")
                );
            }
        });
    }


    private String generateVerificationCode() {
        Random random = new Random();
        return String.format("%06d", random.nextInt(999999));
    }


    private void scheduleCodeExpiration(String email) {
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                verificationCodes.remove(email);
                System.out.println("🗑️ Expired verification code removed for: " + email);
            }
        }, 30 * 1000);
    }


    private static class VerificationData {
        String code;
        long timestamp;

        VerificationData(String code, long timestamp) {
            this.code = code;
            this.timestamp = timestamp;
        }
    }
}

