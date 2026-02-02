package fr.uge.webmail.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Utilitaires pour la gestion des mots de passe.
 */
public final class PasswordUtils {
    
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int MIN_PASSWORD_LENGTH = 8;
    
    private PasswordUtils() {
        // Classe utilitaire
    }
    
    /**
     * Génère un hash SHA-256 du mot de passe avec un sel.
     */
    public static String hashPassword(String password, String salt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String saltedPassword = salt + password;
            byte[] hash = digest.digest(saltedPassword.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 non disponible", e);
        }
    }
    
    /**
     * Génère un sel aléatoire.
     */
    public static String generateSalt() {
        byte[] salt = new byte[16];
        SECURE_RANDOM.nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }
    
    /**
     * Génère un code de récupération aléatoire.
     */
    public static String generateRecoveryCode() {
        byte[] code = new byte[32];
        SECURE_RANDOM.nextBytes(code);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(code);
    }
    
    /**
     * Génère un token de session aléatoire.
     */
    public static String generateSessionToken() {
        byte[] token = new byte[32];
        SECURE_RANDOM.nextBytes(token);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(token);
    }
    
    /**
     * Vérifie si un mot de passe est suffisamment fort.
     * Critères :
     * - Au moins 8 caractères
     * - Au moins une majuscule
     * - Au moins une minuscule
     * - Au moins un chiffre
     * - Au moins un caractère spécial
     */
    public static PasswordValidation validatePassword(String password) {
        List<String> errors = new ArrayList<>();
        
        if (password == null || password.isEmpty()) {
            errors.add("Le mot de passe ne peut pas être vide");
            return new PasswordValidation(false, errors);
        }
        
        if (password.length() < MIN_PASSWORD_LENGTH) {
            errors.add("Le mot de passe doit contenir au moins " + MIN_PASSWORD_LENGTH + " caractères");
        }
        
        if (!password.matches(".*[A-Z].*")) {
            errors.add("Le mot de passe doit contenir au moins une lettre majuscule");
        }
        
        if (!password.matches(".*[a-z].*")) {
            errors.add("Le mot de passe doit contenir au moins une lettre minuscule");
        }
        
        if (!password.matches(".*[0-9].*")) {
            errors.add("Le mot de passe doit contenir au moins un chiffre");
        }
        
        if (!password.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?].*")) {
            errors.add("Le mot de passe doit contenir au moins un caractère spécial");
        }
        
        return new PasswordValidation(errors.isEmpty(), errors);
    }
    
    /**
     * Vérifie si un mot de passe correspond au hash stocké.
     */
    public static boolean verifyPassword(String password, String salt, String storedHash) {
        String computedHash = hashPassword(password, salt);
        return computedHash.equals(storedHash);
    }
    
    /**
     * Résultat de la validation d'un mot de passe.
     */
    public record PasswordValidation(boolean isValid, List<String> errors) {
        public String getErrorsAsString() {
            return String.join(", ", errors);
        }
    }
}
