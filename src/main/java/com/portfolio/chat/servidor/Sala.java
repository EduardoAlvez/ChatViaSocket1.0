package com.portfolio.chat.servidor;

/**
 * Uma sala de chat: nome, senha (null = sala aberta) e seus clientes.
 */
public final class Sala {

    private final String nome;
    private final SenhaSala senha;
    private final GerenciadorClientes clientes;

    public Sala(String nome, SenhaSala senha, GerenciadorClientes clientes) {
        this.nome = nome;
        this.senha = senha;
        this.clientes = clientes;
    }

    public Sala(String nome, SenhaSala senha, int maxClientes, int maxHistorico) {
        this(nome, senha, new GerenciadorClientes(maxClientes, maxHistorico));
    }

    public String nome() {
        return nome;
    }

    public SenhaSala senha() {
        return senha;
    }

    public boolean aberta() {
        return senha == null;
    }

    public GerenciadorClientes clientes() {
        return clientes;
    }

    /** Verdadeiro se a sala aceita a senha informada (sala aberta aceita qualquer coisa). */
    public boolean aceita(String senhaInformada) {
        if (senha == null) {
            return true;
        }
        return senha.confere(senhaInformada);
    }
}
