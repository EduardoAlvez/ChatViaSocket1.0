package com.portfolio.chat.servidor;

import com.portfolio.chat.protocolo.Protocolo;
import com.portfolio.chat.protocolo.SenhaHash;
import com.portfolio.chat.protocolo.ProtocoloException;
import com.portfolio.chat.protocolo.Quadro;
import com.portfolio.chat.protocolo.TipoQuadro;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Sobe, derruba e sobe de novo o servidor apontando para a mesma pasta —
 * histórico e salas com senha precisam sobreviver ao reinício.
 */
class IntegracaoPersistenciaTest {

    @TempDir
    java.nio.file.Path tempDir;

    private Servidor servidor;
    private final List<Socket> abertos = new ArrayList<>();

    @AfterEach
    void desceServidor() {
        if (servidor != null) {
            servidor.encerrar();
        }
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
    void historicoSobreviveAoReiniciar() throws Exception {
        sobeServidor();
        ClienteTeste ana = entrar("ana");
        drenarEntradas(ana);
        ana.enviar(Protocolo.mensagem("primeira mensagem"));
        assertEquals(TipoQuadro.MSG, Protocolo.parse(proximo(ana, TipoQuadro.MSG)).tipo());
        desceServidorAgora();

        sobeServidor();
        ClienteTeste bia = entrar("bia");

        Quadro historico = Protocolo.parse(proximo(bia, TipoQuadro.HIST));
        assertEquals("primeira mensagem", Protocolo.textoApos(historico, 2));
    }

    @Test
    void salaComSenhaRecriadaDoDisco() throws Exception {
        sobeServidor();
        ClienteTeste ana = entrar("ana");
        drenarEntradas(ana);
        ana.enviar(Protocolo.criarSala("vip", "1234"));
        proximo(ana, TipoQuadro.LISTA);
        desceServidorAgora();

        sobeServidor();
        ClienteTeste bia = entrar("bia");
        drenarEntradas(bia);

        bia.enviar(Protocolo.entrarSala("vip"));
        responderDesafio(bia, "vip", "9999");
        Quadro erro = Protocolo.parse(proximo(bia, TipoQuadro.ERRO));
        assertTrue(Protocolo.textoApos(erro, 0).contains("senha incorreta"));

        bia.enviar(Protocolo.entrarSala("vip"));
        responderDesafio(bia, "vip", "1234");
        assertEquals("SALOK|vip", proximo(bia, TipoQuadro.SALOK));
    }

    @Test
    void salaAbertaRecriadaDoDiscoTambem() throws Exception {
        sobeServidor();
        ClienteTeste ana = entrar("ana");
        drenarEntradas(ana);
        ana.enviar(Protocolo.criarSala("apoio", ""));
        proximo(ana, TipoQuadro.LISTA);
        desceServidorAgora();

        sobeServidor();
        ClienteTeste bia = entrar("bia");
        drenarEntradas(bia);

        bia.enviar(Protocolo.entrarSala("apoio"));
        assertEquals("SALOK|apoio", proximo(bia, TipoQuadro.SALOK));
    }

    // ---------------------------------------------------------------
    // Auxiliares
    // ---------------------------------------------------------------

    private void sobeServidor() throws IOException {
        servidor = new Servidor(0,
                new GerenciadorSalas(new HistoricoArquivo(tempDir)));
        servidor.iniciar();
    }

    private void desceServidorAgora() {
        servidor.encerrar();
        for (Socket socket : abertos) {
            try {
                socket.close();
            } catch (IOException ignored) {
                // já fechou
            }
        }
        abertos.clear();
        servidor = null;
    }

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

    private void drenarEntradas(ClienteTeste... clientes) throws IOException, ProtocoloException {
        for (ClienteTeste cliente : clientes) {
            proximo(cliente, TipoQuadro.ENTROU);
        }
    }

    /** Responde ao CHAVE do servidor com a prova da senha. */
    private void responderDesafio(ClienteTeste cliente, String sala, String senha)
            throws IOException, ProtocoloException {
        Quadro chave = Protocolo.parse(proximo(cliente, TipoQuadro.CHAVE));
        String hash = SenhaHash.daSenha(senha, chave.campos().get(0));
        cliente.enviar(Protocolo.entrarSalaComResposta(sala,
                SenhaHash.resposta(hash, chave.campos().get(1))));
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
