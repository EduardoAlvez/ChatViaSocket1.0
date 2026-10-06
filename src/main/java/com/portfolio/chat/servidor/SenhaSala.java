package com.portfolio.chat.servidor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

/**
 * Senha de sala guardada como hash SHA-256 com salt aleatório.
 * O servidor nunca guarda a senha em texto puro — só salt + hash em hex.
 */
public final class SenhaSala {

    private static final SecureRandom ALEATORIO = new SecureRandom();

    private final String saltHex;
    private final String hashHex;

    private SenhaSala(String saltHex, String hashHex) {
        this.saltHex = saltHex;
        this.hashHex = hashHex;
    }

    /** Gera um salt novo e calcula o hash da senha. */
    public static SenhaSala nova(String senha) {
        byte[] salt = new byte[16];
        ALEATORIO.nextBytes(salt);
        return new SenhaSala(hex(salt), hash(salt, senha));
    }

    /** Reconstrói a senha a partir do que estava salvo em disco. */
    public static SenhaSala deDados(String saltHex, String hashHex) {
        return new SenhaSala(saltHex, hashHex);
    }

    /** Compara a senha informada com o hash guardado. */
    public boolean confere(String senha) {
        if (senha == null || senha.isEmpty()) {
            return false;
        }
        return hash(hexParaBytes(saltHex), senha).equals(hashHex);
    }

    public String saltHex() {
        return saltHex;
    }

    public String hashHex() {
        return hashHex;
    }

    private static String hash(byte[] salt, String senha) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(salt);
            return hex(md.digest(senha.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static byte[] hexParaBytes(String hex) {
        byte[] bytes = new byte[hex.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return bytes;
    }
}
