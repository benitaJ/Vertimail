# Documentation Technique - UGE Webmail

## 1. Introduction

Ce document présente l'architecture technique, les choix de conception et les difficultés rencontrées lors du développement du projet UGE Webmail, un système de gestion de boîtes de courrier électronique.

## 2. Architecture du projet

### 2.1 Vue d'ensemble

L'application suit une architecture en couches classique :

```
┌─────────────────────────────────────────────────────────┐
│                    Interface Web                         │
│              (Templates Pebble + CSS)                   │
├─────────────────────────────────────────────────────────┤
│                    MainVerticle                          │
│              (Routage HTTP + Contrôleurs)               │
├─────────────────────────────────────────────────────────┤
│                     Services                             │
│    MailboxService │ UserService │ UdpService            │
├─────────────────────────────────────────────────────────┤
│                      Modèles                             │
│           Email │ User │ Attachment                      │
├─────────────────────────────────────────────────────────┤
│                    Stockage                              │
│              (Fichiers JSON + SHA-256)                  │
└─────────────────────────────────────────────────────────┘
```

### 2.2 Packages Java

- **fr.uge.webmail** : Classe principale (MainVerticle)
- **fr.uge.webmail.model** : Classes de données (Email, User, Attachment)
- **fr.uge.webmail.service** : Logique métier (MailboxService, UserService, UdpService)
- **fr.uge.webmail.util** : Utilitaires (PasswordUtils, FileUtils)

## 3. Choix techniques

### 3.1 Framework Vert.x 5

Nous avons choisi Vert.x 5 pour :
- Sa nature asynchrone et non-bloquante
- Sa gestion native des WebSockets et UDP
- Son intégration avec le moteur de templates Pebble
- Ses performances élevées

### 3.2 Stockage en fichiers JSON

Les emails sont stockés au format JSON dans des fichiers individuels. Ce choix offre :
- Simplicité de mise en œuvre
- Lisibilité des données
- Facilité de sauvegarde et migration
- Pas de dépendance à une base de données

Structure des fichiers :
```
data/
├── mailboxes/
│   └── <username>/
│       ├── user.json        # Informations utilisateur
│       ├── salt.txt         # Sel pour le hachage
│       ├── inbox/           # Messages reçus
│       │   └── <id>.json
│       ├── outbox/          # Messages envoyés
│       ├── draft/           # Brouillons
│       └── trash/           # Corbeille
└── attachments/
    └── <sha256>             # Pièces jointes (déduplication)
```

### 3.3 Sécurité des mots de passe

- **Algorithme** : SHA-256 avec sel unique par utilisateur
- **Sel** : 16 bytes aléatoires (SecureRandom)
- **Validation** : 8 caractères min, majuscule, minuscule, chiffre, caractère spécial
- **Récupération** : Code de récupération généré à la création du compte

### 3.4 Gestion des sessions

- Tokens de session aléatoires (32 bytes)
- Stockage en mémoire (ConcurrentHashMap)
- Expiration configurable (1h par défaut, 24h avec "Se souvenir de moi")
- Nettoyage périodique des sessions expirées

### 3.5 Pièces jointes

Les pièces jointes sont stockées avec leur hash SHA-256 comme nom de fichier. Cette approche permet :
- **Déduplication** : Un même fichier n'est stocké qu'une fois
- **Intégrité** : Le hash garantit que le fichier n'a pas été modifié
- **Performances** : Pas de duplication inutile d'espace disque

### 3.6 Messages UDP

Le service UDP écoute sur le port 9999 et applique :
- Limite de 10 messages/jour par adresse IP
- Validation du format du message
- Vérification de l'existence du destinataire
- Réponse de confirmation/erreur à l'expéditeur

## 4. Fonctionnalités implémentées

### 4.1 Gestion des comptes
- [x] Création de compte avec validation
- [x] Connexion avec sessions
- [x] Code de récupération
- [x] Changement de mot de passe
- [x] Déconnexion

### 4.2 Messagerie
- [x] Composition de messages
- [x] Envoi à plusieurs destinataires
- [x] Sauvegarde de brouillons
- [x] Pièces jointes multiples
- [x] Réponse aux messages
- [x] Suppression (corbeille puis définitif)
- [x] Purge automatique de la corbeille (30 jours)

### 4.3 Organisation
- [x] Tags personnalisables
- [x] Tag "important"
- [x] Tag "non lu" (automatique)
- [x] Filtrage par expéditeur/destinataire/sujet
- [x] Calcul de l'espace disque utilisé

### 4.4 Messages anonymes
- [x] Réception UDP
- [x] Limite par IP
- [x] Validation et réponse

## 5. Difficultés rencontrées

### 5.1 Gestion des sessions avec Vert.x 5

Vert.x 5 a introduit des changements dans l'API par rapport à Vert.x 4. Notamment :
- `VerticleBase` remplace `AbstractVerticle`
- La méthode `start()` retourne maintenant un `Future<?>`
- Certaines méthodes sont désormais asynchrones

**Solution** : Consultation de la documentation Vert.x 5 et adaptation du code.

### 5.2 Upload de fichiers multiples

L'upload de plusieurs fichiers simultanément nécessite une configuration spécifique du BodyHandler.

**Solution** : Configuration du `BodyHandler` avec :
- `setUploadsDirectory("uploads")`
- `setBodyLimit(50 * 1024 * 1024)` pour 50 MB

### 5.3 Encodage des caractères

Les fichiers JSON et les templates doivent gérer correctement les caractères spéciaux français.

**Solution** : Utilisation systématique de `StandardCharsets.UTF_8` et spécification de `charset=UTF-8` dans les headers HTTP.

### 5.4 Concurrence dans les accès fichiers

L'accès concurrent aux fichiers peut poser des problèmes de cohérence.

**Solution** : Utilisation de `ConcurrentHashMap` pour les sessions et lecture/écriture atomique des fichiers JSON avec Jackson.

## 6. Organisation du travail

### 6.1 Répartition des tâches

| Membre | Responsabilités |
|--------|-----------------|
| Étudiant 1 | Architecture, Services, MainVerticle |
| Étudiant 2 | Templates Pebble, CSS, Interface utilisateur |
| Étudiant 3 | Service UDP, Tests, Documentation |

### 6.2 Outils utilisés

- **IDE** : IntelliJ IDEA 2025.2.1 (Community Edition)
- **Gestion de version** : Git
- **Build** : Maven 3.x
- **Tests** : JUnit 5 + Vert.x Test

### 6.3 Planning

| Semaine | Tâches réalisées |
|---------|------------------|
| 1 | Mise en place du projet, architecture de base |
| 2 | Services MailboxService et UserService |
| 3 | Routes HTTP et templates Pebble |
| 4 | Service UDP et messages anonymes |
| 5 | Tests, corrections de bugs, documentation |

## 7. Améliorations possibles

- Chiffrement des emails stockés
- Support IMAP/SMTP pour recevoir/envoyer des emails externes
- Interface d'administration
- Quotas par utilisateur
- Recherche full-text dans le contenu des messages
- Support des dossiers personnalisés

## 8. Conclusion

Ce projet nous a permis de mettre en pratique les concepts de développement web avec Vert.x et Java. L'architecture en couches facilite la maintenance et l'évolution du code. Le stockage en fichiers JSON, bien que simple, répond aux besoins du projet sans nécessiter de base de données.

Les principales compétences acquises :
- Développement asynchrone avec Vert.x
- Gestion de la sécurité (hachage, sessions)
- Protocole UDP pour les messages anonymes
- Moteur de templates Pebble
- Organisation d'un projet en équipe
