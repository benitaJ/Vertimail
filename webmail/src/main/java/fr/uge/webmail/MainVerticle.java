package fr.uge.webmail;

import fr.uge.webmail.model.Attachment;
import fr.uge.webmail.model.Email;
import fr.uge.webmail.service.MailboxService;
import fr.uge.webmail.service.UdpService;
import fr.uge.webmail.service.UserService;
import fr.uge.webmail.util.FileUtils;
import io.vertx.core.Future;
import io.vertx.core.VerticleBase;
import io.vertx.core.http.Cookie;
import io.vertx.ext.web.FileUpload;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.SessionHandler;
import io.vertx.ext.web.handler.StaticHandler;
import io.vertx.ext.web.sstore.LocalSessionStore;
import io.vertx.ext.web.templ.pebble.PebbleTemplateEngine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Verticle principal de l'application Webmail.
 */
public class MainVerticle extends VerticleBase {

    private static final int HTTP_PORT = 8080;
    private static final int UDP_PORT = 9999;
    private static final String DATA_DIR = "data";
    private static final String SESSION_COOKIE = "webmail_session";

    private MailboxService mailboxService;
    private UserService userService;
    private UdpService udpService;
    private PebbleTemplateEngine templateEngine;

    @Override
    public Future<?> start() {
        // Initialiser les services
        Path dataPath = Path.of(DATA_DIR);
        mailboxService = new MailboxService(dataPath);
        userService = new UserService(dataPath, mailboxService);

        // Démarrer le service UDP
        udpService = new UdpService(vertx, mailboxService, UDP_PORT);
        udpService.start();

        // Créer le moteur de templates Pebble
        templateEngine = PebbleTemplateEngine.create(vertx);

        // Créer le routeur
        Router router = Router.router(vertx);

        // Middleware pour le body et les fichiers uploadés
        router.route().handler(BodyHandler.create()
            .setUploadsDirectory("uploads")
            .setBodyLimit(50 * 1024 * 1024)); // 50 MB max

        // Session handler
        router.route().handler(SessionHandler.create(LocalSessionStore.create(vertx)));

        // Routes publiques (sans authentification)
        router.get("/").handler(this::handleIndex);
        router.get("/login").handler(this::handleLoginPage);
        router.post("/login").handler(this::handleLogin);
        router.get("/register").handler(this::handleRegisterPage);
        router.post("/register").handler(this::handleRegister);
        router.get("/recovery").handler(this::handleRecoveryPage);
        router.post("/recovery").handler(this::handleRecovery);
        router.get("/logout").handler(this::handleLogout);

        // Routes protégées (avec authentification)
        router.route("/mail/*").handler(this::authMiddleware);
        router.get("/mail/inbox").handler(ctx -> handleFolder(ctx, MailboxService.INBOX));
        router.get("/mail/outbox").handler(ctx -> handleFolder(ctx, MailboxService.OUTBOX));
        router.get("/mail/drafts").handler(ctx -> handleFolder(ctx, MailboxService.DRAFT));
        router.get("/mail/trash").handler(ctx -> handleFolder(ctx, MailboxService.TRASH));
        router.get("/mail/compose").handler(this::handleComposePage);
        router.post("/mail/compose").handler(this::handleCompose);
        router.get("/mail/view/:folder/:id").handler(this::handleViewEmail);
        router.post("/mail/delete/:folder/:id").handler(this::handleDeleteEmail);
        router.post("/mail/tag/:folder/:id/:tag").handler(this::handleToggleTag);
        router.get("/mail/edit/:id").handler(this::handleEditDraft);
        router.get("/mail/attachment/:sha256/:filename").handler(this::handleDownloadAttachment);
        router.get("/mail/settings").handler(this::handleSettingsPage);
        router.post("/mail/settings/password").handler(this::handleChangePassword);

        // Fichiers statiques
        router.route("/static/*").handler(StaticHandler.create("webroot"));

        // Planifier le nettoyage périodique
        vertx.setPeriodic(60000, id -> userService.cleanupExpiredSessions());

        // Démarrer le serveur HTTP
        return vertx.createHttpServer()
            .requestHandler(router)
            .listen(HTTP_PORT)
            .onSuccess(server -> {
                System.out.println("🌐 Serveur HTTP démarré sur http://localhost:" + HTTP_PORT);
            });
    }

    @Override
    public Future<?> stop() {
        udpService.stop();
        return Future.succeededFuture();
    }

    // ==================== Middleware ====================

