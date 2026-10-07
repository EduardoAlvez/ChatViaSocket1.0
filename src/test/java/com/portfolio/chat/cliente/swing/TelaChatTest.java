package com.portfolio.chat.cliente.swing;

import com.portfolio.chat.protocolo.Protocolo;
import com.portfolio.chat.protocolo.Quadro;
import com.portfolio.chat.protocolo.SenhaHash;
import com.portfolio.chat.protocolo.TipoQuadro;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.function.BooleanSupplier;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Testa a janela de verdade: um socket local no lugar do servidor e outro no
 * lugar da tela. Em ambiente sem display (CI) os testes são pulados.
 */
class TelaChatTest {

    private ServerSocket servidorLocal;
    private Socket ladoJanela;
    private Socket ladoTeste;
    private DataInputStream doTeste;
    private DataOutputStream paraTeste;
    private TelaChat janela;

    @BeforeEach
    void sobeJanela() throws Exception {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(),
                "teste de interface precisa de display");

        servidorLocal = new ServerSocket(0);
        ladoJanela = new Socket("localhost", servidorLocal.getLocalPort());
        ladoTeste = servidorLocal.accept();
        ladoTeste.setSoTimeout(3000);
        doTeste = new DataInputStream(ladoTeste.getInputStream());
        paraTeste = new DataOutputStream(ladoTeste.getOutputStream());

