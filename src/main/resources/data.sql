-- Données de démonstration, chargées à chaque démarrage. Les identifiants sont attribués dans l'ordre : 1, 2, 3…
INSERT INTO adherent (nom, prenom, email, categorie, suspendu_jusqu_au) VALUES
    ('Martin',  'Alice',  'alice.martin@example.org',  'ETUDIANT', NULL),              -- 1
    ('Durand',  'Bruno',  'bruno.durand@example.org',  'STANDARD', NULL),              -- 2
    ('Petit',   'Chloé',  'chloe.petit@example.org',   'STANDARD', NULL),              -- 3 : déjà 3 emprunts
    ('Moreau',  'Damien', 'damien.moreau@example.org', 'STANDARD', CURRENT_DATE + INTERVAL '5' DAY);  -- 4 : suspendu

INSERT INTO exemplaire (isbn, titre, statut) VALUES
    ('9782070368228', 'L''Étranger',                'DISPONIBLE'),  -- 1
    ('9782266320986', 'Dune',                       'EMPRUNTE'),    -- 2 : seul exemplaire de Dune
    ('9782070413478', 'Germinal',                   'DISPONIBLE'),  -- 3
    ('9782253004226', 'Le Petit Prince',            'EMPRUNTE'),    -- 4
    ('9782070413119', 'Le Seigneur des anneaux',    'EMPRUNTE'),    -- 5
    ('9782290349595', 'Fondation',                  'EMPRUNTE');    -- 6

INSERT INTO emprunt (adherent_id, exemplaire_id, date_emprunt, date_retour_prevue) VALUES
    (3, 4, CURRENT_DATE - INTERVAL '3' DAY,  CURRENT_DATE + INTERVAL '11' DAY),
    (3, 5, CURRENT_DATE - INTERVAL '3' DAY,  CURRENT_DATE + INTERVAL '11' DAY),
    (3, 6, CURRENT_DATE - INTERVAL '18' DAY, CURRENT_DATE - INTERVAL '4' DAY),    -- en retard de 4 jours
    (2, 2, CURRENT_DATE - INTERVAL '10' DAY, CURRENT_DATE + INTERVAL '4' DAY);

-- Alice attend Dune, actuellement emprunté par Bruno
INSERT INTO reservation (adherent_id, isbn, date_reservation) VALUES
    (1, '9782266320986', CURRENT_TIMESTAMP);