    private void authMiddleware(RoutingContext ctx) {
        Cookie sessionCookie = ctx.request().getCookie(SESSION_COOKIE);
        if (sessionCookie == null) {
            ctx.redirect("/login");
            return;
        }

        Optional<String> userOpt = userService.validateSession(sessionCookie.getValue());
        if (userOpt.isEmpty()) {
            ctx.response().removeCookie(SESSION_COOKIE);
            ctx.redirect("/login");
            return;
        }

        // Stocker l'utilisateur dans le contexte
        ctx.put("username", userOpt.get());

        // Purger automatiquement la corbeille
        try {
            mailboxService.purgeOldTrashEmails(userOpt.get());
        } catch (IOException e) {
            System.err.println("Erreur lors de la purge de la corbeille : " + e.getMessage());
        }

        ctx.next();
    }

    // ==================== Pages publiques ====================

    private void handleIndex(RoutingContext ctx) {
        Cookie sessionCookie = ctx.request().getCookie(SESSION_COOKIE);
        if (sessionCookie != null && userService.validateSession(sessionCookie.getValue()).isPresent()) {
            ctx.redirect("/mail/inbox");
        } else {
            ctx.redirect("/login");
        }
    }

    private void handleLoginPage(RoutingContext ctx) {
        render(ctx, "login.peb", Map.of());
    }

    private void handleLogin(RoutingContext ctx) {
        String username = ctx.request().getFormAttribute("username");
        String password = ctx.request().getFormAttribute("password");
        String rememberMe = ctx.request().getFormAttribute("remember");

        int sessionDuration = "on".equals(rememberMe)
            ? UserService.EXTENDED_SESSION_DURATION_MINUTES
            : UserService.DEFAULT_SESSION_DURATION_MINUTES;

        Optional<String> tokenOpt = userService.authenticate(username, password, sessionDuration);

        if (tokenOpt.isPresent()) {
            ctx.response().addCookie(Cookie.cookie(SESSION_COOKIE, tokenOpt.get())
                .setPath("/")
                .setMaxAge(sessionDuration * 60));
            ctx.redirect("/mail/inbox");
        } else {
            render(ctx, "login.peb", Map.of("error", "Nom d'utilisateur ou mot de passe incorrect"));
        }
    }

    private void handleRegisterPage(RoutingContext ctx) {
        render(ctx, "register.peb", Map.of());
    }

    private void handleRegister(RoutingContext ctx) {
        String username = ctx.request().getFormAttribute("username");
        String password = ctx.request().getFormAttribute("password");
        String confirmPassword = ctx.request().getFormAttribute("confirmPassword");

        if (!password.equals(confirmPassword)) {
            render(ctx, "register.peb", Map.of("error", "Les mots de passe ne correspondent pas"));
            return;
        }

        try {
            String recoveryCode = userService.createUser(username, password);
            render(ctx, "register_success.peb", Map.of(
                "username", username,
                "recoveryCode", recoveryCode
            ));
        } catch (IllegalArgumentException e) {
            render(ctx, "register.peb", Map.of("error", e.getMessage()));
        } catch (IOException e) {
            render(ctx, "register.peb", Map.of("error", "Erreur lors de la création du compte"));
        }
    }

    private void handleRecoveryPage(RoutingContext ctx) {
        render(ctx, "recovery.peb", Map.of());
    }

    private void handleRecovery(RoutingContext ctx) {
        String username = ctx.request().getFormAttribute("username");
        String recoveryCode = ctx.request().getFormAttribute("recoveryCode");
        String newPassword = ctx.request().getFormAttribute("newPassword");
        String confirmPassword = ctx.request().getFormAttribute("confirmPassword");

        if (!newPassword.equals(confirmPassword)) {
            render(ctx, "recovery.peb", Map.of("error", "Les mots de passe ne correspondent pas"));
            return;
        }

        try {
            userService.resetPasswordWithRecoveryCode(username, recoveryCode, newPassword);
            render(ctx, "login.peb", Map.of("success", "Mot de passe réinitialisé avec succès. Connectez-vous avec votre nouveau mot de passe."));
        } catch (IllegalArgumentException e) {
            render(ctx, "recovery.peb", Map.of("error", e.getMessage()));
        } catch (IOException e) {
            render(ctx, "recovery.peb", Map.of("error", "Erreur lors de la réinitialisation"));
        }
    }

    private void handleLogout(RoutingContext ctx) {
        Cookie sessionCookie = ctx.request().getCookie(SESSION_COOKIE);
        if (sessionCookie != null) {
            userService.logout(sessionCookie.getValue());
            ctx.response().removeCookie(SESSION_COOKIE);
        }
        ctx.redirect("/login");
    }

    // ==================== Pages protégées ====================

