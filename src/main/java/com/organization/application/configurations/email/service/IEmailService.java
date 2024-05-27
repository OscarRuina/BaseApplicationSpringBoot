package com.organization.application.configurations.email.service;

import java.util.Map;

public interface IEmailService {

    void sendEmail(String[] toUser, String subject, Map<String, Object> message);
}