        DataInputStream entradaJanela = new DataInputStream(ladoJanela.getInputStream());
        DataOutputStream saidaJanela = new DataOutputStream(ladoJanela.getOutputStream());
        int porta = servidorLocal.getLocalPort();
        SwingUtilities.invokeAndWait(() ->
                janela = new TelaChat("ana", "localhost", porta, entradaJanela, saidaJanela));
    }

    @AfterEach
    void desce() throws Exception {
        if (servidorLocal != null && !servidorLocal.isClosed()) {
            servidorLocal.close();
        }
        fecha(ladoTeste);
        fecha(ladoJanela);
        if (janela != null) {
            TelaChat paraFechar = janela;
            SwingUtilities.invokeAndWait(paraFechar::dispose);
        }
    }

    // ---------------------------------------------------------------
    // Cenários
    // ---------------------------------------------------------------

    @Test
    void tituloMudaQuandoEntraNaSala() throws Exception {
        mandar("SALOK|vendas");

        esperar(() -> janela.getTitle().equals("Chat — ana · vendas"));
    }

    @Test
    void mensagemPublicaApareceNoHistorico() throws Exception {
        mandar(Protocolo.mensagemHora("18:00", "bia", "ola mundo"));

        esperar(() -> historicoOuErro().contains("[18:00] bia: ola mundo"));
    }

    @Test
    void privadaSemFocoMarcaAvisoNoTitulo() throws Exception {
        mandar("SALOK|vendas");
        esperar(() -> janela.getTitle().equals("Chat — ana · vendas"));

        mandar(Protocolo.privadoHora("18:00", "bia", "ana", "oi"));

        esperar(() -> janela.getTitle().startsWith("* "));
        assertTrue(historico().contains("(privado de bia): oi"));
    }

    @Test
    void conexaoPerdidaDesabilitaOCampo() throws Exception {
        ladoTeste.close();

        esperar(() -> {
            JTextField campo = acharEm(janela.getContentPane(), JTextField.class);
            JLabel status = acharEm(janela.getContentPane(), JLabel.class);
            return campo != null && !campo.isEnabled()
                    && status.getText().contains("conexão encerrada");
        });
    }

    @Test
    void textoDigitadoVaiParaOFio() throws Exception {
        JTextField campo = achar(JTextField.class);
        campo.setText("ola tudo");
        JButton botao = acharBotao("Enviar");
        SwingUtilities.invokeAndWait(botao::doClick);

        Quadro quadro = Protocolo.parse(daJanela());
        assertEquals(TipoQuadro.MSG, quadro.tipo());
        assertEquals(List.of("ola tudo"), quadro.campos());
        esperar(() -> campo.getText().isEmpty());
    }

    @Test
    void desafioDeSenhaEhRespondidoComAProva() throws Exception {
        String salt = SenhaHash.novoSalt();
        String nonce = SenhaHash.novoNonce();
        definirCamposPendentes("loja", "senha123");

        mandar("CHAVE|" + salt + "|" + nonce);

        Quadro quadro = Protocolo.parse(daJanela());
        assertEquals(TipoQuadro.ENTRASALAH, quadro.tipo());
        String provaEsperada = SenhaHash.resposta(SenhaHash.daSenha("senha123", salt), nonce);
        assertEquals(List.of("loja", provaEsperada), quadro.campos());
    }

    // ---------------------------------------------------------------
    // Auxiliares
    // ---------------------------------------------------------------

    private void mandar(String quadro) throws IOException {
        paraTeste.writeUTF(quadro);
        paraTeste.flush();
    }

    private String daJanela() throws IOException {
        return doTeste.readUTF();
    }

    /** Espera a condição valer no EDT, dando tempo de o quadro chegar. */
    private void esperar(BooleanSupplier condicao) throws Exception {
        long limite = System.currentTimeMillis() + 3000;
        while (System.currentTimeMillis() < limite) {
            boolean[] ok = {false};
            SwingUtilities.invokeAndWait(() -> ok[0] = condicao.getAsBoolean());
            if (ok[0]) {
                return;
            }
            Thread.sleep(30);
        }
        fail("a janela não mostrou o esperado a tempo");
    }

    private <T extends Component> T achar(Class<T> tipo) throws Exception {
        Component[] achado = new Component[1];
        SwingUtilities.invokeAndWait(() -> achado[0] = acharEm(janela.getContentPane(), tipo));
        return tipo.cast(achado[0]);
    }

    private JButton acharBotao(String texto) throws Exception {
        Component[] achado = new Component[1];
        SwingUtilities.invokeAndWait(() -> achado[0] = acharBotaoEm(janela.getContentPane(), texto));
        return (JButton) achado[0];
    }

    private static Component acharBotaoEm(Component raiz, String texto) {
        if (raiz instanceof JButton botao && texto.equals(botao.getText())) {
            return botao;
        }
        if (raiz instanceof Container container) {
            for (Component filho : container.getComponents()) {
                Component achado = acharBotaoEm(filho, texto);
                if (achado != null) {
                    return achado;
                }
            }
        }
        return null;
    }

    private String historicoOuErro() {
        try {
            return historico();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private String historico() throws Exception {
        if (SwingUtilities.isEventDispatchThread()) {
            return acharEm(janela.getContentPane(), JTextPane.class).getText();
        }
        JTextPane painel = achar(JTextPane.class);
        StringBuilder texto = new StringBuilder();
        SwingUtilities.invokeAndWait(() -> texto.append(painel.getText()));
        return texto.toString();
    }

    private void definirCamposPendentes(String sala, String senha) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try {
                definir("salaPendente", sala);
                definir("senhaPendente", senha);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        });
    }

    private void definir(String nome, Object valor) throws ReflectiveOperationException {
        Field campo = TelaChat.class.getDeclaredField(nome);
        campo.setAccessible(true);
        campo.set(janela, valor);
    }

    private static <T extends Component> T acharEm(Component raiz, Class<T> tipo) {
        if (tipo.isInstance(raiz)) {
            return tipo.cast(raiz);
        }
        if (raiz instanceof Container container) {
            for (Component filho : container.getComponents()) {
                T achado = acharEm(filho, tipo);
                if (achado != null) {
                    return achado;
                }
            }
        }
        return null;
    }

    private static void fecha(Socket socket) {
        if (socket == null) {
            return;
        }
        try {
            socket.close();
        } catch (IOException ignorada) {
            // já fechou
        }
    }
}