    private void handleFolder(RoutingContext ctx, String folder) {
        String username = ctx.get("username");
        String filter = ctx.request().getParam("filter");

        try {
            List<Email> emails = (filter != null && !filter.isEmpty())
                ? mailboxService.filterEmails(username, folder, filter)
                : mailboxService.listEmails(username, folder);

            int unreadInbox = mailboxService.countUnreadEmails(username, MailboxService.INBOX);
            long storageUsed = mailboxService.calculateTotalUserStorage(username);

            Map<String, Object> data = new HashMap<>();
            data.put("username", username);
            data.put("folder", folder);
            data.put("emails", emails);
            data.put("filter", filter != null ? filter : "");
            data.put("unreadInbox", unreadInbox);
            data.put("storageUsed", FileUtils.formatSize(storageUsed));

            render(ctx, "folder.peb", data);
        } catch (IOException e) {
            ctx.fail(500, e);
        }
    }

    private void handleComposePage(RoutingContext ctx) {
        String username = ctx.get("username");
        String to = ctx.request().getParam("to");
        String subject = ctx.request().getParam("subject");
        String replyTo = ctx.request().getParam("replyTo");

        Map<String, Object> data = new HashMap<>();
        data.put("username", username);
        data.put("to", to != null ? to : "");
        data.put("subject", subject != null ? subject : "");
        data.put("content", "");
        data.put("draftId", "");

        // Si c'est une réponse, pré-remplir
        if (replyTo != null && !replyTo.isEmpty()) {
            String[] parts = replyTo.split("/", 2);
            if (parts.length == 2) {
                try {
                    Optional<Email> emailOpt = mailboxService.loadEmail(username, parts[0], parts[1]);
                    if (emailOpt.isPresent()) {
                        Email original = emailOpt.get();
                        data.put("to", original.getFrom());
                        data.put("subject", "Re: " + original.getSubject());
                        data.put("content", "\n\n--- Message original ---\n" + original.getContent());
                    }
                } catch (IOException e) {
                    // Ignorer
                }
            }
        }

        render(ctx, "compose.peb", data);
    }

    private void handleCompose(RoutingContext ctx) {
        String username = ctx.get("username");
        String toStr = ctx.request().getFormAttribute("to");
        String subject = ctx.request().getFormAttribute("subject");
        String content = ctx.request().getFormAttribute("content");
        String action = ctx.request().getFormAttribute("action");
        String draftId = ctx.request().getFormAttribute("draftId");

        // Parser les destinataires
        List<String> recipients = new ArrayList<>();
        if (toStr != null && !toStr.isEmpty()) {
            for (String recipient : toStr.split("[,;\\s]+")) {
                recipient = recipient.trim();
                if (!recipient.isEmpty()) {
                    recipients.add(recipient);
                }
            }
        }

        try {
            // Créer l'email
            Email email = new Email();
            if (draftId != null && !draftId.isEmpty()) {
                email.setId(draftId);
            }
            email.setFrom(username);
            email.setTo(recipients);
            email.setSubject(subject != null ? subject : "");
            email.setContent(content != null ? content : "");
            email.setDate(LocalDateTime.now());
            email.removeTag("unread");

            // Gérer les pièces jointes
            for (FileUpload upload : ctx.fileUploads()) {
                if (upload.size() > 0) {
                    byte[] data = Files.readAllBytes(Path.of(upload.uploadedFileName()));
                    Attachment attachment = mailboxService.saveAttachment(
                        upload.fileName(),
                        upload.contentType(),
                        data
                    );
                    email.addAttachment(attachment);
                    // Supprimer le fichier temporaire
                    Files.deleteIfExists(Path.of(upload.uploadedFileName()));
                }
            }

            if ("draft".equals(action)) {
                // Sauvegarder comme brouillon
                mailboxService.saveDraft(username, email);
                ctx.redirect("/mail/drafts");
            } else {
                // Envoyer l'email
                // Vérifier que tous les destinataires existent
                List<String> invalidRecipients = new ArrayList<>();
                for (String recipient : recipients) {
                    if (!mailboxService.mailboxExists(recipient)) {
                        invalidRecipients.add(recipient);
                    }
                }

                if (!invalidRecipients.isEmpty()) {
                    Map<String, Object> data = new HashMap<>();
                    data.put("username", username);
                    data.put("to", toStr);
                    data.put("subject", subject);
                    data.put("content", content);
                    data.put("error", "Destinataires non trouvés : " + String.join(", ", invalidRecipients));
                    render(ctx, "compose.peb", data);
                    return;
                }

                // Supprimer le brouillon s'il existe
                if (draftId != null && !draftId.isEmpty()) {
                    mailboxService.deleteEmail(username, MailboxService.DRAFT, draftId);
                }

                mailboxService.sendEmail(email);
                ctx.redirect("/mail/outbox");
            }
        } catch (IOException e) {
            ctx.fail(500, e);
        }
    }

