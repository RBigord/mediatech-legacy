package com.mediatech;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Un simple CRUD : aucune règle métier. */
@RestController
@RequestMapping("/api/adherents")
public class AdherentController {

    @Autowired private JdbcTemplate jdbc;

    @GetMapping
    public List<Map<String, Object>> lister() {
        return jdbc.queryForList("SELECT * FROM adherent ORDER BY nom");
    }

    @GetMapping("/{id}")
    public Map<String, Object> consulter(@PathVariable Long id) {
        return jdbc.queryForMap("SELECT * FROM adherent WHERE id = ?", id);
    }

    @PostMapping
    public ResponseEntity<?> inscrire(@RequestBody Map<String, String> adherent) {
        jdbc.update("INSERT INTO adherent(nom, prenom, email, categorie) VALUES (?,?,?,?)",
            adherent.get("nom"), adherent.get("prenom"), adherent.get("email"), adherent.get("categorie"));
        return ResponseEntity.status(201).build();
    }
}
