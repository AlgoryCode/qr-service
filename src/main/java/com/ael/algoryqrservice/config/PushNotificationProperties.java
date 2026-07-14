package com.ael.algoryqrservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "push-notification")
public class PushNotificationProperties {

    private Messaging messaging = new Messaging();
    private String paymentSubject = "Ödeme Onayı";
    private String passwordResetSubject = "Şifre Değiştirme Kodu";
    private String emailChangeSubject = "E-posta Değişiklik Kodu";
    private java.util.List<String> channels = java.util.List.of("mail");

    public Messaging getMessaging() {
        return messaging;
    }

    public void setMessaging(Messaging messaging) {
        this.messaging = messaging;
    }

    public String getPaymentSubject() {
        return paymentSubject;
    }

    public void setPaymentSubject(String paymentSubject) {
        this.paymentSubject = paymentSubject;
    }

    public String getPasswordResetSubject() {
        return passwordResetSubject;
    }

    public void setPasswordResetSubject(String passwordResetSubject) {
        this.passwordResetSubject = passwordResetSubject;
    }

    public String getEmailChangeSubject() {
        return emailChangeSubject;
    }

    public void setEmailChangeSubject(String emailChangeSubject) {
        this.emailChangeSubject = emailChangeSubject;
    }

    public java.util.List<String> getChannels() {
        return channels;
    }

    public void setChannels(java.util.List<String> channels) {
        this.channels = channels;
    }

    public static class Messaging {
        private String exchange = "push-notification-exchange";
        private String routingKey = "push-notification.request";

        public String getExchange() {
            return exchange;
        }

        public void setExchange(String exchange) {
            this.exchange = exchange;
        }

        public String getRoutingKey() {
            return routingKey;
        }

        public void setRoutingKey(String routingKey) {
            this.routingKey = routingKey;
        }
    }
}
