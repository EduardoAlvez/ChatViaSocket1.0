package com.portfolio.chat.servidor;

import com.portfolio.chat.Log;
import com.portfolio.chat.protocolo.Protocolo;

import java.io.IOException;

/**
 * Ponto de entrada do servidor.
 * Uso: {@code java -jar chat-via-socket-1.0.0.jar [porta]}
 */
public final class ServidorChat {

    private ServidorChat() {
    }

    public static void main(String[] args) {
        int porta = Protocolo.PORTA_PADRAO;
        if (args.length > 0) {
            try {
                porta = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Porta inválida: " + args[0]);
                System.err.println("Uso: java -jar chat-via-socket-1.0.0.jar [porta]");
                return;
            }
        }

        try {
            Servidor servidor = new Servidor(porta);
            servidor.iniciar();
            Log.info("Servidor iniciado na porta " + servidor.portaEfetiva());
            new Thread(new ConsoleServidor(servidor), "console-servidor").start();
        } catch (IOException e) {
            Log.erro("não foi possível iniciar o servidor na porta " + porta + ": " + e.getMessage());
        }
    }
}
