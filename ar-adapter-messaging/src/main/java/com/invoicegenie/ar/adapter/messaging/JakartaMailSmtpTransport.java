package com.invoicegenie.ar.adapter.messaging;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Properties;
import java.util.UUID;

/**
 * Jakarta Mail SMTP transport using invoicegenie.notifications.smtp.* config.
 */
@ApplicationScoped
public class JakartaMailSmtpTransport implements SmtpMailTransport {

    @ConfigProperty(name = "invoicegenie.notifications.smtp.host", defaultValue = "none")
    String host;

    @ConfigProperty(name = "invoicegenie.notifications.smtp.port", defaultValue = "587")
    int port;

    @ConfigProperty(name = "invoicegenie.notifications.smtp.username", defaultValue = "none")
    String username;

    @ConfigProperty(name = "invoicegenie.notifications.smtp.password", defaultValue = "none")
    String password;

    @ConfigProperty(name = "invoicegenie.notifications.smtp.starttls", defaultValue = "true")
    boolean startTls;

    @Override
    public String send(String from, String to, String subject, String body) throws Exception {
        Properties props = new Properties();
        props.put("mail.smtp.host", host);
        props.put("mail.smtp.port", String.valueOf(port));
        props.put("mail.smtp.auth", String.valueOf(hasCredentials()));
        if (startTls) {
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.starttls.required", "true");
        }
        props.put("mail.smtp.connectiontimeout", "10000");
        props.put("mail.smtp.timeout", "15000");

        Session session;
        if (hasCredentials()) {
            final String user = username.trim();
            final String pass = password;
            session = Session.getInstance(props, new jakarta.mail.Authenticator() {
                @Override
                protected jakarta.mail.PasswordAuthentication getPasswordAuthentication() {
                    return new jakarta.mail.PasswordAuthentication(user, pass);
                }
            });
        } else {
            session = Session.getInstance(props);
        }

        MimeMessage message = new MimeMessage(session);
        message.setFrom(new InternetAddress(from));
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to, false));
        message.setSubject(subject != null ? subject : "", "UTF-8");
        message.setText(body != null ? body : "", "UTF-8");
        message.setHeader("X-Mailer", "InvoiceGenie");
        message.saveChanges();

        if (hasCredentials()) {
            try (Transport transport = session.getTransport("smtp")) {
                transport.connect(host, port, username.trim(), password);
                transport.sendMessage(message, message.getAllRecipients());
            }
        } else {
            Transport.send(message);
        }

        String[] messageIds = message.getHeader("Message-ID");
        if (messageIds != null && messageIds.length > 0 && messageIds[0] != null && !messageIds[0].isBlank()) {
            return messageIds[0].trim();
        }
        return "smtp-" + UUID.randomUUID();
    }

    private boolean hasCredentials() {
        return username != null && !username.isBlank() && !"none".equalsIgnoreCase(username.trim())
                && password != null && !password.isBlank() && !"none".equalsIgnoreCase(password.trim());
    }
}
