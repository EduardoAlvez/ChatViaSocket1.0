import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Classe principal do servidor de chat que gerencia conexões de múltiplos clientes.
 * Utiliza um pool de threads para lidar com cada cliente conectado.
 */
public class ServidorChat {
    // Porta padrão onde o servidor irá escutar
    private static final int PORTA = 6666;

    // Lista thread-safe para armazenar os streams de saída de todos os clientes conectados
    static final Set<DataOutputStream> clientes = ConcurrentHashMap.newKeySet();

    /**
     * Método principal que inicia o servidor
     * @param args Argumentos de linha de comando (não utilizados)
     */
    public static void main(String[] args) {
        try {
            // Cria um pool de threads para gerenciar os clientes
            ExecutorService threadPool = Executors.newFixedThreadPool(10);

            // Cria o socket do servidor
            ServerSocket serverSocket = new ServerSocket(PORTA);

            // Inicia a thread que envia mensagens do servidor para os clientes
            new Thread(new ServidorEnviador()).start();

            System.out.println("Servidor iniciado na porta " + PORTA);

            // Loop principal que aceita novas conexões
            while (true) {
                // Aguarda uma nova conexão de cliente
                Socket clientSocket = serverSocket.accept();

                // Cria stream de saída para o novo cliente
                DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream());

                // Adiciona o cliente à lista de clientes conectados
                clientes.add(out);

                // Cria um handler para gerenciar a comunicação com este cliente
                ClientHandler handler = new ClientHandler(clientSocket, out);

                // Submete o handler ao pool de threads
                threadPool.submit(handler);

                // Log da nova conexão
                System.out.println("Novo cliente conectado: " +
                        clientSocket.getInetAddress() + ":" + clientSocket.getPort());
            }
        } catch (IOException e) {
            System.err.println("Erro no servidor: " + e.getMessage());
        }
    }

    /**
     * Envia uma mensagem para todos os clientes conectados
     * @param mensagem A mensagem a ser enviada
     */
    static void broadcast(String mensagem) {
        // Itera sobre todos os clientes conectados
        for (DataOutputStream out : clientes) {
            try {
                // Envia a mensagem para o cliente
                out.writeUTF(mensagem);
                out.flush();
            } catch (IOException e) {
                // Se houver erro, remove o cliente da lista
                System.err.println("Erro ao enviar mensagem: " + e.getMessage());
                clientes.remove(out);
            }
        }
    }
}