package com.portfolio.chat.cliente.swing;

import com.portfolio.chat.cliente.RecebedorMensagens;
import com.portfolio.chat.protocolo.Protocolo;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Janela principal do chat: histórico estilizado, participantes à direita,
 * campo de envio embaixo e botões de sala no topo.
 * A lógica de interpretação fica em {@link InterpretadorTela} (testada);
 * esta classe só pinta e manda quadros.
 */
public final class TelaChat extends JFrame {

    private static final Color COR_PUBLICA = new Color(30, 30, 30);
    private static final Color COR_HISTORICO = new Color(120, 120, 120);
    private static final Color COR_PRIVADA_RECEBIDA = new Color(120, 50, 160);
    private static final Color COR_PRIVADA_ENVIADA = new Color(20, 90, 170);
    private static final Color COR_SISTEMA = new Color(0, 110, 60);

    private final String eu;
    private final DataInputStream entrada;
    private final DataOutputStream saida;
    private final InterpretadorTela interpretador;
    private final Reconector reconector;

    private final JTextPane historico = new JTextPane();
    private final DefaultListModel<String> modeloParticipantes = new DefaultListModel<>();
    private final JTextField campo = new JTextField();
    private final JLabel status = new JLabel();

    private String tituloBase;
    private boolean avisoPendente;
    private boolean conexaoCaiu;

    public TelaChat(String eu, String ip, int porta,
                    DataInputStream entrada, DataOutputStream saida) {
        this(eu, ip, porta, entrada, saida, null);
    }

    /** Com reconector: a janela pergunta se quer reconectar quando a conexão cair. */
    public TelaChat(String eu, String ip, int porta,
                    DataInputStream entrada, DataOutputStream saida, Reconector reconector) {
        this.eu = eu;
        this.entrada = entrada;
        this.saida = saida;
        this.interpretador = new InterpretadorTela(eu);
        this.reconector = reconector;

        tituloBase = "Chat — " + eu;
        setTitle(tituloBase);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(780, 520);
        setLocationRelativeTo(null);
        montar(ip, porta);
    }

    private void montar(String ip, int porta) {
        historico.setEditable(false);
        JScrollPane rolagemHistorico = new JScrollPane(historico);
        rolagemHistorico.setBorder(BorderFactory.createTitledBorder("Conversa"));

        JList<String> lista = new JList<>(modeloParticipantes);
        lista.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane rolagemParticipantes = new JScrollPane(lista);
        rolagemParticipantes.setPreferredSize(new java.awt.Dimension(160, 0));
        rolagemParticipantes.setBorder(BorderFactory.createTitledBorder("Participantes"));

        JSplitPane divisao = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, rolagemHistorico, rolagemParticipantes);
        divisao.setResizeWeight(1.0);

        JButton botaoSalas = new JButton("Salas");
        botaoSalas.addActionListener(e -> pedirSalas());
        JButton botaoEntrar = new JButton("Entrar");
        botaoEntrar.addActionListener(e -> trocarSala(false));
        JButton botaoCriar = new JButton("Criar");
        botaoCriar.addActionListener(e -> trocarSala(true));
        JButton botaoSair = new JButton("Sair");
        botaoSair.addActionListener(e -> sair());

