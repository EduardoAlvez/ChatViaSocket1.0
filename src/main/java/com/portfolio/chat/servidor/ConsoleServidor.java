package com.portfolio.chat.servidor;

import com.portfolio.chat.Log;

import java.util.Scanner;

/**
 * Console do servidor: o que o operador digita vira mensagem para a sala;
 * {@code /sair} encerra o servidor com aviso a todos.
 */
public final class ConsoleServidor implements Runnable {

    private final Servidor servidor;

    public ConsoleServidor(Servidor servidor) {
        this.servidor = servidor;
    }

    @Override
    public void run() {
        try (Scanner scanner = new Scanner(System.in)) {
            Log.info("Console do servidor: digite um texto para avisar todos, /salas para listar, /sair para encerrar");
            while (scanner.hasNextLine()) {
                String linha = scanner.nextLine();
                if (linha.equalsIgnoreCase("/sair")) {
                    servidor.encerrar();
                    return;
                }
                if (linha.equalsIgnoreCase("/salas")) {
                    Log.info("Salas: " + String.join(", ", servidor.salas().nomes()));
                    continue;
                }
                if (linha.isBlank()) {
                    continue;
                }
                try {
                    servidor.publicarServidor(linha);
                } catch (IllegalArgumentException e) {
                    Log.erro(e.getMessage());
                }
            }
        }
    }
}
