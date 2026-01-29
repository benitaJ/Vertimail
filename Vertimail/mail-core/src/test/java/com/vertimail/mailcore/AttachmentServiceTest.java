package com.vertimail.mailcore;

import com.vertimail.mailcore.model.AttachmentRef;
import com.vertimail.mailcore.service.AttachmentService;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class AttachmentServiceTest {

    @Test
    void store_shouldDeduplicateBySha256() throws Exception {
        Path dataRoot = Files.createTempDirectory("vertimail-test-data-");
        AttachmentService svc = new AttachmentService(dataRoot);

        Path file = Files.createTempFile("attach-", ".txt");
        Files.writeString(file, "same content");

        AttachmentRef r1 = svc.store(file, "a.txt");
        AttachmentRef r2 = svc.store(file, "a.txt");

        assertEquals(r1.getSha256(), r2.getSha256());
        assertTrue(Files.exists(svc.resolve(r1.getSha256())));
    }
}

