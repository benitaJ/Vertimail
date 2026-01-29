package com.vertimail.mailcore.service;

import com.vertimail.mailcore.model.Mail;
import com.vertimail.mailcore.model.MailFolder;

import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service métier : gestion des mails par fichiers JSON.
 *
 * Arborescence :
 * data/mailboxes/{username}/(inbox|outbox|draft|trash)/*.json
 */
public class MailService {

    private final Path mailboxesRoot;          // data/mailboxes
    private final FileStorageService storage;  // JSON read/write + list files


    public MailService(Path dataRoot, FileStorageService storage) throws Exception {
        this.mailboxesRoot = dataRoot.resolve("mailboxes");
        this.storage = storage;
        Files.createDirectories(this.mailboxesRoot);
    }
    private static void validateUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username is required");
        }
        // Autorise seulement : lettres, chiffres, point, underscore, tiret
        // Empêche '/', '\', '..', espaces, etc.
        if (!username.matches("[a-zA-Z0-9._-]{1,50}")) {
            throw new IllegalArgumentException("invalid username: " + username);
        }
    }

    // -------------------- Helpers chemins --------------------

    private Path userRoot(String username) {
        return mailboxesRoot.resolve(username);
    }

    private Path folderRoot(String username, MailFolder folder) {
        return userRoot(username).resolve(folder.dirName());
    }

    private Path mailFile(String username, MailFolder folder, String id) {
        return folderRoot(username, folder).resolve(id + ".json");
    }

    public boolean mailboxExists(String username) {
        validateUsername(username);
        return Files.exists(userRoot(username));
    }

    /**
     * Crée la mailbox et ses sous-dossiers si absent.
     */
    public void createMailboxIfMissing(String username) throws Exception {
        validateUsername(username);
        Files.createDirectories(folderRoot(username, MailFolder.INBOX));
        Files.createDirectories(folderRoot(username, MailFolder.OUTBOX));
        Files.createDirectories(folderRoot(username, MailFolder.DRAFT));
        Files.createDirectories(folderRoot(username, MailFolder.TRASH));
    }

    // -------------------- 1) sendMail --------------------

    /**
     * Envoie un mail :
     * - copie dans OUTBOX de l'expéditeur
     * - copie dans INBOX de chaque destinataire
     * - ajoute tag "unread" aux copies INBOX
     */
    public Mail sendMail(Mail mail) throws Exception {
        if (mail == null) throw new IllegalArgumentException("mail is null");
        if (mail.getFrom() == null || mail.getFrom().isBlank())
            throw new IllegalArgumentException("from is required");
        if (mail.getTo() == null || mail.getTo().isEmpty())
            throw new IllegalArgumentException("to is required");

        validateUsername(mail.getFrom());
        for (String rcpt : mail.getTo()) {
            validateUsername(rcpt);
        }

        if (!mailboxExists(mail.getFrom()))
            throw new IllegalArgumentException("Sender mailbox does not exist: " + mail.getFrom());

        for (String rcpt : mail.getTo()) {
            if (!mailboxExists(rcpt))
                throw new IllegalArgumentException("Recipient mailbox does not exist: " + rcpt);
        }

        if (mail.getId() == null || mail.getId().isBlank())
            mail.setId(UUID.randomUUID().toString());

        if (mail.getDate() == null)
            mail.setDate(LocalDateTime.now());

        mail.setDeletedAt(null);

        // OUTBOX expéditeur
        storage.writeMail(mailFile(mail.getFrom(), MailFolder.OUTBOX, mail.getId()), mail);

        // INBOX destinataires (copie + tag unread)
        for (String rcpt : mail.getTo()) {
            Mail copy = deepCopy(mail);
            copy.addTag("unread");
            storage.writeMail(mailFile(rcpt, MailFolder.INBOX, mail.getId()), copy);
        }

        return mail;
    }

    // -------------------- 2) saveDraft --------------------

    /**
     * Sauvegarde un brouillon dans DRAFT/{id}.json
     */
    public Mail saveDraft(String username, Mail draft) throws Exception {
        validateUsername(username);

        if (!mailboxExists(username))
            throw new IllegalArgumentException("Mailbox does not exist: " + username);

        if (draft == null) throw new IllegalArgumentException("draft is null");

        if (draft.getId() == null || draft.getId().isBlank())
            draft.setId(UUID.randomUUID().toString());

        if (draft.getDate() == null)
            draft.setDate(LocalDateTime.now());

        draft.setDeletedAt(null);

        storage.writeMail(mailFile(username, MailFolder.DRAFT, draft.getId()), draft);
        return draft;
    }

    // -------------------- 3) listMails(folder) --------------------

    /**
     * Liste les mails d'un dossier, triés par date décroissante.
     */
    public List<Mail> listMails(String username, MailFolder folder) throws Exception {
        validateUsername(username);
        Path dir = folderRoot(username, folder);
        List<Path> files = storage.listJsonFiles(dir);

        List<Mail> mails = new ArrayList<>();
        for (Path f : files) {
            try {
                mails.add(storage.readMail(f));
            } catch (Exception e) {
                // robuste : si un JSON est corrompu, on skip au lieu de casser toute la liste
            }
        }

        return mails.stream()
                .sorted(Comparator.comparing(Mail::getDate,
                        Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .collect(Collectors.toList());
    }

    // -------------------- 4) readMail(id) --------------------

    /**
     * Lit un mail. Si "unread" présent, on le retire et on réécrit le fichier.
     */
    public Mail readMail(String username, MailFolder folder, String id) throws Exception {
        validateUsername(username);
        Path file = mailFile(username, folder, id);
        if (!Files.exists(file)) throw new NoSuchFileException(file.toString());

        Mail mail = storage.readMail(file);
        if (mail.hasTag("unread")) {
            mail.removeTag("unread");
            storage.writeMail(file, mail);
        }
        return mail;
    }

    // -------------------- 5) moveToTrash(id) --------------------

    /**
     * Déplace un mail vers TRASH et ajoute deletedAt = now.
     */
    public void moveToTrash(String username, MailFolder fromFolder, String id) throws Exception {
        validateUsername(username);
        if (fromFolder == MailFolder.TRASH) return;

        Path from = mailFile(username, fromFolder, id);
        if (!Files.exists(from)) throw new NoSuchFileException(from.toString());

        Mail mail = storage.readMail(from);
        mail.setDeletedAt(LocalDateTime.now());

        Path to = mailFile(username, MailFolder.TRASH, id);
        storage.writeMail(to, mail);

        storage.deleteIfExists(from);
    }

    // -------------------- 6) purgeTrash() --------------------

    /**
     * Supprime les mails du TRASH dont deletedAt est plus vieux que 30 jours.
     * Retourne le nombre de mails supprimés.
     */
    public int purgeTrash(String username) throws Exception {
        validateUsername(username);
        return purgeTrash(username, 30);
    }

    public int purgeTrash(String username, int days) throws Exception {
        validateUsername(username);
        Path trashDir = folderRoot(username, MailFolder.TRASH);
        List<Path> files = storage.listJsonFiles(trashDir);

        LocalDateTime now = LocalDateTime.now();
        int deleted = 0;

        for (Path f : files) {
            Mail mail;
            try {
                mail = storage.readMail(f);
            } catch (Exception e) {
                // JSON illisible : option “nettoyage”
                storage.deleteIfExists(f);
                deleted++;
                continue;
            }

            LocalDateTime deletedAt = mail.getDeletedAt();
            if (deletedAt != null) {
                if (deletedAt.plusDays(days).isBefore(now)) {
                    storage.deleteIfExists(f);
                    deleted++;
                }
            }
        }
        return deleted;
    }

    // -------------------- Deep copy (simple) --------------------

    /**
     * Copie profonde minimale (pour éviter de partager tags/attachments entre copies).
     */
    private Mail deepCopy(Mail m) {
        Mail c = new Mail();
        c.setId(m.getId());
        c.setFrom(m.getFrom());
        c.setTo(new ArrayList<>(m.getTo() == null ? List.of() : m.getTo()));
        c.setDate(m.getDate());
        c.setSubject(m.getSubject());
        c.setContent(m.getContent());
        c.setAttachments(m.getAttachments() == null ? new ArrayList<>() : new ArrayList<>(m.getAttachments()));
        c.setTags(m.getTags() == null ? new HashSet<>() : new HashSet<>(m.getTags()));
        c.setDeletedAt(m.getDeletedAt());
        return c;
    }
}
