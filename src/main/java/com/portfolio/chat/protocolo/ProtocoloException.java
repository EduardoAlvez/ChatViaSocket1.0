package com.portfolio.chat.protocolo;

/**
 * Exceção lançada quando um quadro recebido não segue o protocolo.
 */
public class ProtocoloException extends Exception {

    public ProtocoloException(String mensagem) {
        super(mensagem);
    }
}
