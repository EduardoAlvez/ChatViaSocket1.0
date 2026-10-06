package com.portfolio.chat.cliente;

import com.portfolio.chat.protocolo.Protocolo;
import com.portfolio.chat.protocolo.ProtocoloException;
import com.portfolio.chat.protocolo.Quadro;

import java.io.DataInputStream;
import java.io.IOException;

/**
 * Fica lendo os quadros do servidor em uma thread própria
 * (para não travar a digitação) e imprime na tela.
 */
public final class RecebedorMensagens implements Runnable {

    private final DataInputStream entrada;
    private final Renderizador renderizador;
    private final Runnable aoEncerrar;

    public RecebedorMensagens(DataInputStream entrada, Renderizador renderizador, Runnable aoEncerrar) {
        this.entrada = entrada;
        this.renderizador = renderizador;
        this.aoEncerrar = aoEncerrar;
    }

    @Override
    public void run() {
        try {
            while (true) {
                String bruto = entrada.readUTF();
                Quadro quadro;
                try {
                    quadro = Protocolo.parse(bruto);
                } catch (ProtocoloException e) {
                    System.out.println(bruto);
                    continue;
                }
                System.out.println(renderizador.render(quadro));
            }
        } catch (IOException e) {
            // conexão encerrada por nós ou pelo servidor — o loop principal cuida da mensagem
        } finally {
            aoEncerrar.run();
        }
    }
}
