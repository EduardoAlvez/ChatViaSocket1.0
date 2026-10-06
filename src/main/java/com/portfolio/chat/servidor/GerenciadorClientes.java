package com.portfolio.chat.servidor;

import com.portfolio.chat.Log;
import com.portfolio.chat.protocolo.Protocolo;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Sala de chat: registro de clientes, broadcast, mensagens privadas e histórico.
 *
 * <p>Todos os métodos são sincronizados no próprio gerenciador, o que dá
 * ordem global consistente: a mesma sequência de quadros chega para todos.</p>
 */
public final class GerenciadorClientes {

    private record Registro(String hora, String nome, String texto) {
    }

    private final Map<String, ClienteConectado> porNick = new HashMap<>();
    private final Deque<Registro> historico = new ArrayDeque<>();
    private final int maxClientes;
    private final int maxHistorico;

    public GerenciadorClientes() {
        this(Protocolo.MAX_CLIENTES, Protocolo.MAX_HISTORICO);
    }

    /** Permite limites menores nos testes. */
    public GerenciadorClientes(int maxClientes, int maxHistorico) {
        this.maxClientes = maxClientes;
        this.maxHistorico = maxHistorico;
    }

    /**
     * Registra um cliente na sala.
     *
     * @throws IllegalArgumentException se o nome estiver em uso ou a sala estiver cheia
     */
    public synchronized void registrar(ClienteConectado cliente) {
        String chave = chave(cliente.nome());
        if (porNick.size() >= maxClientes) {
            throw new IllegalArgumentException("sala cheia (máximo " + maxClientes + " clientes)");
        }
        if (porNick.containsKey(chave)) {
            throw new IllegalArgumentException("nome já em uso — escolha outro");
        }
        porNick.put(chave, cliente);
    }

    /** Remove o cliente da sala e encerra a conexão dele. */
    public synchronized void remover(String nome) {
        ClienteConectado removido = porNick.remove(chave(nome));
        if (removido != null) {
            removido.encerrar();
        }
    }

    /**
     * Tira o cliente da sala sem fechar a conexão (usado na troca de sala).
     *
     * @return o cliente removido, ou null se não estava aqui
     */
    public synchronized ClienteConectado desregistrar(String nome) {
        return porNick.remove(chave(nome));
    }

    public synchronized boolean estaConectado(String nome) {
        return porNick.containsKey(chave(nome));
    }

    public synchronized int total() {
        return porNick.size();
    }

    /** Apelidos em ordem alfabética (sem diferenciar maiúsculas). */
    public synchronized List<String> nicks() {
        return porNick.values().stream()
                .map(ClienteConectado::nome)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    /**
     * Entrega um quadro para todos os clientes da sala.
     * Cliente lento (fila cheia) é desconectado para não travar os demais.
     */
    public synchronized void broadcast(String quadro) {
        List<ClienteConectado> lentos = new ArrayList<>();
        for (ClienteConectado cliente : porNick.values()) {
            if (!cliente.oferecer(quadro)) {
                lentos.add(cliente);
            }
        }
        for (ClienteConectado lento : lentos) {
            Log.erro("Removendo cliente lento: " + lento.nome());
            porNick.remove(chave(lento.nome()));
            lento.encerrar();
        }
    }

    /** Entrega um quadro só para um cliente. Retorna false se não estiver na sala. */
    public synchronized boolean enviarPara(String nome, String quadro) {
        ClienteConectado alvo = porNick.get(chave(nome));
        if (alvo == null) {
            return false;
        }
        if (!alvo.oferecer(quadro)) {
            porNick.remove(chave(alvo.nome()));
            alvo.encerrar();
            return false;
        }
        return true;
    }

    /**
     * Publica uma mensagem pública: entrega para todos e guarda no histórico
     * (as duas coisas na mesma trava, para a ordem ficar igual para todo mundo).
     */
    public synchronized void publicarMensagem(String hora, String nome, String texto) {
        broadcast(Protocolo.mensagemHora(hora, nome, texto));
        historico.addLast(new Registro(hora, nome, texto));
        while (historico.size() > maxHistorico) {
            historico.removeFirst();
        }
    }

    /** Quadros HIST das últimas mensagens, na ordem antiga → nova. */
    public synchronized List<String> quadrosDoHistorico() {
        List<String> quadros = new ArrayList<>();
        for (Registro registro : historico) {
            quadros.add(Protocolo.historico(registro.hora(), registro.nome(), registro.texto()));
        }
        return quadros;
    }

    /** Guarda uma mensagem vinda do arquivo na partida do servidor (sem broadcast). */
    public synchronized void restaurarHistorico(String hora, String nome, String texto) {
        historico.addLast(new Registro(hora, nome, texto));
        while (historico.size() > maxHistorico) {
            historico.removeFirst();
        }
    }

    /** Avisa todo mundo e encerra todas as conexões entregando o que resta na fila. */
    public synchronized void encerrarTodos(String quadroDespedida) {
        for (ClienteConectado cliente : porNick.values()) {
            cliente.oferecer(quadroDespedida);
            cliente.encerrarDrenado(500);
        }
        porNick.clear();
    }

    private static String chave(String nome) {
        return nome.toLowerCase();
    }
}
