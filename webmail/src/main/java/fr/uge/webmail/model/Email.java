package fr.uge.webmail.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Représente un courrier électronique.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Email {
    
    @JsonProperty("id")
    private String id;
    
    @JsonProperty("from")
    private String from;
    
    @JsonProperty("to")
    private List<String> to;
    
    @JsonProperty("subject")
    private String subject;
    
    @JsonProperty("content")
    private String content;
    
    @JsonProperty("date")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime date;
    
    @JsonProperty("attachments")
    private List<Attachment> attachments;
    
    @JsonProperty("tags")
    private Set<String> tags;
    
    @JsonProperty("folder")
    private String folder;
    
    /**
     * Constructeur par défaut pour Jackson.
     */
    public Email() {
        this.id = UUID.randomUUID().toString();
        this.to = new ArrayList<>();
        this.attachments = new ArrayList<>();
        this.tags = new HashSet<>();
        this.tags.add("unread"); // Par défaut, un mail est non lu
    }
    
    /**
     * Constructeur complet.
     */
    public Email(String from, List<String> to, String subject, String content) {
        this();
        this.from = from;
        this.to = new ArrayList<>(to);
        this.subject = subject;
        this.content = content;
        this.date = LocalDateTime.now();
    }
    
    // Getters et Setters
    
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getFrom() {
        return from;
    }
    
    public void setFrom(String from) {
        this.from = from;
    }
    
    public List<String> getTo() {
        return to;
    }
    
    public void setTo(List<String> to) {
        this.to = to;
    }
    
    public String getSubject() {
        return subject;
    }
    
    public void setSubject(String subject) {
        this.subject = subject;
    }
    
    public String getContent() {
        return content;
    }
    
    public void setContent(String content) {
        this.content = content;
    }
    
    public LocalDateTime getDate() {
        return date;
    }
    
    public void setDate(LocalDateTime date) {
        this.date = date;
    }
    
    public List<Attachment> getAttachments() {
        return attachments;
    }
    
    public void setAttachments(List<Attachment> attachments) {
        this.attachments = attachments;
    }
    
    public void addAttachment(Attachment attachment) {
        this.attachments.add(attachment);
    }
    
    public Set<String> getTags() {
        return tags;
    }
    
    public void setTags(Set<String> tags) {
        this.tags = tags;
    }
    
    public void addTag(String tag) {
        this.tags.add(tag);
    }
    
    public void removeTag(String tag) {
        this.tags.remove(tag);
    }
    
    public boolean hasTag(String tag) {
        return this.tags.contains(tag);
    }
    
    public String getFolder() {
        return folder;
    }
    
    public void setFolder(String folder) {
        this.folder = folder;
    }
    
    /**
     * Marque le mail comme lu (supprime le tag "unread").
     */
    public void markAsRead() {
        this.tags.remove("unread");
    }
    
    /**
     * Vérifie si le mail est non lu.
     */
    public boolean isUnread() {
        return this.tags.contains("unread");
    }
    
    /**
     * Vérifie si le mail est important.
     */
    public boolean isImportant() {
        return this.tags.contains("important");
    }
    
    /**
     * Retourne un résumé du contenu (50 premiers caractères).
     */
    public String getContentPreview() {
        if (content == null || content.isEmpty()) {
            return "";
        }
        return content.length() > 50 ? content.substring(0, 50) + "..." : content;
    }
    
    /**
     * Retourne la liste des destinataires sous forme de chaîne.
     */
    public String getToAsString() {
        return String.join(", ", to);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Email email = (Email) o;
        return Objects.equals(id, email.id);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
    
    @Override
    public String toString() {
        return "Email{" +
                "id='" + id + '\'' +
                ", from='" + from + '\'' +
                ", to=" + to +
                ", subject='" + subject + '\'' +
                ", date=" + date +
                '}';
    }
}
