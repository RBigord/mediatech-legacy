package com.mediatech;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ReservationController {

    @Autowired private JdbcTemplate jdbc;

    @PostMapping("/reservations")
    public ResponseEntity<?> reserver(@RequestParam Long adherentId, @RequestParam String isbn) {
        Integer existe = jdbc.queryForObject(
            "SELECT COUNT(*) FROM adherent WHERE id = ?", Integer.class, adherentId);
        if (existe == 0) {
            return ResponseEntity.status(404).body("Adhérent inconnu");
        }
        Integer disponibles = jdbc.queryForObject(
            "SELECT COUNT(*) FROM exemplaire WHERE isbn = ? AND statut = 'DISPONIBLE'", Integer.class, isbn);
        if (disponibles > 0) {
            return ResponseEntity.status(409).body("Un exemplaire est disponible : empruntez-le");
        }
        jdbc.update("INSERT INTO reservation(adherent_id, isbn, date_reservation) VALUES (?,?,?)",
            adherentId, isbn, LocalDateTime.now());
        return ResponseEntity.status(201).build();
    }

    @GetMapping("/reservations")
    public List<Map<String, Object>> fileDAttente(@RequestParam String isbn) {
        return jdbc.queryForList(
            "SELECT r.id, r.adherent_id, r.date_reservation FROM reservation r "
            + "WHERE r.isbn = ? ORDER BY r.date_reservation", isbn);
    }
}
