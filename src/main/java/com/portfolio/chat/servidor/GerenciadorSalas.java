package com.portfolio.chat.servidor;

import com.portfolio.chat.Log;
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
    private final HistoricoArquivo historico;

    public GerenciadorSalas() {
        this(Protocolo.MAX_CLIENTES, Protocolo.MAX_HISTORICO, null);
    }

    /** Permite limites menores nos testes. */
    public GerenciadorSalas(int maxClientes, int maxHistorico) {
        this(maxClientes, maxHistorico, null);
    }

    /** Com persistência em disco (usado pelo servidor de verdade). */
    public GerenciadorSalas(HistoricoArquivo historico) {
        this(Protocolo.MAX_CLIENTES, Protocolo.MAX_HISTORICO, historico);
    }

    public GerenciadorSalas(int maxClientes, int maxHistorico, HistoricoArquivo historico) {
        this.maxClientes = maxClientes;
        this.maxHistorico = maxHistorico;
        this.historico = historico;
        salas.put(SALA_PADRAO.toLowerCase(), new Sala(SALA_PADRAO, null, maxClientes, maxHistorico));
        if (historico != null) {
            carregarDoDisco();
        }
    }

    /** Reconstrói salas e históricos a partir dos arquivos. */
    private void carregarDoDisco() {
        for (String[] dados : historico.lerSalas()) {
            String nome = dados[0];
            if (obter(nome) != null) {
                continue;
            }
            try {
                SenhaSala senha = dados[1].isEmpty() || dados[2].isEmpty()
                        ? null
                        : SenhaSala.deDados(dados[1], dados[2]);
                recriar(nome, senha);
            } catch (IllegalArgumentException e) {
                Log.erro("sala corrompida no disco ignorada: " + nome);
            }
        }
        for (Sala sala : List.copyOf(salas.values())) {
            for (String[] linha : historico.lerLinhas(sala.nome())) {
                sala.clientes().restaurarHistorico(linha[0], linha[1], linha[2]);
            }
        }
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
        String senhaLimpa = Protocolo.validarSenha(senha);
        SenhaSala senhaSala = senhaLimpa.isEmpty() ? null : SenhaSala.nova(senhaLimpa);
        return criarInterna(nome, senhaSala);
    }

    /**
     * Cria a sala com o salt + hash calculados pelo cliente — o servidor
     * nunca vê a senha em texto puro. Os dois campos vazios = sala aberta.
     *
     * @throws IllegalArgumentException se nome/formato forem inválidos ou a sala já existir
     */
    public synchronized Sala criarComDados(String nome, String saltHex, String hashHex) {
        boolean saltVazio = saltHex == null || saltHex.isEmpty();
        boolean hashVazio = hashHex == null || hashHex.isEmpty();
        if (saltVazio != hashVazio) {
            throw new IllegalArgumentException("salt e hash precisam vir juntos");
        }
        SenhaSala senhaSala = saltVazio ? null : SenhaSala.deDados(saltHex, hashHex);
        return criarInterna(nome, senhaSala);
    }

    private Sala criarInterna(String nome, SenhaSala senhaSala) {
        String limpo = Protocolo.validarNome(nome);
        String chave = limpo.toLowerCase();
        if (salas.containsKey(chave)) {
            throw new IllegalArgumentException("essa sala já existe");
        }
        Sala sala = new Sala(limpo, senhaSala, maxClientes, maxHistorico);
        salas.put(chave, sala);
        if (historico != null) {
            historico.anexarSala(limpo,
                    senhaSala == null ? "" : senhaSala.saltHex(),
                    senhaSala == null ? "" : senhaSala.hashHex());
        }
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
     * Nomes em ordem alfabética; as salas com senha ganham o sufixo {@code *}
     * (o caractere não é permitido em nomes de sala, então não ambigua).
     */
    public synchronized List<String> nomesMarcados() {
        return salas.values().stream()
                .map(sala -> sala.aberta() ? sala.nome() : sala.nome() + "*")
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
        if (historico != null) {
            historico.anexar(sala.nome(), hora, de, texto);
        }
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
