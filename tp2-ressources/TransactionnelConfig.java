package com.mediatech.emprunt.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * TP2 — étape 4. À copier dans src/main/java/com/mediatech/emprunt/config/
 *
 * Fournit un TransactionTemplate : un objet qui exécute du code DANS une transaction
 * (tout est validé à la fin, ou tout est annulé en cas d'exception).
 *
 * Utilisation dans votre MediatechConfig, pour envelopper un cas d'usage SANS annoter le service :
 *
 *   @Bean
 *   EmprunterDocument emprunterDocument(..., TransactionTemplate transaction) {
 *       var service = new EmprunterDocumentService(...);
 *       return commande -> transaction.execute(statut -> service.executer(commande));
 *   }
 *
 * C'est un décorateur (« Wrap ») : le service reste du Java pur, la transaction est ajoutée AUTOUR.
 */
@Configuration
public class TransactionnelConfig {

    @Bean
    TransactionTemplate transactionTemplate(PlatformTransactionManager gestionnaire) {
        return new TransactionTemplate(gestionnaire);
    }
}
