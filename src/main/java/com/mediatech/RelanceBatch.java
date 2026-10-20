package com.mediatech;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Component
public class RelanceBatch {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private JavaMailSender mailSender;

    @Scheduled(cron = "0 0 7 * * *")   // chaque jour à 7h
    public void relancerRetards() {
        List<Map<String, Object>> emprunts = jdbc.queryForList(
            "SELECT e.date_emprunt, a.email, a.categorie FROM emprunt e "
            + "JOIN adherent a ON a.id = e.adherent_id WHERE e.date_retour IS NULL");
        for (Map<String, Object> e : emprunts) {
            LocalDate debut = ((Date) e.get("date_emprunt")).toLocalDate();
            int duree = "ETUDIANT".equals(e.get("categorie")) ? 21 : 14;
            if (debut.plusDays(duree).isBefore(LocalDate.now())) {
                SimpleMailMessage msg = new SimpleMailMessage();
                msg.setTo((String) e.get("email"));
                msg.setSubject("Retard de retour");
                msg.setText("Votre document aurait dû être rendu le "
                    + debut.plusDays(duree));
                mailSender.send(msg);
            }
        }
    }
}
