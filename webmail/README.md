# UGE Webmail

Système de gestion de boîtes de courrier électronique avec Vert.x 5.

## 📋 Description

Ce projet est une application de messagerie web développée avec le framework Vert.x 5 en Java 17. Elle permet aux utilisateurs de :

- Créer un compte et s'authentifier de manière sécurisée
- Envoyer et recevoir des courriers électroniques
- Gérer des brouillons
- Organiser ses messages (boîte de réception, messages envoyés, corbeille)
- Ajouter des pièces jointes aux messages
- Recevoir des messages anonymes via UDP
- Filtrer et rechercher les messages

## 🚀 Fonctionnalités

### Gestion des comptes
- Création de compte avec validation de mot de passe fort
- Connexion avec gestion de sessions (cookies)
- Code de récupération en cas de perte de mot de passe
- Changement de mot de passe

### Messagerie
- Composition et envoi de messages
- Réponse aux messages
- Sauvegarde de brouillons
- Pièces jointes (avec déduplication SHA-256)
- Tags personnalisables (important, non lu, etc.)
- Purge automatique de la corbeille (30 jours)

### Messages anonymes (UDP)
- Réception de messages anonymes sur le port UDP 9999
- Limite de 10 messages/jour par adresse IP
- Format : `destinataire\nsujet\ncontenu`

## 🛠️ Prérequis

- Java 17 ou supérieur
- Maven 3.8+
- IntelliJ IDEA (recommandé) ou autre IDE Java

## 📦 Installation et exécution

### Cloner le projet
```bash
git clone <url-du-repo>
cd webmail
```

### Compiler le projet
```bash
./mvnw clean compile
```

### Lancer l'application
```bash
./mvnw clean compile exec:java
```

L'application sera accessible sur :
- **HTTP** : http://localhost:8080
- **UDP** : port 9999 (messages anonymes)

### Créer un JAR exécutable
```bash
./mvnw clean package
java -jar target/webmail-1.0.0-SNAPSHOT-fat.jar
```

### Lancer les tests
```bash
./mvnw clean test
```

## 📁 Structure du projet

```
webmail/
├── src/
│   ├── main/
│   │   ├── java/fr/uge/webmail/
│   │   │   ├── MainVerticle.java       # Point d'entrée de l'application
│   │   │   ├── model/                  # Classes de modèle
│   │   │   │   ├── Email.java
│   │   │   │   ├── Attachment.java
│   │   │   │   └── User.java
│   │   │   ├── service/                # Services métier
│   │   │   │   ├── MailboxService.java
│   │   │   │   ├── UserService.java
│   │   │   │   └── UdpService.java
│   │   │   └── util/                   # Utilitaires
│   │   │       ├── PasswordUtils.java
│   │   │       └── FileUtils.java
│   │   └── resources/
│   │       ├── templates/              # Templates Pebble
│   │       │   ├── layout.peb
│   │       │   ├── login.peb
│   │       │   ├── register.peb
│   │       │   ├── folder.peb
│   │       │   ├── compose.peb
│   │       │   ├── view_email.peb
│   │       │   └── settings.peb
│   │       └── webroot/                # Fichiers statiques
│   │           └── css/
│   │               └── style.css
│   └── test/
│       └── java/fr/uge/webmail/
│           └── TestMainVerticle.java
├── data/                               # Données de l'application
│   ├── mailboxes/                      # Boîtes mail des utilisateurs
│   └── attachments/                    # Pièces jointes (par hash SHA-256)
├── pom.xml
├── README.md
└── doc.pdf
```

## 🔒 Sécurité

### Mots de passe
Les mots de passe doivent respecter les critères suivants :
- Au moins 8 caractères
- Au moins une lettre majuscule
- Au moins une lettre minuscule
- Au moins un chiffre
- Au moins un caractère spécial

Les mots de passe sont stockés sous forme de hash SHA-256 avec sel unique.

### Sessions
- Sessions basées sur des tokens aléatoires sécurisés
- Durée par défaut : 1 heure (24 heures avec "Se souvenir de moi")
- Nettoyage automatique des sessions expirées

### Code de récupération
Un code de récupération unique est généré à la création du compte. Ce code permet de réinitialiser le mot de passe en cas de perte.

## 📡 API UDP

Format des messages UDP :
```
destinataire
sujet du message
contenu
du
message
```

Exemple avec netcat :
```bash
echo -e "alice\nBonjour\nCeci est un message anonyme" | nc -u localhost 9999
```

Réponses possibles :
- `OK: Message envoyé à <destinataire>`
- `ERREUR: Destinataire '<nom>' non trouvé.`
- `ERREUR: Limite de 10 messages/jour atteinte pour cette IP.`
- `ERREUR: Format invalide.`

## 🧪 Tests

Pour tester l'application :

1. Lancez le serveur
2. Créez deux comptes utilisateurs (ex: "alice" et "bob")
3. Connectez-vous avec "alice" et envoyez un message à "bob"
4. Connectez-vous avec "bob" pour voir le message reçu
5. Testez l'envoi de messages anonymes via UDP

## 👥 Auteurs

- [Nom étudiant 1]
- [Nom étudiant 2]
- [Nom étudiant 3]

## 📝 Licence

Projet universitaire - UGE 2025
