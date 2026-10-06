package com.portfolio.chat.cliente;

import com.portfolio.chat.protocolo.Protocolo;
import com.portfolio.chat.protocolo.TipoQuadro;
import com.portfolio.chat.protocolo.Quadro;
import com.portfolio.chat.protocolo.ProtocoloException;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Scanner;

/**
 * Ponto de entrada do cliente (interface de linha de comando).
 * Uso: {@code java -cp chat-via-socket-1.0.0.jar com.portfolio.chat.cliente.ClienteChat [ip] [porta]}
 */
public final class ClienteChat {

    private Socket socket;
    private DataInputStream entrada;
    private DataOutputStream saida;
    private volatile boolean finalizado;

    public static void main(String[] args) {
        String ip = "localhost";
        int porta = Protocolo.PORTA_PADRAO;
        if (args.length > 0) {
            ip = args[0];
        }
        if (args.length > 1) {
            try {
                porta = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                System.err.println("Porta inválida: " + args[1]);
                System.err.println("Uso: ClienteChat [ip] [porta]");
                return;
            }
        }
        new ClienteChat().iniciar(ip, porta);
    }

    public void iniciar(String ip, int porta) {
        Scanner scanner = new Scanner(System.in);
        String nome = pedirNome(scanner);
        if (nome == null) {
            return;
        }

        if (!conectar(ip, porta, nome)) {
            return;
        }
        String meuNome = aguardarEntrada();
        if (meuNome == null) {
            return;
        }

        System.out.println("Conectado como " + meuNome + ". Digite /ajuda para ver os comandos.");
        new Thread(
                new RecebedorMensagens(entrada, new Renderizador(meuNome), () -> finalizado = true),
                "recebedor-mensagens"
        ).start();

        enviarMensagens(scanner);
        finalizado = true;
        desconectar();
        System.out.println("Desconectado. Até logo!");
    }

    private String pedirNome(Scanner scanner) {
        while (true) {
            System.out.print("Digite seu nome: ");
            if (!scanner.hasNextLine()) {
                return null;
            }
            try {
                return Protocolo.validarNome(scanner.nextLine());
            } catch (IllegalArgumentException e) {
                System.out.println("[!] " + e.getMessage());
            }
        }
    }

    private boolean conectar(String ip, int porta, String nome) {
        try {
            socket = new Socket(ip, porta);
            entrada = new DataInputStream(socket.getInputStream());
            saida = new DataOutputStream(socket.getOutputStream());
            saida.writeUTF(Protocolo.entrar(nome));
            return true;
        } catch (IOException e) {
            System.err.println("Não foi possível conectar em " + ip + ":" + porta + " — " + e.getMessage());
            return false;
        }
    }

    /**
     * Lê a primeira resposta do servidor (OK ou ERRO) antes de subir o
     * receptor — os quadros seguintes ficam no buffer e são lidos por ele.
     *
     * @return o nome aceito ou null se recusado
     */
    private String aguardarEntrada() {
        String primeiro;
        try {
            socket.setSoTimeout(5000);
            primeiro = entrada.readUTF();
            socket.setSoTimeout(0);
        } catch (SocketTimeoutException e) {
            System.err.println("O servidor não respondeu a tempo.");
            desconectar();
            return null;
        } catch (IOException e) {
            System.err.println("Conexão perdida: " + e.getMessage());
            desconectar();
            return null;
        }

        Quadro quadro;
        try {
            quadro = Protocolo.parse(primeiro);
        } catch (ProtocoloException e) {
            System.err.println("Resposta inválida do servidor.");
            desconectar();
            return null;
        }
        if (quadro.tipo() == TipoQuadro.ERRO) {
            System.out.println("[!] " + Protocolo.textoApos(quadro, 0));
            System.out.println("Escolha outro nome e tente de novo.");
            desconectar();
            return null;
        }
        if (quadro.tipo() != TipoQuadro.OK || quadro.campos().isEmpty()) {
            System.err.println("Resposta inesperada do servidor.");
            desconectar();
            return null;
        }
        return quadro.campos().get(0);
    }

    private void enviarMensagens(Scanner scanner) {
        System.out.println("Comandos: /lista  /salas  /entrar <sala> [senha]  /criar <sala> [senha]"
                + "  /w <nick> <mensagem>  /ajuda  /sair");
        while (!finalizado) {
            if (!scanner.hasNextLine()) {
                break;
            }
            String linha = scanner.nextLine();
            if (linha.isBlank()) {
                continue;
            }
            try {
                if (processarLinha(linha)) {
                    break;
                }
            } catch (IllegalArgumentException e) {
                System.out.println("[!] " + e.getMessage());
            } catch (IOException e) {
                System.out.println("[!] Conexão com o servidor perdida.");
                finalizado = true;
            }
        }
    }

    /**
     * Converte a linha digitada em um quadro do protocolo.
     *
     * @return true para encerrar o loop (comando /sair)
     */
    private boolean processarLinha(String linha) throws IOException {
        String baixo = linha.toLowerCase();
        if (baixo.equals("/sair")) {
            saida.writeUTF(Protocolo.sair());
            return true;
        }
        if (baixo.equals("/lista")) {
            saida.writeUTF(Protocolo.lista());
            return false;
        }
        if (baixo.equals("/salas")) {
            saida.writeUTF(Protocolo.pedirSalas());
            return false;
        }
        if (baixo.equals("/ajuda")) {
            System.out.println("""
                    /lista                 mostra quem está na sala
                    /salas                 lista as salas do servidor
                    /entrar <sala> [senha] entra ou troca de sala
                    /criar <sala> [senha]  cria uma sala (sem senha = aberta)
                    /w <nick> <mensagem>   mensagem privada
                    /ajuda                 esta ajuda
                    /sair                  sai da sala e desconecta""");
            return false;
        }
        if (baixo.startsWith("/entrar ")) {
            String[] partes = linha.trim().split("\\s+", 3);
            if (partes.length == 2) {
                saida.writeUTF(Protocolo.entrarSala(partes[1], ""));
            } else if (partes.length == 3) {
                saida.writeUTF(Protocolo.entrarSala(partes[1], partes[2]));
            } else {
                System.out.println("[!] uso: /entrar <sala> [senha]");
            }
            return false;
        }
        if (baixo.startsWith("/criar ")) {
            String[] partes = linha.trim().split("\\s+", 3);
            if (partes.length == 2) {
                saida.writeUTF(Protocolo.criarSala(partes[1], ""));
            } else if (partes.length == 3) {
                saida.writeUTF(Protocolo.criarSala(partes[1], partes[2]));
            } else {
                System.out.println("[!] uso: /criar <sala> [senha]");
            }
            return false;
        }
        if (baixo.startsWith("/w ")) {
            String[] partes = linha.trim().split("\\s+", 3);
            if (partes.length < 3) {
                System.out.println("[!] uso: /w <nick> <mensagem>");
            } else {
                saida.writeUTF(Protocolo.privado(partes[1], partes[2]));
            }
            return false;
        }
        if (linha.startsWith("/")) {
            System.out.println("[!] comando desconhecido — digite /ajuda");
            return false;
        }
        saida.writeUTF(Protocolo.mensagem(linha));
        return false;
    }

    private void desconectar() {
        finalizado = true;
        try {
            if (entrada != null) {
                entrada.close();
            }
        } catch (IOException ignored) {
            // já fechou
        }
        try {
            if (saida != null) {
                saida.close();
            }
        } catch (IOException ignored) {
            // já fechou
        }
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException ignored) {
            // já fechou
        }
    }
}
