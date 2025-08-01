import java.util.Scanner;

/**
 * Classe que gerencia o envio de mensagens do próprio servidor para os clientes.
 * Executada em uma thread separada.
 */
public class ServidorEnviador implements Runnable {
    /**
     * Metodo principal da thread que envia mensagens do servidor
     */
    @Override
    public void run() {
        // Scanner para ler entrada do console do servidor
        try (Scanner scanner = new Scanner(System.in)) {
            System.out.println("Digite mensagens do servidor (ou 'sair' para encerrar):");

            // Loop para enviar mensagens
            while (true) {
                // Lê a mensagem do console
                String mensagem = scanner.nextLine();

                // Verifica se é comando para sair
                if (mensagem.equalsIgnoreCase("sair")) {
                    System.exit(0);
                }

                // Envia a mensagem para todos os clientes
                ServidorChat.broadcast("[Servidor] " + mensagem);
            }
        }
    }
}