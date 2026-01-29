package com.vertimail.mailcore;

import com.vertimail.mailcore.model.Mail;
import com.vertimail.mailcore.service.FileStorageService;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class FileStorageServiceTest {

    @Test
    void writeThenReadMail_shouldPreserveFields() throws Exception {
        FileStorageService storage = new FileStorageService();

        Path tempDir = Files.createTempDirectory("vertimail-test-");
        Path file = tempDir.resolve("test.json");

        Mail mail = new Mail()
                .setId("123")
                .setFrom("alice")
                .setTo(List.of("bob"))
                .setDate(LocalDateTime.of(2026, 1, 21, 10, 0))
                .setSubject("Hello")
                .setContent("World");

        storage.writeMail(file, mail);

        Mail read = storage.readMail(file);

        assertEquals("123", read.getId());
        assertEquals("alice", read.getFrom());
        assertEquals(List.of("bob"), read.getTo());
        assertEquals(LocalDateTime.of(2026, 1, 21, 10, 0), read.getDate());
        assertEquals("Hello", read.getSubject());
        assertEquals("World", read.getContent());
    }
}

