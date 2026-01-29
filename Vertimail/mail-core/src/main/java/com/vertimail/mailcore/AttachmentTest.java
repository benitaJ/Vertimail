package com.vertimail.mailcore;

import com.vertimail.mailcore.model.AttachmentRef;
import com.vertimail.mailcore.service.AttachmentService;

import java.nio.file.Files;
import java.nio.file.Path;

public class AttachmentTest {

    public static void main(String[] args) throws Exception {
        // Racine data du module (tu as créé mail-core/data)
        Path dataRoot = Path.of("data");

        AttachmentService service = new AttachmentService(dataRoot);

        // Crée un fichier temporaire à attacher
        Path tmp = Files.createTempFile("vertimail-attach-", ".txt");
        Files.writeString(tmp, "hello attachment");

        AttachmentRef ref1 = service.store(tmp, "hello.txt");
        AttachmentRef ref2 = service.store(tmp, "hello.txt");

        System.out.println("SHA1 = " + ref1.getSha256());
        System.out.println("SHA2 = " + ref2.getSha256());
        System.out.println("Same? " + ref1.getSha256().equals(ref2.getSha256()));
        System.out.println("Stored path = " + service.resolve(ref1.getSha256()).toAbsolutePath());
        System.out.println("Exists? " + Files.exists(service.resolve(ref1.getSha256())));
    }
}

