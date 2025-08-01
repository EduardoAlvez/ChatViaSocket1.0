import java.io.*;

/**
 * Classe que gerencia o recebimento de mensagens do servidor.
 * Executada em uma thread separada para não bloquear a interface do usuário.
 */
public class RecebedorMensagens implements Runnable {
    // Stream de entrada do servidor
    private final DataInputStream input;

    /**
     * Construtor que recebe o stream de entrada
     * @param input Stream de entrada configurado para o servidor
     */
    public RecebedorMensagens(DataInputStream input) {
        this.input = input;
    }

    /**
     * Método principal da thread que recebe mensagens
     */
    @Override
    public void run() {
        try {
            // Loop infinito para receber mensagens
            while (true) {
                // Lê mensagem do servidor
                String mensagem = input.readUTF();

                // Exibe a mensagem para o usuário
                System.out.println(mensagem);
            }
        } catch (IOException e) {
            // Servidor encerrou a conexão
            System.out.println("Conexão com o servidor encerrada.");
        }
    }
}