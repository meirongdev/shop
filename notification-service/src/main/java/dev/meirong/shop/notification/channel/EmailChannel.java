package dev.meirong.shop.notification.channel;

import dev.meirong.shop.notification.config.NotificationProperties;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@Component
public class EmailChannel implements NotificationChannel {

    private static final Logger log = LoggerFactory.getLogger(EmailChannel.class);

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final NotificationProperties properties;

    public EmailChannel(JavaMailSender mailSender,
                        TemplateEngine templateEngine,
                        NotificationProperties properties) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
        this.properties = properties;
    }

    @Override
    public String channelType() {
        return "EMAIL";
    }

    @Override
    public void send(NotificationRequest request) {
        Context context = new Context();
        context.setVariables(request.variables());

        String htmlBody = templateEngine.process("email/" + request.templateCode(), context);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(properties.mailFrom());
            helper.setTo(request.recipientAddr());
            helper.setSubject(request.subject());
            helper.setText(htmlBody, true);

            mailSender.send(message);
            log.info("Email sent to={} template={}", request.recipientAddr(), request.templateCode());
        } catch (MessagingException e) {
            throw new RuntimeException("Failed to send email to " + request.recipientAddr(), e);
        }
    }
}
