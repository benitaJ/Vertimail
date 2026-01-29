package com.vertimail.mailcore;

import com.vertimail.mailcore.model.Mail;
import com.vertimail.mailcore.model.MailFolder;
import com.vertimail.mailcore.service.FileStorageService;
import com.vertimail.mailcore.service.MailService;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class MailServiceTest {

    @Test
    void sendMail_createsOutboxAndInbox_andInboxHasUnread() throws Exception {
        Path dataRoot = Files.createTempDirectory("vertimail-test-data-");
        FileStorageService storage = new FileStorageService();
        MailService svc = new MailService(dataRoot, storage);

        svc.createMailboxIfMissing("alice");
        svc.createMailboxIfMissing("bob");

        Mail mail = new Mail()
                .setFrom("alice")
                .setTo(List.of("bob"))
                .setDate(LocalDateTime.now())
                .setSubject("Hello")
                .setContent("World");

        svc.sendMail(mail);

        assertEquals(1, svc.listMails("alice", MailFolder.OUTBOX).size());
        assertEquals(1, svc.listMails("bob", MailFolder.INBOX).size());

        Mail inboxCopy = svc.listMails("bob", MailFolder.INBOX).get(0);
        assertTrue(inboxCopy.hasTag("unread"));
    }

    @Test
    void readMail_removesUnread_andPersists() throws Exception {
        Path dataRoot = Files.createTempDirectory("vertimail-test-data-");
        FileStorageService storage = new FileStorageService();
        MailService svc = new MailService(dataRoot, storage);

        svc.createMailboxIfMissing("alice");
        svc.createMailboxIfMissing("bob");

        Mail mail = new Mail()
                .setFrom("alice")
                .setTo(List.of("bob"))
                .setDate(LocalDateTime.now())
                .setSubject("Hello")
                .setContent("World");

        svc.sendMail(mail);

        Mail read1 = svc.readMail("bob", MailFolder.INBOX, mail.getId());
        assertFalse(read1.hasTag("unread"));

        Mail read2 = svc.readMail("bob", MailFolder.INBOX, mail.getId());
        assertFalse(read2.hasTag("unread"));
    }

    @Test
    void saveDraft_writesToDraftFolder() throws Exception {
        Path dataRoot = Files.createTempDirectory("vertimail-test-data-");
        FileStorageService storage = new FileStorageService();
        MailService svc = new MailService(dataRoot, storage);

        svc.createMailboxIfMissing("alice");

        Mail draft = new Mail()
                .setFrom("alice")
                .setTo(List.of("bob"))
                .setDate(LocalDateTime.now())
                .setSubject("Draft")
                .setContent("Draft content");

        svc.saveDraft("alice", draft);

        assertEquals(1, svc.listMails("alice", MailFolder.DRAFT).size());
    }

    @Test
    void moveToTrash_movesMail_setsDeletedAt_removesFromSource() throws Exception {
        Path dataRoot = Files.createTempDirectory("vertimail-test-data-");
        FileStorageService storage = new FileStorageService();
        MailService svc = new MailService(dataRoot, storage);

        svc.createMailboxIfMissing("alice");
        svc.createMailboxIfMissing("bob");

        Mail mail = new Mail()
                .setFrom("alice")
                .setTo(List.of("bob"))
                .setDate(LocalDateTime.now())
                .setSubject("Hello")
                .setContent("World");

        svc.sendMail(mail);

        assertEquals(1, svc.listMails("bob", MailFolder.INBOX).size());

        svc.moveToTrash("bob", MailFolder.INBOX, mail.getId());

        assertEquals(0, svc.listMails("bob", MailFolder.INBOX).size());
        assertEquals(1, svc.listMails("bob", MailFolder.TRASH).size());

        Mail trashed = svc.listMails("bob", MailFolder.TRASH).get(0);
        assertNotNull(trashed.getDeletedAt());
    }

    @Test
    void purgeTrash_deletesOldMails() throws Exception {
        Path dataRoot = Files.createTempDirectory("vertimail-test-data-");
        FileStorageService storage = new FileStorageService();
        MailService svc = new MailService(dataRoot, storage);

        svc.createMailboxIfMissing("bob");

        // écrire un mail déjà ancien dans trash
        Mail old = new Mail()
                .setId("old-1")
                .setFrom("alice")
                .setTo(List.of("bob"))
                .setDate(LocalDateTime.now().minusDays(40))
                .setSubject("Old")
                .setContent("Old content")
                .setDeletedAt(LocalDateTime.now().minusDays(40));

        Path trashFile = dataRoot
                .resolve("mailboxes").resolve("bob")
                .resolve("trash").resolve(old.getId() + ".json");

        storage.writeMail(trashFile, old);

        assertEquals(1, svc.listMails("bob", MailFolder.TRASH).size());

        int deleted = svc.purgeTrash("bob");
        assertEquals(1, deleted);
        assertEquals(0, svc.listMails("bob", MailFolder.TRASH).size());
    }
}

