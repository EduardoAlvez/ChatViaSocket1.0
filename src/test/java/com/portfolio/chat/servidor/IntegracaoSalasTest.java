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
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Ponta a ponta das salas: servidor de verdade, sockets de verdade,
 * salas isoladas com e sem senha.
 */
class IntegracaoSalasTest {

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
    void mensagensFicamIsoladasPorSala() throws Exception {
        ClienteTeste ana = entrar("ana");
        ClienteTeste bia = entrar("bia");
        drenarEntradas(ana, bia);

        bia.enviar(Protocolo.criarSala("vendas", ""));
        proximo(bia, TipoQuadro.LISTA);

        ana.enviar(Protocolo.mensagem("alguém aí?"));
        Quadro eco = Protocolo.parse(proximo(ana, TipoQuadro.MSG));
        assertEquals("ana", eco.campos().get(1));
        assertNull(bia.receber(400), "quem está em outra sala não pode ver a mensagem");

        bia.enviar(Protocolo.mensagem("aqui na vendas"));
        Quadro daVendas = Protocolo.parse(proximo(bia, TipoQuadro.MSG));
        assertEquals("aqui na vendas", Protocolo.textoApos(daVendas, 2));
        assertNull(ana.receber(400), "a geral não pode ver o que acontece na vendas");
    }

    @Test
    void senhaErradaNaoDeixaEntrarEENaoSaiDaSalaAntiga() throws Exception {
        ClienteTeste ana = entrar("ana");
        ClienteTeste bia = entrar("bia");
        drenarEntradas(ana, bia);
        bia.enviar(Protocolo.criarSala("vip", "1234"));
        proximo(bia, TipoQuadro.LISTA);

        ana.enviar(Protocolo.entrarSala("vip", "9999"));

        Quadro erro = Protocolo.parse(proximo(ana, TipoQuadro.ERRO));
        assertTrue(Protocolo.textoApos(erro, 0).contains("senha incorreta"));
        ana.enviar(Protocolo.mensagem("continuo na geral"));
        assertEquals(TipoQuadro.MSG, Protocolo.parse(proximo(ana, TipoQuadro.MSG)).tipo());
    }

    @Test
    void senhaCertaDeixaEntrar() throws Exception {
        ClienteTeste ana = entrar("ana");
        ClienteTeste bia = entrar("bia");
        drenarEntradas(ana, bia);
        bia.enviar(Protocolo.criarSala("vip", "1234"));
        proximo(bia, TipoQuadro.LISTA);

        ana.enviar(Protocolo.entrarSala("vip", "1234"));

        assertEquals("SALOK|vip", proximo(ana, TipoQuadro.SALOK));
    }

    @Test
    void trocaDeSalaAvisaAsDuasSalas() throws Exception {
        ClienteTeste ana = entrar("ana");
        ClienteTeste bia = entrar("bia");
        drenarEntradas(ana, bia);
        proximo(ana, TipoQuadro.ENTROU); // ana ainda tem pendente o aviso de entrada da bia

        ana.enviar(Protocolo.criarSala("reuniao", ""));

        assertEquals("ENTROU|ana", proximo(ana, TipoQuadro.ENTROU));
        assertEquals("SALOK|reuniao", proximo(ana, TipoQuadro.SALOK));
        assertEquals("LISTA|ana", proximo(ana, TipoQuadro.LISTA));
        assertEquals("SAIU|ana", proximo(bia, TipoQuadro.SAIU));
    }

    @Test
    void voltarParaAMesmaSalaDaErro() throws Exception {
        ClienteTeste ana = entrar("ana");
        drenarEntradas(ana);

        ana.enviar(Protocolo.entrarSala("geral", ""));

        Quadro erro = Protocolo.parse(proximo(ana, TipoQuadro.ERRO));
        assertTrue(Protocolo.textoApos(erro, 0).contains("já está nessa sala"));
    }

    @Test
    void listarSalasMostraTodas() throws Exception {
        ClienteTeste ana = entrar("ana");
        drenarEntradas(ana);
        ana.enviar(Protocolo.criarSala("apoio", ""));
        proximo(ana, TipoQuadro.LISTA);

        ana.enviar(Protocolo.pedirSalas());

        Quadro resposta = Protocolo.parse(proximo(ana, TipoQuadro.SALAS));
        assertEquals("apoio,geral", Protocolo.textoApos(resposta, 0));
    }

    @Test
    void criarSalaDuplicadaViraErro() throws Exception {
        ClienteTeste ana = entrar("ana");
        drenarEntradas(ana);
        ana.enviar(Protocolo.criarSala("vip", ""));
        proximo(ana, TipoQuadro.LISTA);

        ana.enviar(Protocolo.criarSala("VIP", ""));

        Quadro erro = Protocolo.parse(proximo(ana, TipoQuadro.ERRO));
        assertTrue(Protocolo.textoApos(erro, 0).contains("já existe"));
    }

    @Test
    void salaInexistenteViraErroComDicaDeCriacao() throws Exception {
        ClienteTeste ana = entrar("ana");
        drenarEntradas(ana);

        ana.enviar(Protocolo.entrarSala("naoexiste", ""));

        Quadro erro = Protocolo.parse(proximo(ana, TipoQuadro.ERRO));
        assertTrue(Protocolo.textoApos(erro, 0).contains("/criar"));
    }

    // ---------------------------------------------------------------
    // Auxiliares
    // ---------------------------------------------------------------

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

    /** Consome as notificações de entrada (SALOK/LISTA/ENTROU). */
    private void drenarEntradas(ClienteTeste... clientes) throws IOException, ProtocoloException {
        for (ClienteTeste cliente : clientes) {
            proximo(cliente, TipoQuadro.ENTROU);
        }
    }

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
