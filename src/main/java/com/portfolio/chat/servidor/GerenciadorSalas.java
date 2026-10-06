package com.portfolio.chat.servidor;

import com.portfolio.chat.protocolo.Protocolo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Registro de todas as salas do servidor.
 *
 * <p>Todos os métodos são sincronizados aqui: operações que mexem em duas
 * salas ao mesmo tempo (troca de sala) ficam atômicas e na ordem certa —
 * o broadcast de cada sala continua travado na própria sala.</p>
 */
public final class GerenciadorSalas {

    /** Sala que sempre existe e onde todo mundo começa. */
    public static final String SALA_PADRAO = "geral";

    private final Map<String, Sala> salas = new HashMap<>();
    private final int maxClientes;
    private final int maxHistorico;

    public GerenciadorSalas() {
        this(Protocolo.MAX_CLIENTES, Protocolo.MAX_HISTORICO);
    }

    /** Permite limites menores nos testes. */
    public GerenciadorSalas(int maxClientes, int maxHistorico) {
        this.maxClientes = maxClientes;
        this.maxHistorico = maxHistorico;
        salas.put(SALA_PADRAO.toLowerCase(), new Sala(SALA_PADRAO, null, maxClientes, maxHistorico));
    }

    /** Sala inicial (sempre existe). */
    public synchronized Sala padrao() {
        return salas.get(SALA_PADRAO.toLowerCase());
    }

    /** Procura sala pelo nome (sem diferenciar maiúsculas). Retorna null se não existir. */
    public synchronized Sala obter(String nome) {
        if (nome == null) {
            return null;
        }
        return salas.get(nome.trim().toLowerCase());
    }

    /**
     * Cria uma sala nova. Senha vazia cria sala aberta.
     *
     * @throws IllegalArgumentException se o nome for inválido ou a sala já existir
     */
    public synchronized Sala criar(String nome, String senha) {
        String limpo = Protocolo.validarNome(nome);
        String chave = limpo.toLowerCase();
        if (salas.containsKey(chave)) {
            throw new IllegalArgumentException("essa sala já existe");
        }
        String senhaLimpa = Protocolo.validarSenha(senha);
        SenhaSala senhaSala = senhaLimpa.isEmpty() ? null : SenhaSala.nova(senhaLimpa);
        Sala sala = new Sala(limpo, senhaSala, maxClientes, maxHistorico);
        salas.put(chave, sala);
        return sala;
    }

    /** Reconstrói uma sala lida do disco (usado na carga inicial). */
    synchronized Sala recriar(String nome, SenhaSala senha) {
        String chave = nome.toLowerCase();
        Sala sala = new Sala(nome, senha, maxClientes, maxHistorico);
        salas.put(chave, sala);
        return sala;
    }

    /** Nomes de todas as salas em ordem alfabética. */
    public synchronized List<String> nomes() {
        return salas.values().stream()
                .map(Sala::nome)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    /**
     * Move o cliente de uma sala para outra: registra no destino primeiro
     * (se falhar, não sai da origem) e só então remove da origem,
     * avisando as duas salas.
     *
     * @throws IllegalArgumentException se for a mesma sala, o nome estiver em uso
     *                                 ou o destino estiver cheio
     */
    public synchronized void mover(ClienteConectado cliente, Sala origem, Sala destino) {
        if (origem == destino) {
            throw new IllegalArgumentException("você já está nessa sala");
        }
        destino.clientes().registrar(cliente);
        origem.clientes().desregistrar(cliente.nome());
        origem.clientes().broadcast(Protocolo.saiu(cliente.nome()));
        destino.clientes().broadcast(Protocolo.entrou(cliente.nome()));
    }

    /** Guarda a mensagem na sala (memória + arquivo, quando houver). */
    public synchronized void publicarMensagem(Sala sala, String hora, String de, String texto) {
        sala.clientes().publicarMensagem(hora, de, texto);
    }

    /** Entrega um quadro para todas as salas (aviso do operador, encerramento). */
    public synchronized void broadcastTodas(String quadro) {
        for (Sala sala : salas.values()) {
            sala.clientes().broadcast(quadro);
        }
    }

    /** Avisa todo mundo e encerra todas as conexões de todas as salas. */
    public synchronized void encerrarTodas(String quadroDespedida) {
        for (Sala sala : salas.values()) {
            sala.clientes().encerrarTodos(quadroDespedida);
        }
    }

    /** Total de clientes conectados somando todas as salas. */
    public synchronized int total() {
        int total = 0;
        for (Sala sala : salas.values()) {
            total += sala.clientes().total();
        }
        return total;
    }
}
