package com.portfolio.chat.servidor;

import com.portfolio.chat.protocolo.SenhaHash;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SenhaSalaTest {

    @Test
    void confereSenhaCertaERejetaErrada() {
        SenhaSala senha = SenhaSala.nova("segredo");

        assertTrue(senha.confere("segredo"));
        assertFalse(senha.confere("errada"));
        assertFalse(senha.confere(""));
        assertFalse(senha.confere(null));
    }

    @Test
    void confereRespostaDoDesafio() {
        SenhaSala senha = SenhaSala.nova("segredo");
        String hash = senha.hashHex();
        String nonce = "00112233445566778899aabbccddeeff";

        assertTrue(senha.confereResposta(SenhaHash.resposta(hash, nonce), nonce));
        assertFalse(senha.confereResposta(
                SenhaHash.resposta(hash, "ffffffffffffffffffffffffffffffff"), nonce));
        assertFalse(senha.confereResposta("beef", nonce));
        assertFalse(senha.confereResposta(null, nonce));
    }

    @Test
    void guardaDadosQueVoltamDoDisco() {
        SenhaSala original = SenhaSala.nova("minha123");

        SenhaSala carregada = SenhaSala.deDados(original.saltHex(), original.hashHex());

        assertTrue(carregada.confere("minha123"));
        assertFalse(carregada.confere("minha124"));
    }

    @Test
    void cadaSalaTemSeuProprioSalt() {
        assertNotEquals(SenhaSala.nova("mesma").saltHex(), SenhaSala.nova("mesma").saltHex());
    }

    @Test
    void guardaHashENaoASenhaEmTextoPuro() {
        SenhaSala senha = SenhaSala.nova("topsecret1");

        assertFalse(senha.hashHex().contains("topsecret1"));
        assertFalse(senha.saltHex().contains("topsecret1"));
    }
}
