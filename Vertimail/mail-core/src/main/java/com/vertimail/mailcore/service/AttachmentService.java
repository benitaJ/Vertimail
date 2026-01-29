package com.vertimail.mailcore.service;

import com.vertimail.mailcore.model.AttachmentRef;
import com.vertimail.mailcore.util.Hashing;

import java.io.IOException;
import java.nio.file.*;

/**
 * Service de gestion des pièces jointes.
 * Stockage centralisé + déduplication via SHA-256.
 *
 * Stockage : data/attachments/{sha256}
 */
public class AttachmentService {

    private final Path attachmentsDir;

    public AttachmentService(Path dataRoot) throws IOException {
        this.attachmentsDir = dataRoot.resolve("attachments");
        Files.createDirectories(this.attachmentsDir);
    }

    public Path getAttachmentsDir() {
        return attachmentsDir;
    }

    /**
     * Stocke un fichier en pièce jointe (déduplication).
     * @param uploadedFile chemin du fichier à stocker (source)
     * @param originalFilename nom original (pour afficher côté mail)
     * @return référence (filename + sha256)
     */
    public AttachmentRef store(Path uploadedFile, String originalFilename) throws Exception {
        String sha256 = Hashing.sha256Hex(uploadedFile);
        Path target = attachmentsDir.resolve(sha256);

        // Déduplication : si déjà présent, ne pas recopier
        if (!Files.exists(target)) {
            Files.copy(uploadedFile, target, StandardCopyOption.REPLACE_EXISTING);
        }

        return new AttachmentRef(originalFilename, sha256);
    }

    /**
     * Retourne le chemin du fichier stocké correspondant au hash.
     */
    public Path resolve(String sha256) {
        return attachmentsDir.resolve(sha256);
    }
}

