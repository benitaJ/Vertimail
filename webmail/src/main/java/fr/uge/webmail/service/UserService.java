package fr.uge.webmail.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import fr.uge.webmail.model.User;
import fr.uge.webmail.util.FileUtils;
import fr.uge.webmail.util.PasswordUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service pour gérer les utilisateurs et l'authentification.
 */
public class UserService {
    
    private final Path dataDirectory;
    private final MailboxService mailboxService;
    private final ObjectMapper objectMapper;
    
    // Sessions actives : token -> username
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    
    // Fichiers de configuration utilisateur
    private static final String USER_FILE = "user.json";
    private static final String SALT_FILE = "salt.txt";
    
    // Durées de session par défaut
    public static final int DEFAULT_SESSION_DURATION_MINUTES = 60;
    public static final int EXTENDED_SESSION_DURATION_MINUTES = 24 * 60; // 24 heures
    
    public UserService(Path dataDirectory, MailboxService mailboxService) {
        this.dataDirectory = dataDirectory.resolve("mailboxes");
        this.mailboxService = mailboxService;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
    
    /**
     * Crée un nouvel utilisateur.
     * @return Le code de récupération à conserver
     */
    public String createUser(String username, String password) throws IOException {
        // Valider le nom d'utilisateur
        if (!FileUtils.isValidUsername(username)) {
            throw new IllegalArgumentException("Nom d'utilisateur invalide. Utilisez uniquement des lettres, chiffres, tirets et underscores (3-32 caractères).");
        }
        
        // Vérifier si l'utilisateur existe déjà
        if (userExists(username)) {
            throw new IllegalArgumentException("Ce nom d'utilisateur est déjà pris.");
        }
        
        // Valider le mot de passe
        var validation = PasswordUtils.validatePassword(password);
        if (!validation.isValid()) {
            throw new IllegalArgumentException("Mot de passe non valide : " + validation.getErrorsAsString());
        }
        
        // Créer la boîte mail
        mailboxService.createMailbox(username);
        
        // Générer le sel et le code de récupération
        String salt = PasswordUtils.generateSalt();
        String passwordHash = PasswordUtils.hashPassword(password, salt);
        String recoveryCode = PasswordUtils.generateRecoveryCode();
        
        // Créer l'utilisateur
        User user = new User(username, passwordHash, recoveryCode);
        
        // Sauvegarder les fichiers
        Path userDir = dataDirectory.resolve(username);
        Files.writeString(userDir.resolve(SALT_FILE), salt);
        objectMapper.writeValue(userDir.resolve(USER_FILE).toFile(), user);
        
        return recoveryCode;
    }
    
    /**
     * Vérifie si un utilisateur existe.
     */
    public boolean userExists(String username) {
        Path userFile = dataDirectory.resolve(username).resolve(USER_FILE);
        return Files.exists(userFile);
    }
    
    /**
     * Charge un utilisateur depuis le système de fichiers.
     */
    public Optional<User> loadUser(String username) throws IOException {
        Path userFile = dataDirectory.resolve(username).resolve(USER_FILE);
        if (!Files.exists(userFile)) {
            return Optional.empty();
        }
        User user = objectMapper.readValue(userFile.toFile(), User.class);
        return Optional.of(user);
    }
    
    /**
     * Charge le sel d'un utilisateur.
     */
    private String loadSalt(String username) throws IOException {
        Path saltFile = dataDirectory.resolve(username).resolve(SALT_FILE);
        if (!Files.exists(saltFile)) {
            throw new IOException("Fichier de sel non trouvé pour " + username);
        }
        return Files.readString(saltFile).trim();
    }
    
    /**
     * Authentifie un utilisateur et crée une session.
     * @return Le token de session si l'authentification réussit
     */
    public Optional<String> authenticate(String username, String password, int sessionDurationMinutes) {
        try {
            Optional<User> userOpt = loadUser(username);
            if (userOpt.isEmpty()) {
                return Optional.empty();
            }
            
            User user = userOpt.get();
            String salt = loadSalt(username);
            
            if (PasswordUtils.verifyPassword(password, salt, user.getPasswordHash())) {
                // Mettre à jour la dernière connexion
                user.updateLastLogin();
                objectMapper.writeValue(dataDirectory.resolve(username).resolve(USER_FILE).toFile(), user);
                
                // Créer une session
                String token = PasswordUtils.generateSessionToken();
                LocalDateTime expiry = LocalDateTime.now().plusMinutes(sessionDurationMinutes);
                sessions.put(token, new Session(username, expiry));
                
                return Optional.of(token);
            }
        } catch (IOException e) {
            System.err.println("Erreur d'authentification : " + e.getMessage());
        }
        
        return Optional.empty();
    }
    
    /**
     * Vérifie si une session est valide et retourne le nom d'utilisateur.
     */
    public Optional<String> validateSession(String token) {
        if (token == null || token.isEmpty()) {
            return Optional.empty();
        }
        
        Session session = sessions.get(token);
        if (session == null) {
            return Optional.empty();
        }
        
        if (session.isExpired()) {
            sessions.remove(token);
            return Optional.empty();
        }
        
        return Optional.of(session.username());
    }
    
    /**
     * Déconnecte un utilisateur (supprime la session).
     */
    public void logout(String token) {
        sessions.remove(token);
    }
    
    /**
     * Change le mot de passe d'un utilisateur.
     */
    public void changePassword(String username, String oldPassword, String newPassword) throws IOException {
        // Vérifier l'ancien mot de passe
        Optional<User> userOpt = loadUser(username);
        if (userOpt.isEmpty()) {
            throw new IllegalArgumentException("Utilisateur non trouvé");
        }
        
        User user = userOpt.get();
        String salt = loadSalt(username);
        
        if (!PasswordUtils.verifyPassword(oldPassword, salt, user.getPasswordHash())) {
            throw new IllegalArgumentException("Ancien mot de passe incorrect");
        }
        
        // Valider le nouveau mot de passe
        var validation = PasswordUtils.validatePassword(newPassword);
        if (!validation.isValid()) {
            throw new IllegalArgumentException("Nouveau mot de passe non valide : " + validation.getErrorsAsString());
        }
        
        // Générer un nouveau sel et hasher le nouveau mot de passe
        String newSalt = PasswordUtils.generateSalt();
        String newPasswordHash = PasswordUtils.hashPassword(newPassword, newSalt);
        
        // Mettre à jour l'utilisateur
        user.setPasswordHash(newPasswordHash);
        
        // Sauvegarder les modifications
        Path userDir = dataDirectory.resolve(username);
        Files.writeString(userDir.resolve(SALT_FILE), newSalt);
        objectMapper.writeValue(userDir.resolve(USER_FILE).toFile(), user);
    }
    
    /**
     * Réinitialise le mot de passe avec le code de récupération.
     */
    public void resetPasswordWithRecoveryCode(String username, String recoveryCode, String newPassword) throws IOException {
        Optional<User> userOpt = loadUser(username);
        if (userOpt.isEmpty()) {
            throw new IllegalArgumentException("Utilisateur non trouvé");
        }
        
        User user = userOpt.get();
        
        // Vérifier le code de récupération
        if (!user.getRecoveryCode().equals(recoveryCode)) {
            throw new IllegalArgumentException("Code de récupération incorrect");
        }
        
        // Valider le nouveau mot de passe
        var validation = PasswordUtils.validatePassword(newPassword);
        if (!validation.isValid()) {
            throw new IllegalArgumentException("Nouveau mot de passe non valide : " + validation.getErrorsAsString());
        }
        
        // Générer un nouveau sel et hasher le nouveau mot de passe
        String newSalt = PasswordUtils.generateSalt();
        String newPasswordHash = PasswordUtils.hashPassword(newPassword, newSalt);
        
        // Générer un nouveau code de récupération
        String newRecoveryCode = PasswordUtils.generateRecoveryCode();
        
        // Mettre à jour l'utilisateur
        user.setPasswordHash(newPasswordHash);
        user.setRecoveryCode(newRecoveryCode);
        
        // Sauvegarder les modifications
        Path userDir = dataDirectory.resolve(username);
        Files.writeString(userDir.resolve(SALT_FILE), newSalt);
        objectMapper.writeValue(userDir.resolve(USER_FILE).toFile(), user);
    }
    
    /**
     * Nettoie les sessions expirées.
     */
    public void cleanupExpiredSessions() {
        sessions.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }
    
    /**
     * Représente une session utilisateur.
     */
    private record Session(String username, LocalDateTime expiry) {
        boolean isExpired() {
            return LocalDateTime.now().isAfter(expiry);
        }
    }
}
