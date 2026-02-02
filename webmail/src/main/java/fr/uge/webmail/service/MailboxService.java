package fr.uge.webmail.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import fr.uge.webmail.model.Attachment;
import fr.uge.webmail.model.Email;
import fr.uge.webmail.util.FileUtils;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service pour gérer les boîtes mail et les emails.
 */
public class MailboxService {
    
    private final Path dataDirectory;
    private final Path attachmentsDirectory;
    private final ObjectMapper objectMapper;
    
    // Dossiers standards d'une boîte mail
    public static final String INBOX = "inbox";
    public static final String OUTBOX = "outbox";
    public static final String DRAFT = "draft";
    public static final String TRASH = "trash";
    
    private static final int TRASH_RETENTION_DAYS = 30;
    
    public MailboxService(Path dataDirectory) {
        this.dataDirectory = dataDirectory.resolve("mailboxes");
        this.attachmentsDirectory = dataDirectory.resolve("attachments");
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        
        try {
            FileUtils.ensureDirectoryExists(this.dataDirectory);
            FileUtils.ensureDirectoryExists(this.attachmentsDirectory);
        } catch (IOException e) {
            throw new RuntimeException("Impossible de créer les répertoires de données", e);
        }
    }
    
    /**
     * Crée une nouvelle boîte mail pour un utilisateur.
     */
    public void createMailbox(String username) throws IOException {
        Path userDir = getUserDirectory(username);
        FileUtils.ensureDirectoryExists(userDir);
        FileUtils.ensureDirectoryExists(userDir.resolve(INBOX));
        FileUtils.ensureDirectoryExists(userDir.resolve(OUTBOX));
        FileUtils.ensureDirectoryExists(userDir.resolve(DRAFT));
        FileUtils.ensureDirectoryExists(userDir.resolve(TRASH));
    }
    
    /**
     * Vérifie si une boîte mail existe.
     */
    public boolean mailboxExists(String username) {
        return Files.exists(getUserDirectory(username));
    }
    
    /**
     * Retourne le chemin du répertoire d'un utilisateur.
     */
    public Path getUserDirectory(String username) {
        return dataDirectory.resolve(username);
    }
    
    /**
     * Sauvegarde un email dans un dossier.
     */
    public void saveEmail(String username, String folder, Email email) throws IOException {
        Path folderPath = getUserDirectory(username).resolve(folder);
        FileUtils.ensureDirectoryExists(folderPath);
        
        email.setFolder(folder);
        Path emailFile = folderPath.resolve(email.getId() + ".json");
        objectMapper.writeValue(emailFile.toFile(), email);
    }
    
    /**
     * Charge un email depuis un fichier.
     */
    public Optional<Email> loadEmail(String username, String folder, String emailId) throws IOException {
        Path emailFile = getUserDirectory(username).resolve(folder).resolve(emailId + ".json");
        if (!Files.exists(emailFile)) {
            return Optional.empty();
        }
        Email email = objectMapper.readValue(emailFile.toFile(), Email.class);
        return Optional.of(email);
    }
    
    /**
     * Liste tous les emails d'un dossier.
     */
    public List<Email> listEmails(String username, String folder) throws IOException {
        Path folderPath = getUserDirectory(username).resolve(folder);
        if (!Files.exists(folderPath)) {
            return new ArrayList<>();
        }
        
        List<Email> emails = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(folderPath, "*.json")) {
            for (Path file : stream) {
                try {
                    Email email = objectMapper.readValue(file.toFile(), Email.class);
                    emails.add(email);
                } catch (IOException e) {
                    System.err.println("Erreur lors de la lecture de " + file + ": " + e.getMessage());
                }
            }
        }
        
