package com.portfolio.chat.cliente.swing;

import com.formdev.flatlaf.FlatLightLaf;
import com.portfolio.chat.protocolo.Protocolo;
import com.portfolio.chat.protocolo.ProtocoloException;
import com.portfolio.chat.protocolo.Quadro;
import com.portfolio.chat.protocolo.TipoQuadro;

import javax.swing.JOptionPane;
import javax.swing.JTextField;
import javax.swing.UIManager;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketTimeoutException;

/**
 * Ponto de entrada do cliente gráfico (FlatLaf).
 * Uso: {@code java -cp chat-via-socket-1.0.0.jar com.portfolio.chat.cliente.ClienteSwing [ip] [porta]}
 */
public final class ClienteSwing {

    private ClienteSwing() {
    }

    public static void main(String[] args) {
        instalarTema();
        String ipPadrao = args.length > 0 ? args[0] : "localhost";
        int portaPadrao = Protocolo.PORTA_PADRAO;
        if (args.length > 1) {
            try {
                portaPadrao = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                System.err.println("Porta inválida: " + args[1]);
                return;
            }
        }
        conectar(ipPadrao, portaPadrao);
    }

    private static void instalarTema() {
        try {
            UIManager.setLookAndFeel(new FlatLightLaf());
        } catch (Throwable ignorada) {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception semTema) {
                // tema padrão do Java
            }
        }
    }

    /** Pergunta nome/servidor, conecta e abre a janela (repete até dar certo ou cancelar). */
    private static void conectar(String ipPadrao, int portaPadrao) {
        JTextField campoNome = new JTextField(16);
        JTextField campoIp = new JTextField(ipPadrao, 16);
        JTextField campoPorta = new JTextField(String.valueOf(portaPadrao), 6);

        while (true) {
            Object[] campos = {
                    "Nome:", campoNome,
                    "Servidor (IP):", campoIp,
                    "Porta:", campoPorta
            };
            int opcao = JOptionPane.showConfirmDialog(null, campos, "Entrar no chat",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (opcao != JOptionPane.OK_OPTION) {
                System.exit(0);
            }

            String nome;
            int porta;
            try {
                nome = Protocolo.validarNome(campoNome.getText());
                porta = Integer.parseInt(campoPorta.getText().trim());
            } catch (IllegalArgumentException e) {
                JOptionPane.showMessageDialog(null, e.getMessage(), "Dados inválidos",
                        JOptionPane.WARNING_MESSAGE);
                continue;
            }

            String ip = campoIp.getText().trim();
            try {
                Socket socket = new Socket(ip, porta);
                DataInputStream entrada = new DataInputStream(socket.getInputStream());
                DataOutputStream saida = new DataOutputStream(socket.getOutputStream());
                saida.writeUTF(Protocolo.entrar(nome));
                String meuNome = aguardarEntrada(entrada, socket, nome);
                if (meuNome == null) {
                    continue;
                }
                javax.swing.SwingUtilities.invokeLater(() -> {
                    TelaChat tela = new TelaChat(meuNome, ip, porta, entrada, saida);
                    tela.setVisible(true);
                });
                return;
            } catch (IOException e) {
                JOptionPane.showMessageDialog(null,
                        "Não foi possível conectar em " + ip + ":" + porta + "\n" + e.getMessage(),
                        "Falha na conexão", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    /**
     * Lê a primeira resposta do servidor (OK ou ERRO) antes de subir a janela.
     *
     * @return o nome aceito ou null se recusado (diálogo já mostrado)
     */
    private static String aguardarEntrada(DataInputStream entrada, Socket socket, String nome) {
        String bruto;
        try {
            socket.setSoTimeout(5000);
            bruto = entrada.readUTF();
            socket.setSoTimeout(0);
        } catch (SocketTimeoutException e) {
            JOptionPane.showMessageDialog(null, "O servidor não respondeu a tempo.",
                    "Sem resposta", JOptionPane.ERROR_MESSAGE);
            return null;
        } catch (IOException e) {
            JOptionPane.showMessageDialog(null, "Conexão perdida: " + e.getMessage(),
                    "Falha na conexão", JOptionPane.ERROR_MESSAGE);
            return null;
        }

        Quadro quadro;
        try {
            quadro = Protocolo.parse(bruto);
        } catch (ProtocoloException e) {
            JOptionPane.showMessageDialog(null, "Resposta inválida do servidor.",
                    "Falha na conexão", JOptionPane.ERROR_MESSAGE);
            return null;
        }
        if (quadro.tipo() == TipoQuadro.ERRO) {
            JOptionPane.showMessageDialog(null, Protocolo.textoApos(quadro, 0),
                    "Entrada recusada", JOptionPane.WARNING_MESSAGE);
            return null;
        }
        if (quadro.tipo() != TipoQuadro.OK || quadro.campos().isEmpty()) {
            JOptionPane.showMessageDialog(null, "Resposta inesperada do servidor.",
                    "Falha na conexão", JOptionPane.ERROR_MESSAGE);
            return null;
        }
        return quadro.campos().get(0);
    }
}
