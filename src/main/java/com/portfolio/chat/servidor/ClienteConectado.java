package com.portfolio.chat.servidor;

import com.portfolio.chat.protocolo.Protocolo;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Um cliente registrado na sala: apelido + fila de envio.
 *
 * <p>Todas as escritas passam por uma fila por cliente e uma única thread
 * de escrita drena a fila. Assim o broadcast nunca bloqueia no socket de
 * um cliente lento — se a fila enche, o cliente é desconectado.</p>
 */
public final class ClienteConectado {

    /** Objeto sentinela: comparação por referência (==), nunca igual a um quadro real. */
    private static final String POISON = new String("chat-sair-da-fila");

    private final String nome;
    private final DataOutputStream saida;
    private final BlockingQueue<String> fila;
    private final CountDownLatch escritorTerminou = new CountDownLatch(1);
    private volatile boolean fechado;
    private volatile boolean escritorIniciado;

    public ClienteConectado(String nome, DataOutputStream saida) {
        this(nome, saida, 100);
    }

    /** Cria o cliente com capacidade de fila própria (usada nos testes). */
    public ClienteConectado(String nome, DataOutputStream saida, int capacidadeFila) {
        this.nome = nome;
        this.saida = saida;
        this.fila = new ArrayBlockingQueue<>(capacidadeFila);
    }

    public String nome() {
        return nome;
    }

    public boolean fechado() {
        return fechado;
    }

    /**
     * Coloca um quadro na fila sem nunca bloquear.
     *
     * @return false se a fila está cheia ou o cliente já foi encerrado
     */
    public boolean oferecer(String quadro) {
        if (fechado) {
            return false;
        }
        return fila.offer(quadro);
    }

    /** Inicia a thread que drena a fila para o socket (uma por cliente). */
    void iniciarEscritor(ExecutorService pool) {
        escritorIniciado = true;
        pool.submit(() -> {
            try {
                while (true) {
                    String quadro = fila.poll(100, TimeUnit.MILLISECONDS);
                    if (quadro == POISON) {
                        break;
                    }
                    if (quadro != null) {
                        saida.writeUTF(quadro);
                        saida.flush();
                        continue;
                    }
                    if (fechado) {
                        break;
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (IOException e) {
                // conexão morreu; o leitor do atendimento cuida da limpeza
            } finally {
                escritorTerminou.countDown();
            }
        });
    }

    /** Encerra na hora: fecha o stream; a thread de escrita sai em seguida. */
    public void encerrar() {
        if (fechado) {
            return;
        }
        fechado = true;
        fechar();
    }

    /**
     * Encerra com elegância: entrega o que já está na fila
     * e só então fecha o stream.
     */
    void encerrarDrenado(long timeoutMs) {
        if (fechado) {
            return;
        }
        fila.offer(POISON);
        fechado = true;
        if (escritorIniciado) {
            try {
                escritorTerminou.await(timeoutMs, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        fechar();
    }

    /** Próximo quadro sem bloquear — usado nos testes. */
    String semBloquear() {
        return fila.poll();
    }

    private void fechar() {
        try {
            saida.close();
        } catch (IOException ignored) {
            // já morreu
        }
    }
}
