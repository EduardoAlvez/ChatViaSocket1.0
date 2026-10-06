package com.portfolio.chat.protocolo;

/**
 * Tipos de quadros trocados entre cliente e servidor.
 * Cliente envia: ENTRAR, SAIR, MSG, PRIVADO, LISTA, CRIARSALA, ENTRASALA, SALAS.
 * Server envia: OK, ERRO, ENTROU, SAIU, MSG, PRIVADO, HIST, LISTA, FIM, SALOK, SALAS.
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
    FIM,
    CRIARSALA,
    ENTRASALA,
    SALAS,
    SALOK
}
