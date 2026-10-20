package com.mediatech;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Arrays;

/**
 * Pas de vrai serveur mail en cours : les emails sont « envoyés » dans la console.
 * Pour brancher un vrai serveur SMTP, il suffirait de supprimer cette classe
 * et de renseigner spring.mail.host dans application.yml.
 */
@Configuration
public class EmailsSimules {

    @Bean
    JavaMailSender mailSender() {
        return new JavaMailSenderImpl() {
            @Override
            public void send(SimpleMailMessage simpleMessage) {
                afficher(simpleMessage);
            }

            @Override
            public void send(SimpleMailMessage... simpleMessages) {
                Arrays.stream(simpleMessages).forEach(EmailsSimules::afficher);
            }
        };
    }

    private static void afficher(SimpleMailMessage email) {
        System.out.println("""
                ┌── 📧 EMAIL (simulé) ──────────────────────────
                │ À     : %s
                │ Sujet : %s
                │ %s
                └───────────────────────────────────────────────"""
                .formatted(String.join(", ", email.getTo()), email.getSubject(), email.getText()));
    }
}
