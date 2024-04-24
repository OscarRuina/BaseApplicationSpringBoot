package com.organization.application.configurations.email.service;

public interface IEmailService {

    void sendEmail(String[] toUser, String subject, String message);
}
