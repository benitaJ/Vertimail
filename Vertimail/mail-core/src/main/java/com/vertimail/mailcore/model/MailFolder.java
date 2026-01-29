package com.vertimail.mailcore.model;

public enum MailFolder {
    INBOX("inbox"),
    OUTBOX("outbox"),
    DRAFT("draft"),
    TRASH("trash");

    private final String dirName;

    MailFolder(String dirName) {
        this.dirName = dirName;
    }

    public String dirName() {
        return dirName;
    }
}
