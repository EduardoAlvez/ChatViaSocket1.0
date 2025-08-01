import java.io.*;
import java.net.*;

/**
 * Classe que gerencia a comunicação com um cliente específico.
 * Executada em uma thread separada para cada cliente conectado.
 */
public class ClientHandler extends Thread {
    // Stream de entrada para receber mensagens do cliente
    private final DataInputStream in;

    // Stream de saída para enviar mensagens ao cliente
    private final DataOutputStream out;

    // Socket de conexão com o cliente
    private final Socket clientSocket;

    /**
     * Construtor do handler do cliente
     * @param socket Socket conectado ao cliente
     * @param out Stream de saída para o cliente
     * @throws IOException Se ocorrer erro ao criar o stream de entrada
     */
    public ClientHandler(Socket socket, DataOutputStream out) throws IOException {
        this.clientSocket = socket;
        this.out = out;
        this.in = new DataInputStream(clientSocket.getInputStream());
    }

    /**
     * Método principal da thread que gerencia o cliente
     */
    @Override
    public void run() {
        try {
            // Loop infinito para receber mensagens do cliente
            while (true) {
                // Lê uma mensagem do cliente
                String mensagem = in.readUTF();

                // Log da mensagem recebida no servidor
                System.out.println("Mensagem recebida: " + mensagem);

                // Retransmite a mensagem para todos os clientes
                ServidorChat.broadcast(mensagem);
            }
        } catch (EOFException e) {
            // Cliente desconectou normalmente
            System.out.println("Cliente desconectado: " + clientSocket.getInetAddress());
        } catch (IOException e) {
            // Erro na comunicação com o cliente
            System.err.println("Erro na conexão: " + e.getMessage());
        } finally {
            try {
                // Remove o cliente da lista de clientes conectados
                ServidorChat.clientes.remove(out);

                // Fecha o socket do cliente
                clientSocket.close();

                // Log do número de conexões ativas
                System.out.println("Conexões ativas: " + ServidorChat.clientes.size());
            } catch (IOException e) {
                System.err.println("Erro ao fechar socket: " + e.getMessage());
            }
        }
    }
}