    private void handleViewEmail(RoutingContext ctx) {
        String username = ctx.get("username");
        String folder = ctx.pathParam("folder");
        String emailId = ctx.pathParam("id");

        try {
            Optional<Email> emailOpt = mailboxService.loadEmail(username, folder, emailId);
            if (emailOpt.isEmpty()) {
                ctx.redirect("/mail/" + folder);
                return;
            }

            Email email = emailOpt.get();

            // Marquer comme lu si c'est dans l'inbox
            if (MailboxService.INBOX.equals(folder) && email.isUnread()) {
                mailboxService.markAsRead(username, folder, emailId);
                email.markAsRead();
            }

            Map<String, Object> data = new HashMap<>();
            data.put("username", username);
            data.put("folder", folder);
            data.put("email", email);

            render(ctx, "view_email.peb", data);
        } catch (IOException e) {
            ctx.fail(500, e);
        }
    }

    private void handleDeleteEmail(RoutingContext ctx) {
        String username = ctx.get("username");
        String folder = ctx.pathParam("folder");
        String emailId = ctx.pathParam("id");

        try {
            mailboxService.deleteEmail(username, folder, emailId);
            ctx.redirect("/mail/" + folder);
        } catch (IOException e) {
            ctx.fail(500, e);
        }
    }

    private void handleToggleTag(RoutingContext ctx) {
        String username = ctx.get("username");
        String folder = ctx.pathParam("folder");
        String emailId = ctx.pathParam("id");
        String tag = ctx.pathParam("tag");

        try {
            mailboxService.toggleTag(username, folder, emailId, tag);
            ctx.redirect("/mail/view/" + folder + "/" + emailId);
        } catch (IOException e) {
            ctx.fail(500, e);
        }
    }

    private void handleEditDraft(RoutingContext ctx) {
        String username = ctx.get("username");
        String draftId = ctx.pathParam("id");

        try {
            Optional<Email> draftOpt = mailboxService.loadEmail(username, MailboxService.DRAFT, draftId);
            if (draftOpt.isEmpty()) {
                ctx.redirect("/mail/drafts");
                return;
            }

            Email draft = draftOpt.get();

            Map<String, Object> data = new HashMap<>();
            data.put("username", username);
            data.put("to", draft.getToAsString());
            data.put("subject", draft.getSubject());
            data.put("content", draft.getContent());
            data.put("draftId", draft.getId());

            render(ctx, "compose.peb", data);
        } catch (IOException e) {
            ctx.fail(500, e);
        }
    }

    private void handleDownloadAttachment(RoutingContext ctx) {
        String sha256 = ctx.pathParam("sha256");
        String filename = ctx.pathParam("filename");

        try {
            Optional<byte[]> dataOpt = mailboxService.getAttachment(sha256);
            if (dataOpt.isEmpty()) {
                ctx.response().setStatusCode(404).end("Pièce jointe non trouvée");
                return;
            }

            ctx.response()
                .putHeader("Content-Type", "application/octet-stream")
                .putHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                .end(io.vertx.core.buffer.Buffer.buffer(dataOpt.get()));
        } catch (IOException e) {
            ctx.fail(500, e);
        }
    }

    private void handleSettingsPage(RoutingContext ctx) {
        String username = ctx.get("username");
        render(ctx, "settings.peb", Map.of("username", username));
    }

    private void handleChangePassword(RoutingContext ctx) {
        String username = ctx.get("username");
        String oldPassword = ctx.request().getFormAttribute("oldPassword");
        String newPassword = ctx.request().getFormAttribute("newPassword");
        String confirmPassword = ctx.request().getFormAttribute("confirmPassword");

        if (!newPassword.equals(confirmPassword)) {
            render(ctx, "settings.peb", Map.of(
                "username", username,
                "error", "Les nouveaux mots de passe ne correspondent pas"
            ));
            return;
        }

        try {
            userService.changePassword(username, oldPassword, newPassword);
            render(ctx, "settings.peb", Map.of(
                "username", username,
                "success", "Mot de passe modifié avec succès"
            ));
        } catch (IllegalArgumentException e) {
            render(ctx, "settings.peb", Map.of(
                "username", username,
                "error", e.getMessage()
            ));
        } catch (IOException e) {
            render(ctx, "settings.peb", Map.of(
                "username", username,
                "error", "Erreur lors du changement de mot de passe"
            ));
        }
    }

    // ==================== Utilitaires ====================

    private void render(RoutingContext ctx, String template, Map<String, Object> data) {
        templateEngine.render(data, "templates/" + template)
            .onSuccess(buffer -> {
                ctx.response()
                    .putHeader("Content-Type", "text/html; charset=UTF-8")
                    .end(buffer);
            })
            .onFailure(err -> {
                System.err.println("Erreur de rendu du template " + template + ": " + err.getMessage());
                ctx.fail(500, err);
            });
    }
}
