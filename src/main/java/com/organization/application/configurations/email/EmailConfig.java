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

    private final String host;

    private final int port;

    private final boolean startTls;

    private final boolean auth;

    private final String connectionTimeout;

    private final String timeout;

    private final String writeTimeout;

    private final boolean debug;

    public EmailConfig(@Value("${email.sender}") String emailUser,
            @Value("${email.password}") String password,
            @Value("${app.mail.host}") String host,
            @Value("${app.mail.port}") int port,
            @Value("${app.mail.starttls}") boolean startTls,
            @Value("${app.mail.auth}") boolean auth,
            @Value("${app.mail.connection-timeout}") String connectionTimeout,
            @Value("${app.mail.timeout}") String timeout,
            @Value("${app.mail.write-timeout}") String writeTimeout,
            @Value("${email.debug:false}") boolean debug) {
        this.emailUser = emailUser;
        this.password = password;
        this.host = host;
        this.port = port;
        this.startTls = startTls;
        this.auth = auth;
        this.connectionTimeout = connectionTimeout;
        this.timeout = timeout;
        this.writeTimeout = writeTimeout;
        this.debug = debug;
    }

    @Bean
    public JavaMailSender getJavaMailSender() {

        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();

        mailSender.setHost(host);
        mailSender.setPort(port);
        mailSender.setUsername(emailUser);
        mailSender.setPassword(password);

        Properties props = mailSender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", String.valueOf(auth));
        props.put("mail.smtp.starttls.enable", String.valueOf(startTls));
        props.put("mail.smtp.connectiontimeout", connectionTimeout);
        props.put("mail.smtp.timeout", timeout);
        props.put("mail.smtp.writetimeout", writeTimeout);
        props.put("mail.debug", String.valueOf(debug));

        return mailSender;
    }
}
