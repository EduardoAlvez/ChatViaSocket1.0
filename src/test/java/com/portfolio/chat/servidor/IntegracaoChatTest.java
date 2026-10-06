package com.portfolio.chat.servidor;

import com.portfolio.chat.protocolo.Protocolo;
import com.portfolio.chat.protocolo.ProtocoloException;
import com.portfolio.chat.protocolo.Quadro;
import com.portfolio.chat.protocolo.TipoQuadro;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Teste de ponta a ponta: sobe o servidor de verdade em porta efêmera e
 * conversa com ele por sockets reais, como faria um cliente de verdade.
 */
class IntegracaoChatTest {

    private Servidor servidor;
    private final List<Socket> abertos = new ArrayList<>();

    @BeforeEach
    void sobeServidor() throws IOException {
        servidor = new Servidor(0);
        servidor.iniciar();
    }

    @AfterEach
    void desceServidor() {
        servidor.encerrar();
        for (Socket socket : abertos) {
            try {
                socket.close();
            } catch (IOException ignored) {
                // já fechou
            }
        }
        abertos.clear();
    }

    // ---------------------------------------------------------------
    // Cenários
    // ---------------------------------------------------------------

    @Test
    void clienteEntraERecebeBoasVindas() throws Exception {
        ClienteTeste ana = entrar("ana");

        assertEquals("LISTA|ana", proximo(ana, TipoQuadro.LISTA));
        assertEquals("ENTROU|ana", proximo(ana, TipoQuadro.ENTROU));
        assertEquals(1, servidor.salas().total());
    }

    @Test
    void mensagemPublicaChegaParaTodosIncluindoQuemEnviou() throws Exception {
        ClienteTeste ana = entrar("ana");
        ClienteTeste bia = entrar("bia");
        drenarEntradas(ana, bia);

        ana.enviar(Protocolo.mensagem("olá a todos"));

        for (ClienteTeste cliente : List.of(ana, bia)) {
            Quadro quadro = Protocolo.parse(proximo(cliente, TipoQuadro.MSG));
            assertEquals("ana", quadro.campos().get(1));
            assertEquals("olá a todos", Protocolo.textoApos(quadro, 2));
        }
    }

    @Test
    void sairAvisaOsDemais() throws Exception {
        ClienteTeste ana = entrar("ana");
        ClienteTeste bia = entrar("bia");
        drenarEntradas(ana, bia);

        ana.enviar(Protocolo.sair());

        assertEquals("SAIU|ana", proximo(bia, TipoQuadro.SAIU));
        aguardar(() -> servidor.salas().total() == 1);
    }

    @Test
    void whisperChegaSomenteNoAlvoEQuemEnviou() throws Exception {
        ClienteTeste ana = entrar("ana");
        ClienteTeste bia = entrar("bia");
        ClienteTeste carlos = entrar("carlos");
        drenarEntradas(ana, bia, carlos);

        ana.enviar(Protocolo.privado("bia", "segredo"));

        Quadro recebido = Protocolo.parse(proximo(bia, TipoQuadro.PRIVADO));
        assertEquals("ana", recebido.campos().get(1));
        assertEquals("bia", recebido.campos().get(2));
        assertEquals("segredo", Protocolo.textoApos(recebido, 3));

        Quadro eco = Protocolo.parse(proximo(ana, TipoQuadro.PRIVADO));
        assertEquals("segredo", Protocolo.textoApos(eco, 3));

        assertNull(carlos.receber(400), "o terceiro cliente não pode ver whisper");
    }

    @Test
    void whisperParaNickInexistenteViraErro() throws Exception {
        ClienteTeste ana = entrar("ana");

        ana.enviar(Protocolo.privado("ninguem", "oi"));

        Quadro erro = Protocolo.parse(proximo(ana, TipoQuadro.ERRO));
        assertTrue(Protocolo.textoApos(erro, 0).contains("não está na sala"));
    }

    @Test
    void historicoDoPrimeiroEntraQuemChegaDepois() throws Exception {
        ClienteTeste ana = entrar("ana");
        drenarEntradas(ana);
        ana.enviar(Protocolo.mensagem("primeira mensagem"));
        assertEquals(TipoQuadro.MSG, Protocolo.parse(proximo(ana, TipoQuadro.MSG)).tipo());

        ClienteTeste bia = entrar("bia");

        Quadro historico = Protocolo.parse(proximo(bia, TipoQuadro.HIST));
        assertEquals("primeira mensagem", Protocolo.textoApos(historico, 2));
    }

    @Test
    void nomeEmUsoEhRejeitadoComErro() throws Exception {
        entrar("ana");

        ClienteTeste repetido = new ClienteTeste();
        String resposta = repetido.entrarOuReceber("ana");

        Quadro quadro = Protocolo.parse(resposta);
        assertEquals(TipoQuadro.ERRO, quadro.tipo());
        assertTrue(Protocolo.textoApos(quadro, 0).contains("já em uso"));
        repetido.close();
    }

    @Test
    void salaCheiaRejeitaNovoCliente() throws Exception {
        servidor.encerrar();
        servidor = new Servidor(0, new GerenciadorSalas(1, 20));
        servidor.iniciar();

        entrar("ana");

        ClienteTeste sobra = new ClienteTeste();
        String resposta = sobra.entrarOuReceber("bia");

        Quadro quadro = Protocolo.parse(resposta);
        assertEquals(TipoQuadro.ERRO, quadro.tipo());
        assertTrue(Protocolo.textoApos(quadro, 0).contains("sala cheia"));
        sobra.close();
    }

