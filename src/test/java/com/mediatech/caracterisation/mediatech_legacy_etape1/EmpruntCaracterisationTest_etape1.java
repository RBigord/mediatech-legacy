package com.mediatech.caracterisation.mediatech_legacy_etape1;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests de CARACTÉRISATION : ils figent le comportement ACTUEL de l'API, bizarreries comprises.
 * Ils passent sur le legacy… et doivent passer, SANS MODIFICATION, après le refactoring.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)   // un vrai serveur, sur un port libre
@AutoConfigureMockMvc
class EmpruntCaracterisationTest_etape1 {

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
        @Test
        void un_adherent_standard_emprunte_pour_14_jours() throws Exception {            // R4
            adherent(1, "Bruno", "STANDARD", null);
            exemplaire(10, "9782070368228", "DISPONIBLE");

            mvc.perform(post("/api/emprunts").param("adherentId", "1").param("exemplaireId", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.retourPrevu").value(AUJOURDHUI.plusDays(14).toString()));
        }

        // TODO : un adhérent suspendu est refusé (R2)
        @Test
        void un_adherent_suspendu_est_refuse() throws Exception {                        // R2
            adherent(1, "Damien", "STANDARD", AUJOURDHUI.plusDays(5));   // suspendu encore 5 jours
            exemplaire(10, "9782070368228", "DISPONIBLE");

            mvc.perform(post("/api/emprunts").param("adherentId", "1").param("exemplaireId", "10"))
                    // TODO : le code HTTP  → status().is...()
                    .andExpect(status().isForbidden())
                    // TODO : le message    → content().string("...")
                    .andExpect(content().string("Adhérent suspendu"));

            // TODO : aucun emprunt n'a été créé
            assertThat(nombreEmprunts()).isZero();
        }

        // TODO : un exemplaire déjà emprunté est refusé (R3)
        @Test
        void un_exemplaire_deja_emprunte_est_refuse() throws Exception {                 // R3
            adherent(1, "Bruno", "STANDARD", null);
            exemplaire(10, "9782070368228", "EMPRUNTE");

            mvc.perform(post("/api/emprunts").param("adherentId", "1").param("exemplaireId", "10"))
                    .andExpect(status().isConflict())
                    .andExpect(content().string("Exemplaire indisponible"));

            assertThat(nombreEmprunts()).isZero();
            verify(mailSender, never()).send(any(SimpleMailMessage.class));
        }

        // TODO : un email de confirmation est envoyé (destinataire, sujet, texte EXACT) → voir emailEnvoye()
        @Test
        void un_email_de_confirmation_est_envoye() throws Exception {
            adherent(1, "Alice", "ETUDIANT", null);
            exemplaire(10, "9782070368228", "DISPONIBLE");

            mvc.perform(post("/api/emprunts").param("adherentId", "1").param("exemplaireId", "10"));

            SimpleMailMessage email = emailEnvoye();               // l'email que le legacy a voulu envoyer
            assertThat(email.getTo()).containsExactly("alice@example.org");
            // TODO : le sujet  → assertThat(email.getSubject()).isEqualTo("...");
            assertThat(email.getSubject()).isEqualTo("Confirmation d'emprunt");
            // TODO : le texte  → assertThat(email.getText()).isEqualTo("..." + ...);
            assertThat(email.getText())
                    .isEqualTo("Bonjour Alice, document à rendre avant le " + AUJOURDHUI.plusDays(21));
        }

        // TODO : un adhérent inconnu… que répond le legacy ? Figez-le, même si c'est bizarre (utilisez codeHttpReel).
        @Test
        void adherent_inconnu() throws Exception{
            exemplaire(10, "9782070368228", "DISPONIBLE");

            assertThat(codeHttpReel("/api/emprunts?adherentId=99&exemplaireId=10")).isEqualTo(500);
        }
    }

    // ---------- POST /api/retours ----------

    @Nested
    class Rendre {
        // TODO : un retour à l'heure libère l'exemplaire, sans suspension
        @Test
        void un_retour_a_l_heure_libere_l_exemplaire_sans_suspension() throws Exception {
            adherent(1, "Alice", "ETUDIANT", null);
            exemplaire(10, "9782070368228", "EMPRUNTE");
            empruntEnCours(1, 10, AUJOURDHUI);                       // à rendre aujourd'hui : pas de retard

            mvc.perform(post("/api/retours").param("exemplaireId", "10"))
                    .andExpect(status().isOk());

            // TODO : le statut de l'exemplaire 10 → statutDe(...)
            assertThat(statutDe(10)).isEqualTo("DISPONIBLE");
            assertThat(jdbc.queryForObject("SELECT date_retour FROM emprunt", LocalDate.class))
                    .isEqualTo(AUJOURDHUI);
            assertThat(jdbc.queryForObject("SELECT suspendu_jusqu_au FROM adherent WHERE id = 1", LocalDate.class))
                    .isNull();
        }

        // TODO : 4 jours de retard donnent 4 jours de suspension (R5)
        @Test
        void quatre_jours_de_retard_donnent_quatre_jours_de_suspension() throws Exception {   // R5
            adherent(1, "Alice", "ETUDIANT", null);
            exemplaire(10, "9782070368228", "EMPRUNTE");
            empruntEnCours(1, 10, AUJOURDHUI.minusDays(4));

            mvc.perform(post("/api/retours").param("exemplaireId", "10"))
                    .andExpect(status().isOk());

            assertThat(jdbc.queryForObject("SELECT suspendu_jusqu_au FROM adherent WHERE id = 1", LocalDate.class))
                    .isEqualTo(AUJOURDHUI.plusDays(4));
        }

        // TODO : la suspension est plafonnée à 30 jours (R5)
        @Test
        void la_suspension_est_plafonnee_a_30_jours() throws Exception {                 // R5
            adherent(1, "Alice", "ETUDIANT", null);
            exemplaire(10, "9782070368228", "EMPRUNTE");
            empruntEnCours(1, 10, AUJOURDHUI.minusDays(45));

            mvc.perform(post("/api/retours").param("exemplaireId", "10"))
                    .andExpect(status().isOk());

            assertThat(jdbc.queryForObject("SELECT suspendu_jusqu_au FROM adherent WHERE id = 1", LocalDate.class))
                    .isEqualTo(AUJOURDHUI.plusDays(30));
        }

        // TODO : un titre réservé est mis de côté et le PREMIER réservataire est prévenu (R6, R7)
        @Test
        void un_titre_reserve_est_mis_de_cote_et_le_premier_reservataire_prevenu() throws Exception {   // R6, R7
            adherent(1, "Bruno", "STANDARD", null);
            adherent(2, "Alice", "ETUDIANT", null);
            adherent(3, "Chloé", "STANDARD", null);
            exemplaire(10, "9782070368228", "EMPRUNTE");
            empruntEnCours(1, 10, AUJOURDHUI.plusDays(3));
            // Piège volontaire : Chloé est insérée AVANT Alice, mais Alice a réservé PLUS TÔT
            reservation(3, "9782070368228", LocalDateTime.now().minusDays(1));
            reservation(2, "9782070368228", LocalDateTime.now().minusDays(2));

            mvc.perform(post("/api/retours").param("exemplaireId", "10"))
                    .andExpect(status().isOk());

            // TODO : le statut de l'exemplaire
            assertThat(statutDe(10)).isEqualTo("RESERVE");
            // TODO : l'email : destinataire, sujet, texte exacts (comme au test 4)
            SimpleMailMessage email = emailEnvoye();
            assertThat(email.getTo()).containsExactly("alice@example.org");
            assertThat(email.getSubject()).isEqualTo("Votre réservation est disponible");
            assertThat(email.getText()).isEqualTo("Bonjour Alice, votre document vous attend pendant 3 jours.");


        }

        // TODO : rendre un exemplaire que personne n'a emprunté… que répond le legacy ? (codeHttpReel)
        @Test
        void rendre_un_exemplaire_non_emprunte_provoque_une_erreur_500() {
            exemplaire(10, "9782070368228", "DISPONIBLE");

            assertThat(codeHttpReel("/api/retours?exemplaireId=10")).isEqualTo(500);
        }
    }
}
