package com.portfolio.chat.cliente;

import com.portfolio.chat.protocolo.Protocolo;
import com.portfolio.chat.protocolo.Quadro;

/**
 * Converte um quadro do protocolo em uma linha amigável para o console.
 * Recebe o próprio apelido para diferenciar "você" dos outros.
 */
public final class Renderizador {

    private final String eu;

    public Renderizador(String eu) {
        this.eu = eu;
    }

    public String render(Quadro quadro) {
        return switch (quadro.tipo()) {
            case OK -> "Conectado como " + campo(quadro, 0);
            case ERRO -> "[!] " + Protocolo.textoApos(quadro, 0);
            case LISTA -> "Na sala: " + campo(quadro, 0);
            case ENTROU -> eu.equalsIgnoreCase(campo(quadro, 0))
                    ? "Você entrou na sala"
                    : "• " + campo(quadro, 0) + " entrou";
            case SAIU -> "• " + campo(quadro, 0) + " saiu";
            case MSG -> "[" + campo(quadro, 0) + "] " + campo(quadro, 1) + ": "
                    + Protocolo.textoApos(quadro, 2);
            case HIST -> "[" + campo(quadro, 0) + "] " + campo(quadro, 1) + ": "
                    + Protocolo.textoApos(quadro, 2) + " (histórico)";
            case PRIVADO -> renderPrivado(quadro);
            case FIM -> "[!] " + Protocolo.textoApos(quadro, 0);
            case SALOK -> "Você está na sala " + campo(quadro, 0);
            case SALAS -> "Salas: " + campo(quadro, 0);
            case SAIR, ENTRAR, CRIARSALA, ENTRASALA, ENTRASALAH, CHAVE -> "";
        };
    }

    /**
     * PRIVADO|hora|de|para|texto — mostra "de" quando a mensagem é para você
     * e "para" quando é a sua saída.
     */
    private String renderPrivado(Quadro quadro) {
        String hora = campo(quadro, 0);
        String de = campo(quadro, 1);
        String para = campo(quadro, 2);
        String texto = Protocolo.textoApos(quadro, 3);
        if (eu.equalsIgnoreCase(para)) {
            return "[" + hora + "] (privado de " + de + "): " + texto;
        }
        if (eu.equalsIgnoreCase(de)) {
            return "[" + hora + "] (privado para " + para + "): " + texto;
        }
        return "[" + hora + "] (privado): " + texto;
    }

    private static String campo(Quadro quadro, int indice) {
        if (indice >= quadro.campos().size()) {
            return "";
        }
        return quadro.campos().get(indice);
    }
}
