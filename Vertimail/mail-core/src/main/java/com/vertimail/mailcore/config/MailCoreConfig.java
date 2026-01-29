package com.vertimail.mailcore.config;

import java.nio.file.Path;

/**
 * Configuration centralisée de mail-core.
 * Le dataRoot est fourni par propriété système ou variable d'env.
 */
public final class MailCoreConfig {

    public static final String SYS_PROP_DATA_ROOT = "vertimail.dataRoot";
    public static final String ENV_DATA_ROOT = "VERTIMAIL_DATA_ROOT";

    private MailCoreConfig() {}

    /**
     * Résout le dataRoot :
     * 1) -Dvertimail.dataRoot=...
     * 2) env VERTIMAIL_DATA_ROOT=...
     * 3) défaut : ./data
     */
    public static Path resolveDataRoot() {
        String p = System.getProperty(SYS_PROP_DATA_ROOT);
        if (p != null && !p.isBlank()) return Path.of(p);

        String e = System.getenv(ENV_DATA_ROOT);
        if (e != null && !e.isBlank()) return Path.of(e);

        return Path.of("data"); // défaut (à la racine du projet final)
    }
}

