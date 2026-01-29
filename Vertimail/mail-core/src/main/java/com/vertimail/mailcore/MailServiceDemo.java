package com.vertimail.mailcore;

import com.vertimail.mailcore.config.MailCoreConfig;
import com.vertimail.mailcore.model.Mail;
import com.vertimail.mailcore.model.MailFolder;
import com.vertimail.mailcore.service.FileStorageService;
import com.vertimail.mailcore.service.MailService;
import com.vertimail.mailcore.service.AttachmentService;
import com.vertimail.mailcore.model.AttachmentRef;
import java.nio.file.Files;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

public class MailServiceDemo {

    public static void main(String[] args) throws Exception {


        //  Résolution du répertoire de données
        Path dataRoot = MailCoreConfig.resolveDataRoot();
        System.out.println("RÉPERTOIRE DE DONNÉES = " + dataRoot.toAbsolutePath());

        FileStorageService storage = new FileStorageService();
        MailService mailService = new MailService(dataRoot, storage);

        //  Création des boîtes aux lettres
        mailService.createMailboxIfMissing("alice");
        mailService.createMailboxIfMissing("bob");
        System.out.println("Boîtes aux lettres créées pour Alice et Bob");

        //  Envoi d’un mail
        Mail mail = new Mail()
                .setFrom("alice")
                .setTo(List.of("bob"))
                .setDate(LocalDateTime.now())
                .setSubject("Démo Vertimail")
                .setContent("Bonjour Bob, ceci est un mail de démonstration.");

        mailService.sendMail(mail);
        System.out.println("Mail envoyé de Alice vers Bob (ID = " + mail.getId() + ")");

        //  État après l’envoi
        System.out.println("Nombre de mails dans la BOÎTE D’ENVOI d’Alice = "
                + mailService.listMails("alice", MailFolder.OUTBOX).size());
        System.out.println("Nombre de mails dans la BOÎTE DE RÉCEPTION de Bob = "
                + mailService.listMails("bob", MailFolder.INBOX).size());

        //  Lecture du mail (suppression du tag unread)
        Mail lu = mailService.readMail("bob", MailFolder.INBOX, mail.getId());
        System.out.println("Bob lit le mail → tag \"unread\" présent ? "
                + lu.hasTag("unread"));

        //  Suppression du mail (déplacement dans la corbeille)
        mailService.moveToTrash("bob", MailFolder.INBOX, mail.getId());
        System.out.println("Mail déplacé dans la CORBEILLE");

        //  État final
        System.out.println("Nombre de mails dans la BOÎTE DE RÉCEPTION de Bob après suppression = "
                + mailService.listMails("bob", MailFolder.INBOX).size());
        System.out.println("Nombre de mails dans la CORBEILLE de Bob = "
                + mailService.listMails("bob", MailFolder.TRASH).size());

        System.out.println("=== DÉMONSTRATION TERMINÉE ===");
    }
}