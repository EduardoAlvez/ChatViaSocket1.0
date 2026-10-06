package com.portfolio.chat.servidor;

import com.portfolio.chat.Log;
import com.portfolio.chat.protocolo.Protocolo;
import com.portfolio.chat.protocolo.ProtocoloException;
import com.portfolio.chat.protocolo.Quadro;
import com.portfolio.chat.protocolo.TipoQuadro;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.concurrent.ExecutorService;

/**
 * Atende um cliente conectado: espera o ENTRAR, registra na sala e
 * processa os quadros enquanto a conexão estiver aberta.
 * Roda em uma thread do pool do servidor.
 */
public final class AtendimentoCliente implements Runnable {

    private final Socket socket;
    private final GerenciadorClientes gerenciador;
    private final ExecutorService pool;
    private final int timeoutEntradaMs;

    private ClienteConectado conectado;
    private boolean registrado;

    public AtendimentoCliente(Socket socket, GerenciadorClientes gerenciador, ExecutorService pool) {
        this(socket, gerenciador, pool, Protocolo.TIMEOUT_ENTRADA_MS);
    }

    /** Com timeout de entrada configurável (usado nos testes). */
    AtendimentoCliente(Socket socket, GerenciadorClientes gerenciador, ExecutorService pool, int timeoutEntradaMs) {
        this.socket = socket;
        this.gerenciador = gerenciador;
        this.pool = pool;
        this.timeoutEntradaMs = timeoutEntradaMs;
    }

    @Override
    public void run() {
        try {
            DataInputStream entrada = new DataInputStream(socket.getInputStream());
            DataOutputStream saida = new DataOutputStream(socket.getOutputStream());

            socket.setSoTimeout(timeoutEntradaMs);
            String nome = receberEntrada(entrada, saida);
            if (nome == null) {
                return;
            }

            ClienteConectado novo = new ClienteConectado(nome, saida);
            try {
                gerenciador.registrar(novo);
            } catch (IllegalArgumentException e) {
                direto(saida, Protocolo.erro(e.getMessage()));
                return;
            }
            conectado = novo;
            registrado = true;
            socket.setSoTimeout(0);
            conectado.iniciarEscritor(pool);

            Log.info(nome + " entrou na sala (" + gerenciador.total() + " conectado(s))");
            boasVindas();

            String bruto;
            while ((bruto = entrada.readUTF()) != null) {
                Quadro quadro;
                try {
                    quadro = Protocolo.parse(bruto);
                } catch (ProtocoloException e) {
                    conectado.oferecer(Protocolo.erro(e.getMessage()));
                    continue;
                }
                if (!processar(quadro)) {
                    break;
                }
            }
        } catch (EOFException e) {
            // cliente fechou o socket sem avisar
        } catch (SocketTimeoutException e) {
            Log.info("cliente de " + socket.getRemoteSocketAddress() + " não enviou ENTRAR a tempo");
        } catch (IOException e) {
            Log.erro("erro na conexão com " + socket.getRemoteSocketAddress() + ": " + e.getMessage());
        } finally {
            if (registrado) {
                gerenciador.remover(conectado.nome());
                gerenciador.broadcast(Protocolo.saiu(conectado.nome()));
                Log.info(conectado.nome() + " saiu da sala (" + gerenciador.total() + " conectado(s))");
            }
            try {
                socket.close();
            } catch (IOException ignored) {
                // já morreu
            }
        }
    }

    /** Espera e valida o primeiro quadro (ENTRAR <nome>). Retorna null se recusado. */
    private String receberEntrada(DataInputStream entrada, DataOutputStream saida) throws IOException {
        Quadro primeiro;
        try {
            primeiro = Protocolo.parse(entrada.readUTF());
        } catch (ProtocoloException e) {
            direto(saida, Protocolo.erro("quadro inválido — envie ENTRAR com seu nome"));
            return null;
        }
        if (primeiro.tipo() != TipoQuadro.ENTRAR || primeiro.campos().size() != 1) {
            direto(saida, Protocolo.erro("envie ENTRAR com seu nome para começar"));
            return null;
        }
        try {
            return Protocolo.validarNome(primeiro.campos().get(0));
        } catch (IllegalArgumentException e) {
            direto(saida, Protocolo.erro(e.getMessage()));
            return null;
        }
    }

    private void boasVindas() {
        conectado.oferecer(Protocolo.ok(conectado.nome()));
        conectado.oferecer(Protocolo.listaDe(gerenciador.nicks()));
        gerenciador.broadcast(Protocolo.entrou(conectado.nome()));
        for (String quadroHistorico : gerenciador.quadrosDoHistorico()) {
            conectado.oferecer(quadroHistorico);
        }
    }

    private boolean processar(Quadro quadro) {
        switch (quadro.tipo()) {
            case MSG -> publicarMensagem(quadro);
            case PRIVADO -> enviarPrivado(quadro);
            case LISTA -> conectado.oferecer(Protocolo.listaDe(gerenciador.nicks()));
            case SAIR -> {
                return false;
            }
            default -> conectado.oferecer(
                    Protocolo.erro("quadro não esperado do cliente: " + quadro.tipo()));
        }
        return true;
    }

    private void publicarMensagem(Quadro quadro) {
        try {
            String texto = Protocolo.validarTexto(Protocolo.textoApos(quadro, 0));
            gerenciador.publicarMensagem(Protocolo.horaAtual(), conectado.nome(), texto);
        } catch (IllegalArgumentException e) {
            conectado.oferecer(Protocolo.erro(e.getMessage()));
        }
    }

    private void enviarPrivado(Quadro quadro) {
        if (quadro.campos().size() < 2) {
            conectado.oferecer(Protocolo.erro("uso: /w <nick> <mensagem>"));
            return;
        }
        String para = quadro.campos().get(0);
        try {
            Protocolo.validarNome(para);
            String texto = Protocolo.validarTexto(Protocolo.textoApos(quadro, 1));
            if (para.equalsIgnoreCase(conectado.nome())) {
                conectado.oferecer(Protocolo.erro("você não pode mandar mensagem para si mesmo"));
                return;
            }
            String frame = Protocolo.privadoHora(Protocolo.horaAtual(), conectado.nome(), para, texto);
            if (!gerenciador.enviarPara(para, frame)) {
                conectado.oferecer(Protocolo.erro(para + " não está na sala"));
                return;
            }
            conectado.oferecer(frame);
        } catch (IllegalArgumentException e) {
            conectado.oferecer(Protocolo.erro(e.getMessage()));
        }
    }

    /** Resposta direta antes do cliente estar registrado (sem fila). */
    private static void direto(DataOutputStream saida, String quadro) {
        try {
            saida.writeUTF(quadro);
            saida.flush();
        } catch (IOException ignored) {
            // cliente já foi embora
        }
    }
}
