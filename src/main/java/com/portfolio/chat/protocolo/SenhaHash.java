package com.portfolio.chat.protocolo;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

/**
 * Hash de senha de sala usado pelos dois lados da conexão.
 *
 * <p>O cliente monta o hash antes de enviar — a senha nunca trafega em texto
 * puro. Na entrada em sala com senha, o servidor manda um nonce e o cliente
 * responde com {@link #resposta(String, String)} sobre o próprio hash,
 * também sem revelar a senha.</p>
 */
public final class SenhaHash {

    private static final SecureRandom ALEATORIO = new SecureRandom();

    private SenhaHash() {
    }

    /** Salt aleatório de 16 bytes em hex (32 caracteres). */
    public static String novoSalt() {
        return hex(novosBytes(16));
    }

    /** Nonce aleatório de 16 bytes em hex, usado no desafio de entrada. */
    public static String novoNonce() {
        return hex(novosBytes(16));
    }

    /**
     * Hash da senha com salt: SHA-256(saltBytes + senha) em hex (64 chars).
     *
     * @throws IllegalArgumentException se senha ou salt forem inválidos
     */
    public static String daSenha(String senha, String saltHex) {
        String limpa = Protocolo.validarSenha(senha);
        if (limpa.isEmpty()) {
            throw new IllegalArgumentException("a senha não pode ser vazia");
        }
        validarSalt(saltHex);
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(hexParaBytes(saltHex));
            return hex(md.digest(limpa.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }

    /**
     * Resposta ao desafio do servidor: SHA-256(hashHex + nonceHex) em hex.
     * Só quem sabe a senha consegue montar o hash que entra nessa conta.
     *
     * @throws IllegalArgumentException se hash ou nonce não forem hex válido
     */
    public static String resposta(String hashHex, String nonceHex) {
        validarHash(hashHex);
        validarNonce(nonceHex);
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return hex(md.digest((hashHex + nonceHex).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }

    /** Verdadeiro se o hash bate com a senha (usado nos testes e conferências). */
    public static boolean confere(String senha, String saltHex, String hashHex) {
        return daSenha(senha, saltHex).equals(hashHex);
    }

    // ---------------------------------------------------------------
    // Validações de formato
    // ---------------------------------------------------------------

    public static void validarSalt(String hex) {
        validarHex(hex, 32, "salt");
    }

    public static void validarNonce(String hex) {
        validarHex(hex, 32, "nonce");
    }

    public static void validarHash(String hex) {
        validarHex(hex, 64, "hash");
    }

    private static void validarHex(String valor, int tamanho, String rotulo) {
        if (valor == null || valor.length() != tamanho) {
            throw new IllegalArgumentException(rotulo + " inválido");
        }
        for (char c : valor.toCharArray()) {
            boolean digito = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
            if (!digito) {
                throw new IllegalArgumentException(rotulo + " inválido");
            }
        }
    }

    private static byte[] novosBytes(int tamanho) {
        byte[] bytes = new byte[tamanho];
        ALEATORIO.nextBytes(bytes);
        return bytes;
    }

    static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    static byte[] hexParaBytes(String hex) {
        byte[] bytes = new byte[hex.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return bytes;
    }
}
