package fr.uge.webmail.service;

import fr.uge.webmail.model.Email;
import io.vertx.core.Vertx;
import io.vertx.core.datagram.DatagramSocket;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service UDP pour recevoir des messages anonymes.
 */
public class UdpService {
    
    private final Vertx vertx;
    private final MailboxService mailboxService;
    private final int port;
    
    // Limite de messages par IP par jour
    private static final int MAX_MESSAGES_PER_IP_PER_DAY = 10;
    
    // Compteur de messages par IP et par jour
    private final Map<String, DailyCounter> messageCounters = new ConcurrentHashMap<>();
    
    private DatagramSocket socket;
    
    public UdpService(Vertx vertx, MailboxService mailboxService, int port) {
        this.vertx = vertx;
        this.mailboxService = mailboxService;
        this.port = port;
    }
    
    /**
     * Démarre le serveur UDP.
     */
    public void start() {
        socket = vertx.createDatagramSocket();
        
        socket.listen(port, "0.0.0.0")
            .onSuccess(s -> {
                System.out.println("✉️  Serveur UDP démarré sur le port " + port);
                
                socket.handler(packet -> {
                    String senderAddress = packet.sender().host();
                    int senderPort = packet.sender().port();
                    String message = packet.data().toString("UTF-8");
                    
                    handleMessage(senderAddress, senderPort, message);
                });
            })
            .onFailure(err -> {
                System.err.println("Erreur lors du démarrage du serveur UDP : " + err.getMessage());
            });
    }
    
    /**
     * Arrête le serveur UDP.
     */
    public void stop() {
        if (socket != null) {
            socket.close();
        }
    }
    
    /**
     * Traite un message UDP reçu.
     * Format attendu :
     * destinataire
     * sujet
     * contenu (multi-lignes)
     */
    private void handleMessage(String senderAddress, int senderPort, String message) {
        // Vérifier la limite de messages
        if (!canSendMessage(senderAddress)) {
            sendResponse(senderAddress, senderPort, 
                "ERREUR: Limite de " + MAX_MESSAGES_PER_IP_PER_DAY + " messages/jour atteinte pour cette IP.");
            return;
        }
        
        try {
            // Parser le message
            String[] lines = message.split("\n", 3);
            
            if (lines.length < 3) {
                sendResponse(senderAddress, senderPort, 
                    "ERREUR: Format invalide. Attendu: destinataire\\nsujet\\ncontenu");
                return;
            }
            
            String recipient = lines[0].trim();
            String subject = lines[1].trim();
            String content = lines[2];
            
            // Vérifier que le destinataire existe
            if (!mailboxService.mailboxExists(recipient)) {
                sendResponse(senderAddress, senderPort, 
                    "ERREUR: Destinataire '" + recipient + "' non trouvé.");
                return;
            }
            
            // Créer l'email anonyme
            Email email = new Email();
            email.setFrom("anonymous@" + senderAddress + ":" + senderPort);
            email.setTo(List.of(recipient));
            email.setSubject(subject);
            email.setContent(content);
            email.setDate(LocalDateTime.now());
            email.addTag("anonymous");
            
            // Sauvegarder dans l'inbox du destinataire
            mailboxService.saveEmail(recipient, MailboxService.INBOX, email);
            
            // Incrémenter le compteur
            incrementMessageCount(senderAddress);
            
            // Envoyer la confirmation
            sendResponse(senderAddress, senderPort, 
                "OK: Message envoyé à " + recipient);
            
            System.out.println("📨 Message anonyme reçu de " + senderAddress + ":" + senderPort + 
                " pour " + recipient);
            
        } catch (Exception e) {
            sendResponse(senderAddress, senderPort, 
                "ERREUR: " + e.getMessage());
        }
    }
    
    /**
     * Envoie une réponse UDP.
     */
    private void sendResponse(String address, int port, String message) {
        socket.send(message, port, address)
            .onFailure(err -> {
                System.err.println("Erreur lors de l'envoi de la réponse UDP : " + err.getMessage());
            });
    }
    
    /**
     * Vérifie si une IP peut encore envoyer des messages aujourd'hui.
     */
    private boolean canSendMessage(String ipAddress) {
        DailyCounter counter = messageCounters.get(ipAddress);
        if (counter == null) {
            return true;
        }
        
        // Réinitialiser si c'est un nouveau jour
        if (!counter.date.equals(LocalDate.now())) {
            messageCounters.remove(ipAddress);
            return true;
        }
        
        return counter.count.get() < MAX_MESSAGES_PER_IP_PER_DAY;
    }
    
    /**
     * Incrémente le compteur de messages pour une IP.
     */
    private void incrementMessageCount(String ipAddress) {
        messageCounters.compute(ipAddress, (ip, counter) -> {
            if (counter == null || !counter.date.equals(LocalDate.now())) {
                DailyCounter newCounter = new DailyCounter(LocalDate.now());
                newCounter.count.incrementAndGet();
                return newCounter;
            }
            counter.count.incrementAndGet();
            return counter;
        });
    }
    
    /**
     * Compteur journalier pour une IP.
     */
    private static class DailyCounter {
        final LocalDate date;
        final AtomicInteger count;
        
        DailyCounter(LocalDate date) {
            this.date = date;
            this.count = new AtomicInteger(0);
        }
    }
}
