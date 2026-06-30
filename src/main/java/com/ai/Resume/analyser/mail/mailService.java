package com.ai.Resume.analyser.mail;


import brevo.ApiClient;
import brevo.ApiException;
import brevo.Configuration;
import brevoApi.TransactionalEmailsApi;
import brevoModel.SendSmtpEmail;
import brevoModel.SendSmtpEmailSender;
import brevoModel.SendSmtpEmailTo;
import jakarta.mail.MessagingException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

@Service
public class mailService {

    @Value("${apiKey}")
    private String apiKey;
    @Autowired
    private TemplateEngine templateEngine;

    private ApiClient buildClient() {
        ApiClient apiClient = Configuration.getDefaultApiClient();
        apiClient.setApiKey(apiKey);
        // Default Brevo SDK timeouts can be very long (or absent), which is why
        // failures were hanging for 20+ seconds instead of failing fast.
        // Force a short, explicit timeout so failures surface quickly and clearly.
        apiClient.setConnectTimeout(8000);
        apiClient.setReadTimeout(10000);
        apiClient.setWriteTimeout(10000);
        return apiClient;
    }

    public void sentVerifyOtp(String username,String email,String otp) throws MessagingException {

        String toEmail=email.substring(0,1)+"*********"+email.substring(email.indexOf("@"),email.length());
        Context context = new Context();
        context.setVariable("username",username);
        context.setVariable("email",toEmail);
        context.setVariable("otp",otp);

        String mgs = templateEngine.process("verify-otp",context);

        ApiClient apiClient = buildClient();

        TransactionalEmailsApi transactionalEmailsApi = new TransactionalEmailsApi(apiClient);

        SendSmtpEmail sendSmtpEmail = new SendSmtpEmail();
        sendSmtpEmail.setSender(
                new SendSmtpEmailSender()
                        .name("Resume Analyser")
                        .email("rohithgxwda001@gmail.com")
        );
        sendSmtpEmail.setTo(Collections.singletonList(new SendSmtpEmailTo().name(username).email(email)));
        sendSmtpEmail.setSubject("Email verification OTP");
        sendSmtpEmail.setHtmlContent(mgs);

        try{
            transactionalEmailsApi.sendTransacEmail(sendSmtpEmail);
        } catch (ApiException e) {
            System.out.println("Brevo ApiException - code: " + e.getCode()
                    + " | message: " + e.getMessage()
                    + " | responseBody: " + e.getResponseBody());
            throw new RuntimeException(e);
        } catch (Exception e) {
            System.out.println("Unexpected mail send error - class: " + e.getClass().getName()
                    + " | message: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException(e);
        }



    }

    public void sentResetOtp(String username,String email,String otp) throws MessagingException {

        String toEmail=email.substring(0,1)+"*********"+email.substring(email.indexOf("@"),email.length());
        Context context = new Context();
        context.setVariable("username",username);
        context.setVariable("email",toEmail);
        context.setVariable("otp",otp);

        String mgs = templateEngine.process("reset-otp",context);

        ApiClient apiClient = buildClient();

        TransactionalEmailsApi transactionalEmailsApi = new TransactionalEmailsApi(apiClient);

        SendSmtpEmail sendSmtpEmail = new SendSmtpEmail();
        sendSmtpEmail.setSender(new SendSmtpEmailSender()
                .name("Resume Analyser")
                .email("rohithgxwda001@gmail.com"));
        sendSmtpEmail.setTo(Collections.singletonList(new SendSmtpEmailTo().name(username).email(email)));
        sendSmtpEmail.setSubject("Reset password OTP");
        sendSmtpEmail.setHtmlContent(mgs);

        try{
            transactionalEmailsApi.sendTransacEmail(sendSmtpEmail);
        } catch (ApiException e) {
            System.out.println("Brevo ApiException - code: " + e.getCode()
                    + " | message: " + e.getMessage()
                    + " | responseBody: " + e.getResponseBody());
            throw new RuntimeException(e);
        } catch (Exception e) {
            System.out.println("Unexpected mail send error - class: " + e.getClass().getName()
                    + " | message: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException(e);
        }



    }


}