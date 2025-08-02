import java.io.*;
import java.net.*;
import java.util.Scanner;

/**
 * Classe principal do cliente de chat.
 * Gerencia a conexão com o servidor e a interface com o usuário.
 */
public class ClienteChat {
    private Socket socket;          // Socket de conexão com o servidor
    private DataInputStream input;  // Stream de entrada do servidor
    private DataOutputStream output; // Stream de saída para o servidor

    /**
     * Metodo principal que inicia o cliente
     * @param args Argumentos de linha de comando (não utilizados)
     */
    public static void main(String[] args) {
        // Inicia o cliente conectando ao localhost na porta 6666
        new ClienteChat().iniciar("localhost", 6666);
    }

    /**
     * Inicia o cliente e gerencia o ciclo de vida da conexão
     * @param serverIp IP do servidor
     * @param serverPort Porta do servidor
     */
    public void iniciar(String serverIp, int serverPort) {
        try {
            // Estabelece conexão com o servidor
            conectarServidor(serverIp, serverPort);

            // Inicia thread para receber mensagens do servidor
            new Thread(new RecebedorMensagens(input)).start();

            // Gerencia o envio de mensagens do usuário
            enviarMensagens();
        } catch (IOException e) {
            System.err.println("Erro no cliente: " + e.getMessage());
        } finally {
            // Garante que a conexão será fechada
            desconectar();
        }
    }

    /**
     * Estabelece conexão com o servidor
     * @param ip IP do servidor
     * @param porta Porta do servidor
     * @throws IOException Se ocorrer erro na conexão
     */
    private void conectarServidor(String ip, int porta) throws IOException {
        System.out.println("Conectando ao servidor...");
        socket = new Socket(ip, porta);
        input = new DataInputStream(socket.getInputStream());
        output = new DataOutputStream(socket.getOutputStream());
        System.out.println("Conectado!");
    }

    /**
     * Gerencia o envio de mensagens digitadas pelo usuário
     * @throws IOException Se ocorrer erro no envio
     */
    private void enviarMensagens() throws IOException {
        Scanner scanner = new Scanner(System.in);

        // Solicita nome do usuário
        System.out.print("Digite seu nome: ");
        String nome = scanner.nextLine();

        System.out.println("Digite suas mensagens (ou 'sair' para encerrar):");

        // Loop principal para envio de mensagens
        while (true) {
            String msg = scanner.nextLine();

            // Verifica comando para sair
            if (msg.equalsIgnoreCase("sair")) break;

            // Envia mensagem para o servidor
            output.writeUTF(nome + ": " + msg);
        }
        scanner.close();
    }

    /**
     * Fecha a conexão com o servidor
     */
    private void desconectar() {
        try {
            if (input != null) input.close();
            if (output != null) output.close();
            if (socket != null) socket.close();
        } catch (IOException e) {
            System.err.println("Erro ao desconectar: " + e.getMessage());
        }
    }
}