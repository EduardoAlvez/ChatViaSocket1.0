package com.portfolio.chat.servidor;

import com.portfolio.chat.Log;
import com.portfolio.chat.protocolo.Protocolo;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Ciclo de vida do servidor: aceita conexões, distribui para o pool de
 * threads e encerra com elegância (avisa os clientes antes de fechar).
 */
public final class Servidor {

    private final int porta;
    private final GerenciadorSalas salas;
    private final ExecutorService pool;
    private final int timeoutEntradaMs;

    private ServerSocket serverSocket;
    private volatile boolean rodando;
    private int portaEfetiva;

    public Servidor(int porta) {
        this(porta, new GerenciadorSalas(), Protocolo.TIMEOUT_ENTRADA_MS);
    }

    /** Permite trocar o gerenciador de salas (testes com limites menores). */
    public Servidor(int porta, GerenciadorSalas salas) {
        this(porta, salas, Protocolo.TIMEOUT_ENTRADA_MS);
    }

    Servidor(int porta, GerenciadorSalas salas, int timeoutEntradaMs) {
        this.porta = porta;
        this.salas = salas;
        this.timeoutEntradaMs = timeoutEntradaMs;
        this.pool = Executors.newCachedThreadPool();
    }

    /** Abre o socket e lança a thread que aceita conexões. */
    public synchronized void iniciar() throws IOException {
        if (rodando) {
            throw new IllegalStateException("servidor já iniciado");
        }
        serverSocket = new ServerSocket(porta);
        portaEfetiva = serverSocket.getLocalPort();
        rodando = true;
        Thread aceitador = new Thread(this::aceitar, "aceitador-conexoes");
        aceitador.start();
    }

    private void aceitar() {
        while (rodando) {
            try {
                Socket socket = serverSocket.accept();
                pool.execute(new AtendimentoCliente(socket, salas, pool, timeoutEntradaMs));
            } catch (IOException e) {
                if (rodando) {
                    Log.erro("falha ao aceitar conexão: " + e.getMessage());
                }
            }
        }
    }

    /** Porta real em uso (útil quando o teste pede porta 0 = efêmera). */
    public int portaEfetiva() {
        return portaEfetiva;
    }

    public GerenciadorSalas salas() {
        return salas;
    }

    public boolean rodando() {
        return rodando;
    }

    /** Mensagem do operador do servidor para todos de todas as salas. */
    public void publicarServidor(String texto) {
        String limpo = Protocolo.validarTexto(texto);
        salas.broadcastTodas(
                Protocolo.mensagemHora(Protocolo.horaAtual(), "Servidor", limpo));
    }

    /** Avisa todo mundo, fecha as conexões e para de aceitar novas. */
    public synchronized void encerrar() {
        if (!rodando) {
            return;
        }
        rodando = false;
        salas.encerrarTodas(Protocolo.fim("Servidor encerrado"));
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (IOException ignored) {
            // já fechou
        }
        pool.shutdown();
        try {
            pool.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        Log.info("Servidor encerrado");
    }
}