    @Test
    void mensagemDoOperadorDoServidorChegaATodos() throws Exception {
        ClienteTeste ana = entrar("ana");
        drenarEntradas(ana);

        servidor.publicarServidor("aviso geral");

        Quadro quadro = Protocolo.parse(proximo(ana, TipoQuadro.MSG));
        assertEquals("Servidor", quadro.campos().get(1));
        assertEquals("aviso geral", Protocolo.textoApos(quadro, 2));
    }

    @Test
    void encerrarAvisaTodosOsClientes() throws Exception {
        ClienteTeste ana = entrar("ana");
        ClienteTeste bia = entrar("bia");

        servidor.encerrar();

        Quadro fimAna = Protocolo.parse(proximo(ana, TipoQuadro.FIM));
        Quadro fimBia = Protocolo.parse(proximo(bia, TipoQuadro.FIM));
        assertTrue(Protocolo.textoApos(fimAna, 0).contains("Servidor encerrado"));
        assertTrue(Protocolo.textoApos(fimBia, 0).contains("Servidor encerrado"));
        assertEquals(0, servidor.salas().total());
    }

    @Test
    void clienteQueNuncaMandaEntraEhDesconectado() throws Exception {
        try (ServerSocket servidorTeste = new ServerSocket(0);
             Socket cliente = new Socket("localhost", servidorTeste.getLocalPort())) {
            Socket ladoServidor = servidorTeste.accept();
            ExecutorService pool = Executors.newCachedThreadPool();
            try {
                Thread atendimento = new Thread(
                        new AtendimentoCliente(ladoServidor, new GerenciadorSalas(), pool, 250));
                atendimento.start();

                cliente.setSoTimeout(2000);
                DataInputStream entrada = new DataInputStream(cliente.getInputStream());
                assertThrows(EOFException.class, () -> entrada.readUTF(),
                        "o servidor deve fechar a conexão de quem não envia ENTRAR");
                atendimento.join(2000);
            } finally {
                pool.shutdownNow();
            }
        }
    }

    // ---------------------------------------------------------------
    // Auxiliares
    // ---------------------------------------------------------------

    /** Cliente novo já conectado — entra com o nome ou recebe a recusa. */
    private ClienteTeste entrar(String nome) throws IOException {
        ClienteTeste cliente = new ClienteTeste();
        String resposta = cliente.entrarOuReceber(nome);
        Quadro quadro;
        try {
            quadro = Protocolo.parse(resposta);
        } catch (ProtocoloException e) {
            fail("resposta inválida do servidor: " + resposta);
            throw new AssertionError();
        }
        assertEquals(TipoQuadro.OK, quadro.tipo(), "esperava OK, veio: " + resposta);
        return cliente;
    }

    /**
     * Consome as notificações de entrada pendentes (LISTA/ENTROU) para que
     * cada teste comece com a fila zerada.
     */
    private void drenarEntradas(ClienteTeste... clientes) throws IOException, ProtocoloException {
        for (ClienteTeste cliente : clientes) {
            proximo(cliente, TipoQuadro.ENTROU);
        }
    }

    /** Próximo quadro do tipo esperado (pula os outros), com limite de tempo. */
    private String proximo(ClienteTeste cliente, TipoQuadro tipo) throws IOException, ProtocoloException {
        long limite = System.currentTimeMillis() + 3000;
        while (System.currentTimeMillis() < limite) {
            String bruto = cliente.receber(1000);
            if (bruto == null) {
                continue;
            }
            Quadro quadro = Protocolo.parse(bruto);
            if (quadro.tipo() == tipo) {
                return bruto;
            }
        }
        fail("não chegou quadro " + tipo + " para " + cliente);
        return null;
    }

    private void aguardar(java.util.function.BooleanSupplier condicao) throws InterruptedException {
        long limite = System.currentTimeMillis() + 3000;
        while (System.currentTimeMillis() < limite) {
            if (condicao.getAsBoolean()) {
                return;
            }
            Thread.sleep(25);
        }
        fail("condição não alcançada a tempo");
    }

    /** Cliente de teste: socket puro + protocolo, sem interface de console. */
    private final class ClienteTeste implements AutoCloseable {

        private final Socket socket;
        private final DataInputStream entrada;
        private final DataOutputStream saida;

        private ClienteTeste() throws IOException {
            socket = new Socket("localhost", servidor.portaEfetiva());
            abertos.add(socket);
            entrada = new DataInputStream(socket.getInputStream());
            saida = new DataOutputStream(socket.getOutputStream());
        }

        /** Envia ENTRAR e lê a primeira resposta (OK ou ERRO). */
        private String entrarOuReceber(String nome) throws IOException {
            enviar(Protocolo.entrar(nome));
            String bruto = receber(3000);
            if (bruto == null) {
                fail("servidor não respondeu ao ENTRAR de " + nome);
            }
            return bruto;
        }

        private void enviar(String quadro) throws IOException {
            saida.writeUTF(quadro);
            saida.flush();
        }

        /** Próximo quadro; null em caso de timeout ou conexão fechada. */
        private String receber(long timeoutMs) {
            try {
                socket.setSoTimeout((int) timeoutMs);
                return entrada.readUTF();
            } catch (SocketTimeoutException e) {
                return null;
            } catch (EOFException e) {
                return null;
            } catch (IOException e) {
                return null;
            }
        }

        @Override
        public void close() {
            try {
                socket.close();
            } catch (IOException ignored) {
                // já fechou
            }
        }
    }
}
