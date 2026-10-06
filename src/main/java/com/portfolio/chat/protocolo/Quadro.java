package com.portfolio.chat.protocolo;

import java.util.List;

/**
 * Um quadro já interpretado: tipo + campos após o tipo.
 * Ex.: "MSG|12:00|ana|olá" vira MSG com campos [12:00, ana, olá].
 */
public record Quadro(TipoQuadro tipo, List<String> campos) {

    public Quadro {
        campos = List.copyOf(campos);
    }
}
