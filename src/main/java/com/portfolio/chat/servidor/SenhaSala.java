package com.portfolio.chat.servidor;

import com.portfolio.chat.protocolo.SenhaHash;

/**
 * Senha de sala guardada como hash SHA-256 com salt aleatório.
 * O servidor nunca guarda a senha em texto puro — só salt + hash em hex.
 * Na entrada, a conferência é pela resposta ao desafio ({@link #confereResposta}),
 * também sem a senha passar pelo fio.
 */
public final class SenhaSala {

    private final String saltHex;
    private final String hashHex;

    private SenhaSala(String saltHex, String hashHex) {
        this.saltHex = saltHex;
        this.hashHex = hashHex;
    }

    /** Gera um salt novo e calcula o hash da senha. */
    public static SenhaSala nova(String senha) {
        String salt = SenhaHash.novoSalt();
        return new SenhaSala(salt, SenhaHash.daSenha(senha, salt));
    }

    /**
     * Reconstrói a senha a partir do que estava salvo em disco ou veio do cliente.
     *
     * @throws IllegalArgumentException se salt/hash não estiverem no formato hex
     */
    public static SenhaSala deDados(String saltHex, String hashHex) {
        SenhaHash.validarSalt(saltHex);
        SenhaHash.validarHash(hashHex);
        return new SenhaSala(saltHex, hashHex);
    }

    /** Compara a senha informada com o hash guardado. */
    public boolean confere(String senha) {
        if (senha == null || senha.isEmpty()) {
            return false;
        }
        try {
            return SenhaHash.confere(senha, saltHex, hashHex);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Confere a prova enviada pelo cliente para o nonce deste desafio:
     * resposta tem que ser SHA-256(hash + nonce).
     */
    public boolean confereResposta(String respostaHex, String nonceHex) {
        if (respostaHex == null || nonceHex == null) {
            return false;
        }
        try {
            return SenhaHash.resposta(hashHex, nonceHex).equals(respostaHex);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public String saltHex() {
        return saltHex;
    }

    public String hashHex() {
        return hashHex;
    }
}
