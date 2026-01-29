package com.vertimail.mailcore;

import com.vertimail.mailcore.model.AttachmentRef;
import com.vertimail.mailcore.model.Mail;
import com.vertimail.mailcore.service.*;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class MailboxStatsServiceTest {

    @Test
    void computeSize_shouldCountJsonAndDeduplicatedAttachments() throws Exception {
        Path root = Files.createTempDirectory("vertimail-size");
        FileStorageService storage = new FileStorageService();
        MailService mailService = new MailService(root, storage);
        AttachmentService attachmentService = new AttachmentService(root);
        MailboxStatsService stats = new MailboxStatsService(root, storage);

        mailService.createMailboxIfMissing("alice");
        mailService.createMailboxIfMissing("bob");

        Path file = Files.createTempFile("attach", ".txt");
        Files.writeString(file, "same content");

        AttachmentRef ref = attachmentService.store(file, "doc.txt");

        Mail m1 = new Mail()
                .setFrom("alice")
                .setTo(List.of("bob"))
                .setDate(LocalDateTime.now())
                .setSubject("A")
                .setContent("A")
                .setAttachments(List.of(ref));

        Mail m2 = new Mail()
                .setFrom("alice")
                .setTo(List.of("bob"))
                .setDate(LocalDateTime.now())
                .setSubject("B")
                .setContent("B")
                .setAttachments(List.of(ref));

        mailService.sendMail(m1);
        mailService.sendMail(m2);

        long jsonSize = stats.computeJsonSizeBytes("bob");
        long attSize = stats.computeAttachmentsSizeBytes("bob");
        long total = stats.computeTotalMailboxSizeBytes("bob");

        assertTrue(jsonSize > 0);
        assertTrue(attSize > 0);
        assertEquals(jsonSize + attSize, total);
    }
}

