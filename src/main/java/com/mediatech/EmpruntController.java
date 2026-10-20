package com.mediatech;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.sql.Date;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class EmpruntController {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private JavaMailSender mailSender;

    @PostMapping("/emprunts")
    public ResponseEntity<?> emprunter(@RequestParam Long adherentId,
                                       @RequestParam Long exemplaireId) {
        Map<String, Object> adh = jdbc.queryForMap(
            "SELECT * FROM adherent WHERE id = ?", adherentId);
        Date suspendu = (Date) adh.get("suspendu_jusqu_au");
        if (suspendu != null && suspendu.toLocalDate().isAfter(LocalDate.now())) {
            return ResponseEntity.status(403).body("Adhérent suspendu");
        }
        Integer nb = jdbc.queryForObject(
            "SELECT COUNT(*) FROM emprunt WHERE adherent_id = ? "
            + "AND date_retour IS NULL", Integer.class, adherentId);
        if (nb >= 3) {
            return ResponseEntity.status(409).body("Maximum 3 emprunts");
        }
        String statut = jdbc.queryForObject(
            "SELECT statut FROM exemplaire WHERE id = ?", String.class, exemplaireId);
        if (!"DISPONIBLE".equals(statut)) {
            return ResponseEntity.status(409).body("Exemplaire indisponible");
        }
        int duree = "ETUDIANT".equals(adh.get("categorie")) ? 21 : 14;
        LocalDate retourPrevu = LocalDate.now().plusDays(duree);
        jdbc.update("INSERT INTO emprunt(adherent_id, exemplaire_id, date_emprunt, "
            + "date_retour_prevue) VALUES (?,?,?,?)",
            adherentId, exemplaireId, LocalDate.now(), retourPrevu);
        jdbc.update("UPDATE exemplaire SET statut = 'EMPRUNTE' WHERE id = ?",
            exemplaireId);

        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setTo((String) adh.get("email"));
        msg.setSubject("Confirmation d'emprunt");
        msg.setText("Bonjour " + adh.get("prenom")
            + ", document à rendre avant le " + retourPrevu);
        mailSender.send(msg);
        return ResponseEntity.ok(Map.of("retourPrevu", retourPrevu.toString()));
    }

    @PostMapping("/retours")
    public ResponseEntity<?> rendre(@RequestParam Long exemplaireId) {
        Map<String, Object> emp = jdbc.queryForMap(
            "SELECT * FROM emprunt WHERE exemplaire_id = ? AND date_retour IS NULL",
            exemplaireId);
        LocalDate prevue = ((Date) emp.get("date_retour_prevue")).toLocalDate();
        long retard = ChronoUnit.DAYS.between(prevue, LocalDate.now());
        if (retard > 0) {
            jdbc.update("UPDATE adherent SET suspendu_jusqu_au = ? WHERE id = ?",
                LocalDate.now().plusDays(Math.min(retard, 30)), emp.get("adherent_id"));
        }
        jdbc.update("UPDATE emprunt SET date_retour = ? WHERE id = ?",
            LocalDate.now(), emp.get("id"));
        List<Map<String, Object>> resas = jdbc.queryForList(
            "SELECT r.id, a.email, a.prenom FROM reservation r "
            + "JOIN adherent a ON a.id = r.adherent_id "
            + "WHERE r.isbn = (SELECT isbn FROM exemplaire WHERE id = ?) "
            + "ORDER BY r.date_reservation", exemplaireId);
        if (resas.isEmpty()) {
            jdbc.update("UPDATE exemplaire SET statut = 'DISPONIBLE' WHERE id = ?",
                exemplaireId);
        } else {
            jdbc.update("UPDATE exemplaire SET statut = 'RESERVE' WHERE id = ?",
                exemplaireId);
            Map<String, Object> premier = resas.get(0);
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setTo((String) premier.get("email"));
            msg.setSubject("Votre réservation est disponible");
            msg.setText("Bonjour " + premier.get("prenom")
                + ", votre document vous attend pendant 3 jours.");
            mailSender.send(msg);
        }
        return ResponseEntity.ok().build();
    }
}
