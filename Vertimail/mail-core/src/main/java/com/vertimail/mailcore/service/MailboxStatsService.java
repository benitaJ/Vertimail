package com.vertimail.mailcore.service;

import com.vertimail.mailcore.model.Mail;
import com.vertimail.mailcore.model.MailFolder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Calcul de l'espace disque utilisé par une mailbox utilisateur.
 */
public class MailboxStatsService {

    private final Path dataRoot;
    private final FileStorageService storage;

    public MailboxStatsService(Path dataRoot, FileStorageService storage) {
        this.dataRoot = dataRoot;
        this.storage = storage;
    }

    private Path userRoot(String username) {
        return dataRoot.resolve("mailboxes").resolve(username);
    }

    private Path folderRoot(String username, MailFolder folder) {
        return userRoot(username).resolve(folder.dirName());
    }

    /**
     * Taille des fichiers JSON (tous dossiers confondus).
     */
    public long computeJsonSizeBytes(String username) throws Exception {
        long total = 0L;

        for (MailFolder folder : MailFolder.values()) {
            Path dir = folderRoot(username, folder);
            for (Path p : storage.listJsonFiles(dir)) {
                if (Files.exists(p)) {
                    total += Files.size(p);
                }
            }
        }
        return total;
    }

    /**
     * Taille des pièces jointes référencées (sans double-compte).
     */
    public long computeAttachmentsSizeBytes(String username) throws Exception {
        Set<String> seenHashes = new HashSet<>();
        long total = 0L;

        for (MailFolder folder : MailFolder.values()) {
            Path dir = folderRoot(username, folder);
            for (Path p : storage.listJsonFiles(dir)) {
                try {
                    Mail mail = storage.readMail(p);
                    if (mail.getAttachments() == null) continue;

                    mail.getAttachments().forEach(ref -> {
                        if (ref != null && ref.getSha256() != null) {
                            seenHashes.add(ref.getSha256());
                        }
                    });
                } catch (Exception ignored) {}
            }
        }

        Path attachmentsDir = dataRoot.resolve("attachments");
        for (String sha : seenHashes) {
            Path f = attachmentsDir.resolve(sha);
            if (Files.exists(f)) {
                total += Files.size(f);
            }
        }
        return total;
    }

    /**
     * Taille totale JSON + pièces jointes.
     */
    public long computeTotalMailboxSizeBytes(String username) throws Exception {
        return computeJsonSizeBytes(username) + computeAttachmentsSizeBytes(username);
    }
}
