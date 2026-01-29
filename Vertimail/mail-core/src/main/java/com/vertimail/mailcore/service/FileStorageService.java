package com.vertimail.mailcore.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.vertimail.mailcore.model.Mail;

import java.io.IOException;
import java.nio.file.*;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class FileStorageService {

    private final ObjectMapper mapper;

    public FileStorageService() {
        mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public void ensureDir(Path dir) throws IOException {
        Files.createDirectories(dir);
    }

    public void writeMail(Path file, Mail mail) throws IOException {
        ensureDir(file.getParent());
        Path tmp = file.resolveSibling(file.getFileName().toString() + ".tmp");
        mapper.writerWithDefaultPrettyPrinter().writeValue(tmp.toFile(), mail);
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    public Mail readMail(Path file) throws IOException {
        return mapper.readValue(file.toFile(), Mail.class);
    }

    public List<Path> listJsonFiles(Path folder) throws IOException {
        if (!Files.exists(folder)) return List.of();
        try (Stream<Path> s = Files.list(folder)) {
            return s.filter(p -> p.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .collect(Collectors.toList());
        }
    }

    public void deleteIfExists(Path file) throws IOException {
        Files.deleteIfExists(file);
    }
}

