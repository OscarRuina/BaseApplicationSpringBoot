package com.organization.application.configurations.email;

import java.util.Properties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

@Configuration
public class EmailConfig {

    private final String emailUser;

    private final String password;

    private final boolean debug;

    public EmailConfig(@Value("${email.sender}") String emailUser,
            @Value("${email.password}") String password,
            @Value("${email.debug:false}") boolean debug) {
        this.emailUser = emailUser;
        this.password = password;
        this.debug = debug;
    }

    @Bean
    public JavaMailSender getJavaMailSender() {

        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();

        mailSender.setHost("smtp.gmail.com");
        mailSender.setPort(587);
        mailSender.setUsername(emailUser);
        mailSender.setPassword(password);

        Properties props = mailSender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.debug", String.valueOf(debug));

        return mailSender;
    }
}