        JPanel topo = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 6, 4));
        topo.add(botaoSalas);
        topo.add(botaoEntrar);
        topo.add(botaoCriar);
        topo.add(botaoSair);

        JButton enviar = new JButton("Enviar");
        enviar.addActionListener(e -> enviarMensagem());
        campo.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    enviarMensagem();
                }
            }
        });

        JPanel embaixo = new JPanel(new BorderLayout(6, 0));
        embaixo.add(campo, BorderLayout.CENTER);
        embaixo.add(enviar, BorderLayout.EAST);

        status.setText("conectado como " + eu + " em " + ip + ":" + porta);
        status.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

        JPanel raiz = new JPanel(new BorderLayout(0, 4));
        raiz.add(topo, BorderLayout.NORTH);
        raiz.add(divisao, BorderLayout.CENTER);
        JPanel sul = new JPanel(new BorderLayout());
        sul.add(status, BorderLayout.SOUTH);
        sul.add(embaixo, BorderLayout.CENTER);
        raiz.add(sul, BorderLayout.SOUTH);
        setContentPane(raiz);

        addWindowFocusListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowGainedFocus(java.awt.event.WindowEvent e) {
                if (avisoPendente) {
                    avisoPendente = false;
                    setTitle(tituloBase);
                }
            }
        });
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                conexaoCaiu = true;
                fecharTudo();
            }
        });

        new Thread(
                new RecebedorMensagens(entrada, quadro -> {
                    InterpretadorTela.EventoTela evento = interpretador.interpretar(quadro);
                    if (evento != null) {
                        SwingUtilities.invokeLater(() -> tratar(evento));
                    }
                }, () -> SwingUtilities.invokeLater(this::conexaoEncerrada)),
                "recebedor-ui"
        ).start();
    }

    private void tratar(InterpretadorTela.EventoTela evento) {
        if (evento instanceof InterpretadorTela.EventoTela.Linha linha) {
            if (linha.tipo() == InterpretadorTela.TipoTexto.PRIVADA_RECEBIDA && !isActive()) {
                avisoPendente = true;
                setTitle("* " + tituloBase);
                java.awt.Toolkit.getDefaultToolkit().beep();
            }
            anexar(linha);
        } else if (evento instanceof InterpretadorTela.EventoTela.Participantes participantes) {
            modeloParticipantes.clear();
            for (String nick : participantes.nicks()) {
                modeloParticipantes.addElement(nick);
            }
        } else if (evento instanceof InterpretadorTela.EventoTela.SalaAtual sala) {
            tituloBase = "Chat — " + eu + " · " + sala.nome();
            avisoPendente = false;
            setTitle(tituloBase);
            status.setText("sala " + sala.nome() + " — conectado como " + eu);
        } else if (evento instanceof InterpretadorTela.EventoTela.SalasDisponiveis salas) {
            JOptionPane.showMessageDialog(this,
                    String.join("\n", salas.nomes()), "Salas disponíveis",
                    JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void anexar(InterpretadorTela.EventoTela.Linha linha) {
        StyledDocument doc = historico.getStyledDocument();
        SimpleAttributeSet atributos = new SimpleAttributeSet();
        StyleConstants.setForeground(atributos, corDe(linha.tipo()));
        StyleConstants.setItalic(atributos, linha.tipo() == InterpretadorTela.TipoTexto.HISTORICO);
        String texto = formatar(linha) + "\n";
        try {
            doc.insertString(doc.getLength(), texto, atributos);
        } catch (javax.swing.text.BadLocationException e) {
            // nunca deve acontecer com o documento da própria janela
        }
        historico.setCaretPosition(doc.getLength());
    }

    private static String formatar(InterpretadorTela.EventoTela.Linha linha) {
        return switch (linha.tipo()) {
            case PUBLICA, HISTORICO -> "[" + linha.hora() + "] " + linha.autor() + ": " + linha.texto();
            case PRIVADA_RECEBIDA -> "[" + linha.hora() + "] (privado de " + linha.autor() + "): " + linha.texto();
            case PRIVADA_ENVIADA -> "[" + linha.hora() + "] (privado para " + linha.autor() + "): " + linha.texto();
            case SISTEMA -> linha.texto();
        };
    }

    private static Color corDe(InterpretadorTela.TipoTexto tipo) {
        return switch (tipo) {
            case PUBLICA -> COR_PUBLICA;
            case HISTORICO -> COR_HISTORICO;
            case PRIVADA_RECEBIDA -> COR_PRIVADA_RECEBIDA;
            case PRIVADA_ENVIADA -> COR_PRIVADA_ENVIADA;
            case SISTEMA -> COR_SISTEMA;
        };
    }

    private void enviarMensagem() {
        String texto = campo.getText();
        if (texto.isBlank()) {
            return;
        }
        try {
            saida.writeUTF(InterpretadorTela.quadroDaLinha(texto));
            saida.flush();
            campo.setText("");
        } catch (IllegalArgumentException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Mensagem inválida",
                    JOptionPane.WARNING_MESSAGE);
        } catch (IOException e) {
            conexaoEncerrada();
        }
    }

    private void pedirSalas() {
        try {
            saida.writeUTF(Protocolo.pedirSalas());
            saida.flush();
        } catch (IOException e) {
            conexaoEncerrada();
        }
    }

    /** Pergunta sala (e senha) para entrar ou criar. */
    private void trocarSala(boolean criar) {
        JTextField campoSala = new JTextField(14);
        JPasswordField campoSenha = new JPasswordField(14);
        Object[] campos = {"Sala:", campoSala, "Senha (vazia = aberta):", campoSenha};
        int opcao = JOptionPane.showConfirmDialog(this, campos,
                criar ? "Criar sala" : "Entrar na sala",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (opcao != JOptionPane.OK_OPTION) {
            return;
        }
        try {
            String quadro = criar
                    ? Protocolo.criarSala(campoSala.getText(), new String(campoSenha.getPassword()))
                    : Protocolo.entrarSala(campoSala.getText(), new String(campoSenha.getPassword()));
            saida.writeUTF(quadro);
            saida.flush();
        } catch (IllegalArgumentException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Dados inválidos",
                    JOptionPane.WARNING_MESSAGE);
        } catch (IOException e) {
            conexaoEncerrada();
        }
    }

    private void sair() {
        conexaoCaiu = true;
        try {
            saida.writeUTF(Protocolo.sair());
            saida.flush();
        } catch (IOException ignorada) {
            // servidor já foi embora
        }
        dispose();
        fecharTudo();
    }

    private void conexaoEncerrada() {
        if (conexaoCaiu) {
            return;
        }
        conexaoCaiu = true;
        status.setText("[!] conexão encerrada");
        campo.setEnabled(false);
        if (reconector == null) {
            return;
        }
        int opcao = JOptionPane.showConfirmDialog(this,
                "Conexão com o servidor encerrada.\nTentar reconectar?",
                "Conexão perdida", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (opcao == JOptionPane.YES_OPTION) {
            reconector.reconectar(this);
        }
    }

    private void fecharTudo() {
        try {
            entrada.close();
        } catch (IOException ignorada) {
            // já fechou
        }
        try {
            saida.close();
        } catch (IOException ignorada) {
            // já fechou
        }
    }
}
