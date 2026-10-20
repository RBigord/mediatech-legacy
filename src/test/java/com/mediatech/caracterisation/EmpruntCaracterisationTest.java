package com.mediatech.caracterisation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests de CARACTÉRISATION : ils figent le comportement ACTUEL de l'API, bizarreries comprises.
 * Ils passent sur le legacy… et doivent passer, SANS MODIFICATION, après le refactoring.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)   // un vrai serveur, sur un port libre
@AutoConfigureMockMvc
class EmpruntCaracterisationTest {

    static final LocalDate AUJOURDHUI = LocalDate.now();   // le legacy lit l'horloge système

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestRestTemplate http;                      // un vrai client HTTP (voir codeHttpReel)
    @MockitoBean JavaMailSender mailSender;                // aucun vrai email envoyé

    @BeforeEach
    void viderLaBase() {
        jdbc.update("DELETE FROM reservation");
        jdbc.update("DELETE FROM emprunt");
        jdbc.update("DELETE FROM exemplaire");
        jdbc.update("DELETE FROM adherent");
    }

    // ---------- outils pour préparer les données ----------

    void adherent(long id, String prenom, String categorie, LocalDate suspenduJusquAu) {
        jdbc.update("INSERT INTO adherent(id, nom, prenom, email, categorie, suspendu_jusqu_au) VALUES (?,?,?,?,?,?)",
                id, "Nom" + id, prenom, prenom.toLowerCase() + "@example.org", categorie, suspenduJusquAu);
    }

    void exemplaire(long id, String isbn, String statut) {
        jdbc.update("INSERT INTO exemplaire(id, isbn, titre, statut) VALUES (?,?,?,?)", id, isbn, "Titre " + id, statut);
    }

    void empruntEnCours(long adherentId, long exemplaireId, LocalDate retourPrevu) {
        jdbc.update("INSERT INTO emprunt(adherent_id, exemplaire_id, date_emprunt, date_retour_prevue) VALUES (?,?,?,?)",
                adherentId, exemplaireId, retourPrevu.minusDays(14), retourPrevu);
    }

    void reservation(long adherentId, String isbn, LocalDateTime quand) {
        jdbc.update("INSERT INTO reservation(adherent_id, isbn, date_reservation) VALUES (?,?,?)", adherentId, isbn, quand);
    }

    String statutDe(long exemplaireId) {
        return jdbc.queryForObject("SELECT statut FROM exemplaire WHERE id = ?", String.class, exemplaireId);
    }

    int nombreEmprunts() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM emprunt", Integer.class);
    }

    /**
     * MockMvc ne transforme pas une exception non gérée en erreur 500 : il la relance dans le test.
     * Pour figer ce que reçoit VRAIMENT un client dans ce cas, on passe par un vrai appel HTTP.
     */
    int codeHttpReel(String url) {
        return http.postForEntity(url, null, String.class).getStatusCode().value();
    }

    SimpleMailMessage emailEnvoye() {
        var captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        return captor.getValue();
    }

    // ---------- POST /api/emprunts ----------

    @Nested
    class Emprunter {

        // ✅ MODÈLE 1 : un emprunt accepté — réponse, état en base
        @Test
        void un_etudiant_emprunte_pour_21_jours() throws Exception {                     // R4
            adherent(1, "Alice", "ETUDIANT", null);
            exemplaire(10, "9782070368228", "DISPONIBLE");

            mvc.perform(post("/api/emprunts").param("adherentId", "1").param("exemplaireId", "10"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.retourPrevu").value(AUJOURDHUI.plusDays(21).toString()));

            assertThat(nombreEmprunts()).isEqualTo(1);
            assertThat(statutDe(10)).isEqualTo("EMPRUNTE");
        }

        // ✅ MODÈLE 2 : un emprunt refusé — code HTTP, message exact, rien n'a changé
        @Test
        void un_quatrieme_emprunt_est_refuse() throws Exception {                        // R1
            adherent(1, "Chloé", "STANDARD", null);
            exemplaire(11, "9782070368228", "EMPRUNTE");
            exemplaire(12, "9782266320986", "EMPRUNTE");
            exemplaire(13, "9782253004226", "EMPRUNTE");
            exemplaire(10, "9782070413478", "DISPONIBLE");
            empruntEnCours(1, 11, AUJOURDHUI.plusDays(5));
            empruntEnCours(1, 12, AUJOURDHUI.plusDays(5));
            empruntEnCours(1, 13, AUJOURDHUI.plusDays(5));

            mvc.perform(post("/api/emprunts").param("adherentId", "1").param("exemplaireId", "10"))
               .andExpect(status().isConflict())
               .andExpect(content().string("Maximum 3 emprunts"));

            assertThat(nombreEmprunts()).isEqualTo(3);
            assertThat(statutDe(10)).isEqualTo("DISPONIBLE");
            verify(mailSender, never()).send(any(SimpleMailMessage.class));
        }

        // TODO : un adhérent standard emprunte pour 14 jours (R4)
        // TODO : un email de confirmation est envoyé (destinataire, sujet, texte EXACT) → voir emailEnvoye()
        // TODO : un adhérent suspendu est refusé (R2)
        // TODO : un exemplaire déjà emprunté est refusé (R3)
        // TODO : un adhérent inconnu… que répond le legacy ? Figez-le, même si c'est bizarre (utilisez codeHttpReel).
    }

    // ---------- POST /api/retours ----------

    @Nested
    class Rendre {

        // TODO : un retour à l'heure libère l'exemplaire, sans suspension
        // TODO : 4 jours de retard donnent 4 jours de suspension (R5)
        // TODO : la suspension est plafonnée à 30 jours (R5)
        // TODO : un titre réservé est mis de côté et le PREMIER réservataire est prévenu (R6, R7)
        // TODO : rendre un exemplaire que personne n'a emprunté… que répond le legacy ? (codeHttpReel)
    }
}
