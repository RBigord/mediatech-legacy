# MédiaTech — application legacy

L'application de gestion des prêts de la médiathèque de Villeneuve, version 1.
Elle fonctionne… mais plus personne n'ose la modifier.

## Lancer l'application

Rien à installer : la base de données (H2) est en mémoire et les emails s'affichent dans la console.

Dans IntelliJ, ouvrez `MediatechApplication` et cliquez sur le **triangle vert** à côté de `main`
(ou onglet **Maven → Plugins → spring-boot → spring-boot:run**).

| Quoi | Où |
|---|---|
| Essayer l'API (Swagger) | http://127.0.0.1:8080/swagger-ui.html |
| Voir les tables (console H2) | http://127.0.0.1:8080/h2-console — JDBC URL : `jdbc:h2:mem:mediatech`, utilisateur `sa`, pas de mot de passe |
| Voir les emails envoyés | la console d'IntelliJ (encadrés « 📧 EMAIL (simulé) ») |

La base est **recréée à chaque démarrage** : relancez l'application pour retrouver les données de départ.


> ⚠️ Utilisez **127.0.0.1** et pas `localhost` : avec `localhost`, le navigateur peut envoyer trop de cookies (laissés par d'autres projets) et l'application répond **400 Bad Request**.

## Essayer l'API

Dans Swagger, ouvrez `emprunt-controller`, choisissez une opération, cliquez sur **Try it out**,
remplissez les paramètres puis **Execute** :

| Opération | Paramètres | Ce qui se passe |
|---|---|---|
| `POST /api/emprunts` | adherentId = 1, exemplaireId = 3 | Alice emprunte Germinal → 200 |
| `POST /api/emprunts` | adherentId = 3, exemplaireId = 1 | Chloé a déjà 3 emprunts → 409 |
| `POST /api/emprunts` | adherentId = 4, exemplaireId = 1 | Damien est suspendu → 403 |
| `POST /api/retours` | exemplaireId = 2 | Bruno rend Dune, réservé par Alice → regardez l'email dans la console |

## Lancer les tests

Onglet **Maven → Lifecycle → test**, ou triangle vert à côté d'une classe de test.

## Les données de démonstration

| Adhérent | Situation |
|---|---|
| 1 — Alice | Étudiante, aucun emprunt, a réservé *Dune* |
| 2 — Bruno | Standard, a emprunté *Dune* (exemplaire 2) |
| 3 — Chloé | Standard, 3 emprunts dont un en retard |
| 4 — Damien | Standard, suspendu pour encore 5 jours |
