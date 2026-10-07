package com.portfolio.chat.cliente;

import com.portfolio.chat.protocolo.Protocolo;
import com.portfolio.chat.protocolo.ProtocoloException;
import com.portfolio.chat.protocolo.Quadro;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.function.Consumer;

/**
 * Fica lendo os quadros do servidor em uma thread própria
 * (para não travar a digitação) e entrega cada um pronto
 * para quem chamou — console imprime, interface gráfica pinta.
 */
public final class RecebedorMensagens implements Runnable {

    private final DataInputStream entrada;
    private final Consumer<Quadro> aoReceber;
    private final Runnable aoEncerrar;

    public RecebedorMensagens(DataInputStream entrada, Consumer<Quadro> aoReceber, Runnable aoEncerrar) {
        this.entrada = entrada;
        this.aoReceber = aoReceber;
        this.aoEncerrar = aoEncerrar;
    }

    @Override
    public void run() {
        try {
            while (true) {
                String bruto = entrada.readUTF();
                try {
                    aoReceber.accept(Protocolo.parse(bruto));
                } catch (ProtocoloException e) {
                    // quadro malformado do outro lado: nada a exibir
                }
            }
        } catch (IOException e) {
            // conexão encerrada por nós ou pelo servidor — o chamador cuida da mensagem
        } finally {
            aoEncerrar.run();
        }
    }
}
