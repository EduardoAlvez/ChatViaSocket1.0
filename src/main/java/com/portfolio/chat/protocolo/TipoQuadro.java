package com.portfolio.chat.protocolo;

/**
 * Tipos de quadro trocados entre cliente e servidor.
 * Cliente envia: ENTRAR, SAIR, MSG, PRIVADO, LISTA.
 * Server envia: OK, ERRO, ENTROU, SAIU, MSG, PRIVADO, HIST, LISTA, FIM.
 */
public enum TipoQuadro {
    ENTRAR,
    SAIR,
    MSG,
    PRIVADO,
    LISTA,
    OK,
    ERRO,
    ENTROU,
    SAIU,
    HIST,
    FIM
}
