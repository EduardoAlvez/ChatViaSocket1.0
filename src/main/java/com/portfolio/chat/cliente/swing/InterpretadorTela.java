package com.portfolio.chat.cliente.swing;

import com.portfolio.chat.protocolo.Protocolo;
import com.portfolio.chat.protocolo.Quadro;

import java.util.Arrays;
import java.util.List;

/**
 * Converte um quadro do protocolo em um evento pronto para a tela.
 * É puro (sem Swing) — a testabilidade fica toda aqui; o JFrame só pinta.
 */
public final class InterpretadorTela {

    /** O que a tela deve fazer com cada quadro. */
    public sealed interface EventoTela {

        /** Uma linha de texto já interpretada (hora/autor/texto dependem do tipo). */
        record Linha(TipoTexto tipo, String hora, String autor, String texto) implements EventoTela {
        }

        /** Troca completa da lista de participantes. */
        record Participantes(List<String> nicks) implements EventoTela {
            public Participantes {
                nicks = List.copyOf(nicks);
            }
        }

        /** O cliente mudou de sala. */
        record SalaAtual(String nome) implements EventoTela {
        }

        /** Resposta ao pedido de lista de salas. */
        record SalasDisponiveis(List<String> nomes) implements EventoTela {
            public SalasDisponiveis {
                nomes = List.copyOf(nomes);
            }
        }
    }

    /** Como a tela colore/formata cada tipo de linha. */
    public enum TipoTexto {
        PUBLICA,
        HISTORICO,
        PRIVADA_RECEBIDA,
        PRIVADA_ENVIADA,
        SISTEMA
    }

    private final String eu;

    public InterpretadorTela(String eu) {
        this.eu = eu;
    }

    /**
     * Interpreta um quadro vindo do servidor.
     *
     * @return o evento para a tela, ou null se o quadro não vira linha
     *         (quadros que só o servidor deveria receber)
     */
    public EventoTela interpretar(Quadro quadro) {
        return switch (quadro.tipo()) {
            case OK -> new EventoTela.Linha(TipoTexto.SISTEMA, "", "",
                    "Conectado como " + campo(quadro, 0));
            case ERRO -> new EventoTela.Linha(TipoTexto.SISTEMA, "", "",
                    Protocolo.textoApos(quadro, 0));
            case FIM -> new EventoTela.Linha(TipoTexto.SISTEMA, "", "",
                    Protocolo.textoApos(quadro, 0));
            case ENTROU -> new EventoTela.Linha(TipoTexto.SISTEMA, "", "",
                    eu.equalsIgnoreCase(campo(quadro, 0))
                            ? "Você entrou na sala"
                            : "• " + campo(quadro, 0) + " entrou");
            case SAIU -> new EventoTela.Linha(TipoTexto.SISTEMA, "", "",
                    "• " + campo(quadro, 0) + " saiu");
            case MSG -> new EventoTela.Linha(TipoTexto.PUBLICA,
                    campo(quadro, 0), campo(quadro, 1), Protocolo.textoApos(quadro, 2));
            case HIST -> new EventoTela.Linha(TipoTexto.HISTORICO,
                    campo(quadro, 0), campo(quadro, 1), Protocolo.textoApos(quadro, 2));
            case PRIVADO -> interpretarPrivado(quadro);
            case LISTA -> new EventoTela.Participantes(listaDe(campo(quadro, 0)));
            case SALOK -> new EventoTela.SalaAtual(campo(quadro, 0));
            case SALAS -> new EventoTela.SalasDisponiveis(listaDe(campo(quadro, 0)));
            case SAIR, ENTRAR, CRIARSALA, ENTRASALA -> null;
        };
    }

    private static List<String> listaDe(String texto) {
        if (texto == null || texto.isEmpty()) {
            return List.of();
        }
        return Arrays.asList(texto.split(",", -1));
    }

    /**
     * PRIVADO|hora|de|para|texto — mostra "de" quando é para você
     * e "para" quando é a sua saída.
     */
    private EventoTela interpretarPrivado(Quadro quadro) {
        String hora = campo(quadro, 0);
        String de = campo(quadro, 1);
        String para = campo(quadro, 2);
        String texto = Protocolo.textoApos(quadro, 3);
        if (eu.equalsIgnoreCase(para)) {
            return new EventoTela.Linha(TipoTexto.PRIVADA_RECEBIDA, hora, de, texto);
        }
        if (eu.equalsIgnoreCase(de)) {
            return new EventoTela.Linha(TipoTexto.PRIVADA_ENVIADA, hora, para, texto);
        }
        return new EventoTela.Linha(TipoTexto.PRIVADA_RECEBIDA, hora, de, texto);
    }

    private static String campo(Quadro quadro, int indice) {
        if (indice >= quadro.campos().size()) {
            return "";
        }
        return quadro.campos().get(indice);
    }
}
