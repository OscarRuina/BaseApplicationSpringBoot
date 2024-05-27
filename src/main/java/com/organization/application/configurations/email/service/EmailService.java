package com.organization.application.configurations.email.service;

import com.organization.application.configurations.exceptions.MailSendException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

@Service
@Slf4j
public class EmailService implements IEmailService{

    @Value("${email.sender}")
    private String emailUser;

    private static final String TEMPLATE_NEW_USER = "email_new_user";

    private final JavaMailSender mailSender;

    private final SpringTemplateEngine templateEngine;

    public EmailService(JavaMailSender mailSender, SpringTemplateEngine templateEngine) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
    }

    @Override
    public void sendEmail(String[] toUser, String subject, Map<String, Object> message) {
        MimeMessage mimeMessage = mailSender.createMimeMessage();
        try{
            MimeMessageHelper mimeMessageHelper = new MimeMessageHelper(mimeMessage, true);
            mimeMessageHelper.setFrom(emailUser);
            mimeMessageHelper.setTo(toUser);
            mimeMessageHelper.setSubject(subject);

            Context context = new Context();
            context.setVariables(message);

            String htmlContent = templateEngine.process(TEMPLATE_NEW_USER, context);
            mimeMessageHelper.setText(htmlContent,true);

            ClassPathResource resource = new ClassPathResource("/static/images/logo.jpg");
            mimeMessageHelper.addInline("logoImage", resource);

            mailSender.send(mimeMessage);
        } catch (MessagingException e) {
            log.error("Error Sending Mail");
            throw new MailSendException(e.getMessage());
        }
    }
}
