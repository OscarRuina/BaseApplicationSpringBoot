package com.organization.application.configurations.email.service;

import com.organization.application.configurations.exceptions.MailSendException;
import com.organization.application.messages.ExceptionMessages;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.util.Arrays;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

@Service
@Slf4j
public class EmailService implements IEmailService{

    private static final String LOGO_IMAGE = "/static/images/logo.png";

    private final JavaMailSender mailSender;

    private final SpringTemplateEngine templateEngine;

    private final String emailUser;

    public EmailService(JavaMailSender mailSender, SpringTemplateEngine templateEngine,
            @Value("${email.sender}") String emailUser) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
        this.emailUser = emailUser;
    }

    @Override
    public void sendEmail(String[] toUser, String subject, String template, Map<String, Object> message) {
        MimeMessage mimeMessage = mailSender.createMimeMessage();
        try{
            MimeMessageHelper mimeMessageHelper = new MimeMessageHelper(mimeMessage, true);
            mimeMessageHelper.setFrom(emailUser);
            mimeMessageHelper.setTo(toUser);
            mimeMessageHelper.setSubject(subject);

            Context context = new Context();
            context.setVariables(message);

            String htmlContent = templateEngine.process(template, context);
            mimeMessageHelper.setText(htmlContent,true);

            ClassPathResource resource = new ClassPathResource(LOGO_IMAGE);
            mimeMessageHelper.addInline("logoImage", resource);

            mailSender.send(mimeMessage);
            log.info("Mail sent to {} using template {}", Arrays.toString(toUser), template);
        } catch (MessagingException | MailException e) {
            log.error("{} to {}", ExceptionMessages.MAIL_ERROR, Arrays.toString(toUser), e);
            throw new MailSendException(ExceptionMessages.MAIL_ERROR, e);
        }
    }
}
