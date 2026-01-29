package com.vertimail.mailcore.model;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Représente un courrier électronique stocké en JSON sur disque.
 * Les pièces jointes sont référencées par (filename, sha256).
 */
public class Mail {

    /** Identifiant unique du mail (UUID en String) */
    private String id;

    /** Expéditeur (username interne) */
    private String from;

    /** Destinataires (usernames internes) */
    private List<String> to = new ArrayList<>();

    /** Date d'envoi / création (ISO-8601) */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private LocalDateTime date;

    /** Sujet */
    private String subject;

    /** Contenu en texte brut */
    private String content;

    /** Références des pièces jointes (filename + sha256) */
    private List<AttachmentRef> attachments = new ArrayList<>();

    /** Tags (ex: unread, important, etc.) */
    private Set<String> tags = new HashSet<>();

    /**
     * Date de suppression logique (si le mail est dans trash).
     * null sinon.
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private LocalDateTime deletedAt;

    // Constructeur vide requis par Jackson
    public Mail() {}

    // Getters / Setters

    public String getId() {
        return id;
    }

    public Mail setId(String id) {
        this.id = id;
        return this;
    }

    public String getFrom() {
        return from;
    }

    public Mail setFrom(String from) {
        this.from = from;
        return this;
    }

    public List<String> getTo() {
        return to;
    }

    public Mail setTo(List<String> to) {
        this.to = (to == null) ? new ArrayList<>() : to;
        return this;
    }

    public LocalDateTime getDate() {
        return date;
    }

    public Mail setDate(LocalDateTime date) {
        this.date = date;
        return this;
    }

    public String getSubject() {
        return subject;
    }

    public Mail setSubject(String subject) {
        this.subject = subject;
        return this;
    }

    public String getContent() {
        return content;
    }

    public Mail setContent(String content) {
        this.content = content;
        return this;
    }

    public List<AttachmentRef> getAttachments() {
        return attachments;
    }

    public Mail setAttachments(List<AttachmentRef> attachments) {
        this.attachments = (attachments == null) ? new ArrayList<>() : attachments;
        return this;
    }

    public Set<String> getTags() {
        return tags;
    }

    public Mail setTags(Set<String> tags) {
        this.tags = (tags == null) ? new HashSet<>() : tags;
        return this;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    public Mail setDeletedAt(LocalDateTime deletedAt) {
        this.deletedAt = deletedAt;
        return this;
    }

    // Helpers pratiques

    public boolean hasTag(String tag) {
        return tags != null && tags.contains(tag);
    }

    public void addTag(String tag) {
        if (tags == null) tags = new HashSet<>();
        tags.add(tag);
    }

    public void removeTag(String tag) {
        if (tags != null) tags.remove(tag);
    }
}
