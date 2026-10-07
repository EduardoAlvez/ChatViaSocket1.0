package com.portfolio.chat.protocolo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SenhaHashTest {

    @Test
    void mesmoSaltEGeramMesmoHash() {
        String salt = SenhaHash.novoSalt();
        assertTrue(SenhaHash.confere("senha1", salt, SenhaHash.daSenha("senha1", salt)));
    }

    @Test
    void saltDiferenteGeraHashDiferente() {
        String hashA = SenhaHash.daSenha("senha1", SenhaHash.novoSalt());
        String hashB = SenhaHash.daSenha("senha1", SenhaHash.novoSalt());
        assertNotEquals(hashA, hashB);
    }

    @Test
    void senhaErradaNaoConfere() {
        String salt = SenhaHash.novoSalt();
        assertFalse(SenhaHash.confere("errada", salt, SenhaHash.daSenha("senha1", salt)));
    }

    @Test
    void respostaMudaComONonce() {
        String salt = SenhaHash.novoSalt();
        String hash = SenhaHash.daSenha("senha1", salt);
        String nonce = SenhaHash.novoNonce();

        assertNotEquals(SenhaHash.resposta(hash, nonce),
                SenhaHash.resposta(hash, "ffffffffffffffffffffffffffffffff"));
        assertEquals(SenhaHash.resposta(hash, nonce), SenhaHash.resposta(hash, nonce));
    }

    @Test
    void saltENonceSaoHexDeTamanhoFixo() {
        SenhaHash.validarSalt(SenhaHash.novoSalt());
        SenhaHash.validarNonce(SenhaHash.novoNonce());
        assertThrows(IllegalArgumentException.class, () -> SenhaHash.validarSalt("xyz"));
        assertThrows(IllegalArgumentException.class, () -> SenhaHash.validarNonce("xyz"));
    }

    @Test
    void senhaInvalidaReprovada() {
        String salt = SenhaHash.novoSalt();
        assertThrows(IllegalArgumentException.class, () -> SenhaHash.daSenha("abc", salt));
        assertThrows(IllegalArgumentException.class, () -> SenhaHash.daSenha(null, salt));
        assertThrows(IllegalArgumentException.class, () -> SenhaHash.daSenha("senha1", "nao-eh-hex"));
    }
}