        // Trier par date décroissante
        emails.sort(Comparator.comparing(Email::getDate).reversed());
        return emails;
    }
    
    /**
     * Filtre les emails selon un critère.
     */
    public List<Email> filterEmails(String username, String folder, String filter) throws IOException {
        List<Email> emails = listEmails(username, folder);
        
        if (filter == null || filter.isEmpty()) {
            return emails;
        }
        
        String filterLower = filter.toLowerCase();
        return emails.stream()
                .filter(email -> 
                    (email.getFrom() != null && email.getFrom().toLowerCase().contains(filterLower)) ||
                    (email.getSubject() != null && email.getSubject().toLowerCase().contains(filterLower)) ||
                    email.getTo().stream().anyMatch(to -> to.toLowerCase().contains(filterLower))
                )
                .collect(Collectors.toList());
    }
    
    /**
     * Déplace un email vers un autre dossier.
     */
    public void moveEmail(String username, String fromFolder, String toFolder, String emailId) throws IOException {
        Optional<Email> emailOpt = loadEmail(username, fromFolder, emailId);
        if (emailOpt.isPresent()) {
            Email email = emailOpt.get();
            
            // Supprimer de l'ancien dossier
            deleteEmailFile(username, fromFolder, emailId);
            
            // Sauvegarder dans le nouveau dossier
            saveEmail(username, toFolder, email);
        }
    }
    
    /**
     * Supprime un email (déplace vers la corbeille ou supprime définitivement).
     */
    public void deleteEmail(String username, String folder, String emailId) throws IOException {
        if (TRASH.equals(folder)) {
            // Suppression définitive
            deleteEmailFile(username, folder, emailId);
        } else {
            // Déplacer vers la corbeille
            moveEmail(username, folder, TRASH, emailId);
        }
    }
    
    /**
     * Supprime physiquement un fichier email.
     */
    private void deleteEmailFile(String username, String folder, String emailId) throws IOException {
        Path emailFile = getUserDirectory(username).resolve(folder).resolve(emailId + ".json");
        Files.deleteIfExists(emailFile);
    }
    
    /**
     * Envoie un email (copie dans outbox de l'expéditeur et inbox des destinataires).
     */
    public void sendEmail(Email email) throws IOException {
        // Sauvegarder dans l'outbox de l'expéditeur
        saveEmail(email.getFrom(), OUTBOX, email);
        
        // Copier dans l'inbox de chaque destinataire
        for (String recipient : email.getTo()) {
            if (mailboxExists(recipient)) {
                // Créer une copie de l'email pour le destinataire
                Email recipientCopy = copyEmail(email);
                saveEmail(recipient, INBOX, recipientCopy);
            }
        }
    }
    
    /**
     * Sauvegarde un brouillon.
     */
    public void saveDraft(String username, Email email) throws IOException {
        saveEmail(username, DRAFT, email);
    }
    
    /**
     * Crée une copie d'un email avec un nouvel ID.
     */
    private Email copyEmail(Email original) {
        Email copy = new Email();
        copy.setFrom(original.getFrom());
        copy.setTo(new ArrayList<>(original.getTo()));
        copy.setSubject(original.getSubject());
        copy.setContent(original.getContent());
        copy.setDate(original.getDate());
        copy.setAttachments(new ArrayList<>(original.getAttachments()));
        copy.getTags().clear();
        copy.getTags().add("unread");
        return copy;
    }
    
    /**
     * Sauvegarde une pièce jointe et retourne son hash SHA-256.
     */
    public Attachment saveAttachment(String filename, String contentType, byte[] data) throws IOException {
        String sha256 = FileUtils.sha256(data);
        Path attachmentFile = attachmentsDirectory.resolve(sha256);
        
        // Sauvegarder seulement si le fichier n'existe pas déjà (déduplication)
        if (!Files.exists(attachmentFile)) {
            Files.write(attachmentFile, data);
        }
        
        return new Attachment(filename, sha256, contentType, data.length);
    }
    
    /**
     * Récupère une pièce jointe par son hash.
     */
    public Optional<byte[]> getAttachment(String sha256) throws IOException {
        Path attachmentFile = attachmentsDirectory.resolve(sha256);
        if (!Files.exists(attachmentFile)) {
            return Optional.empty();
        }
        return Optional.of(Files.readAllBytes(attachmentFile));
    }
    
    /**
     * Purge automatiquement les emails de la corbeille datant de plus de 30 jours.
     */
    public void purgeOldTrashEmails(String username) throws IOException {
        Path trashPath = getUserDirectory(username).resolve(TRASH);
        if (!Files.exists(trashPath)) {
            return;
        }
        
        LocalDateTime threshold = LocalDateTime.now().minusDays(TRASH_RETENTION_DAYS);
        
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(trashPath, "*.json")) {
            for (Path file : stream) {
                try {
                    Email email = objectMapper.readValue(file.toFile(), Email.class);
                    if (email.getDate().isBefore(threshold)) {
                        Files.delete(file);
                        System.out.println("Supprimé de la corbeille : " + email.getSubject());
                    }
                } catch (IOException e) {
                    System.err.println("Erreur lors de la purge de " + file + ": " + e.getMessage());
                }
            }
        }
    }
    
    /**
     * Calcule l'espace disque utilisé par une boîte mail.
     */
    public long calculateMailboxSize(String username) throws IOException {
        Path userDir = getUserDirectory(username);
        return FileUtils.calculateDirectorySize(userDir);
    }
    
    /**
     * Calcule l'espace disque total incluant les pièces jointes liées.
     */
    public long calculateTotalUserStorage(String username) throws IOException {
        long mailboxSize = calculateMailboxSize(username);
        
        // Calculer la taille des pièces jointes uniques
        long attachmentsSize = 0;
        List<String> seenHashes = new ArrayList<>();
        
        for (String folder : List.of(INBOX, OUTBOX, DRAFT, TRASH)) {
            for (Email email : listEmails(username, folder)) {
                for (Attachment attachment : email.getAttachments()) {
                    if (!seenHashes.contains(attachment.getSha256())) {
                        seenHashes.add(attachment.getSha256());
                        attachmentsSize += attachment.getSize();
                    }
                }
            }
        }
        
        return mailboxSize + attachmentsSize;
    }
    
    /**
     * Compte le nombre d'emails non lus dans un dossier.
     */
    public int countUnreadEmails(String username, String folder) throws IOException {
        return (int) listEmails(username, folder).stream()
                .filter(Email::isUnread)
                .count();
    }
    
    /**
     * Marque un email comme lu.
     */
    public void markAsRead(String username, String folder, String emailId) throws IOException {
        Optional<Email> emailOpt = loadEmail(username, folder, emailId);
        if (emailOpt.isPresent()) {
            Email email = emailOpt.get();
            email.markAsRead();
            saveEmail(username, folder, email);
        }
    }
    
    /**
     * Ajoute ou retire un tag sur un email.
     */
    public void toggleTag(String username, String folder, String emailId, String tag) throws IOException {
        Optional<Email> emailOpt = loadEmail(username, folder, emailId);
        if (emailOpt.isPresent()) {
            Email email = emailOpt.get();
            if (email.hasTag(tag)) {
                email.removeTag(tag);
            } else {
                email.addTag(tag);
            }
            saveEmail(username, folder, email);
        }
    }
}
