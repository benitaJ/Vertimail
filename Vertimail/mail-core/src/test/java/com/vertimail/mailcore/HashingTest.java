package com.vertimail.mailcore;

import com.vertimail.mailcore.util.Hashing;

import java.nio.file.Path;

public class HashingTest {

    public static void main(String[] args) throws Exception {
        Path p = Path.of("pom.xml");// ou n’importe quel fichier texte
        System.out.println(Hashing.sha256Hex(p));
    }
}

