package com.portfolio.chat.servidor;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GerenciadorSalasTest {

    private static ClienteConectado cliente(String nome) {
        DataOutputStream saida = new DataOutputStream(new ByteArrayOutputStream());
        return new ClienteConectado(nome, saida, 5);
    }

    @Test
    void salaPadraoSempreExisteEAceitaTodos() {
        GerenciadorSalas salas = new GerenciadorSalas();

        Sala geral = salas.padrao();
        assertNotNull(geral);
        assertEquals("geral", geral.nome());
        assertTrue(geral.aberta());
        assertTrue(geral.aceita("qualquer coisa"));
    }

    @Test
    void criarComSenhaSoAceitaQuemSabe() {
        GerenciadorSalas salas = new GerenciadorSalas();

        Sala vip = salas.criar("vip", "senha1");

        assertFalse(vip.aberta());
        assertTrue(vip.aceita("senha1"));
        assertFalse(vip.aceita("outra"));
        assertFalse(vip.aceita(""));
    }

    @Test
    void criarSemSenhaFazSalaAberta() {
        GerenciadorSalas salas = new GerenciadorSalas();

        Sala livre = salas.criar("livre", "");

        assertTrue(livre.aberta());
        assertTrue(livre.aceita("atéErrada"));
    }

    @Test
    void criarSalaDuplicadaERejeitada() {
        GerenciadorSalas salas = new GerenciadorSalas();
        salas.criar("reuniao", "senha1");

        IllegalArgumentException erro = assertThrows(IllegalArgumentException.class,
                () -> salas.criar("REUNIAO", "outra"));
        assertTrue(erro.getMessage().contains("já existe"));
        assertEquals(2, salas.nomes().size());
    }

    @Test
    void criarComNomeInvalidoERejeitado() {
        GerenciadorSalas salas = new GerenciadorSalas();

        IllegalArgumentException erro = assertThrows(IllegalArgumentException.class,
                () -> salas.criar("tem espaço", "senha1"));
        assertTrue(erro.getMessage().contains("letras"));
    }

    @Test
    void criarComSenhaCurtaERejeitada() {
        GerenciadorSalas salas = new GerenciadorSalas();

        IllegalArgumentException erro = assertThrows(IllegalArgumentException.class,
                () -> salas.criar("vip", "abc"));
        assertTrue(erro.getMessage().contains("pelo menos"));
        assertNull(salas.obter("vip"));
    }

    @Test
    void obterIgnoraMaiusculasENomeOriginalFicaIntacto() {
        GerenciadorSalas salas = new GerenciadorSalas();
        salas.criar("Vendas", "");

        assertNotNull(salas.obter("VENDAS"));
        assertNotNull(salas.obter("vendas"));
        assertEquals("Vendas", salas.obter("VENDAS").nome());
        assertNull(salas.obter("naoexiste"));
    }

    @Test
    void nomesEmOrdemAlfabetica() {
        GerenciadorSalas salas = new GerenciadorSalas();
        salas.criar("vendas", "");
        salas.criar("apoio", "");

        assertEquals(List.of("apoio", "geral", "vendas"), salas.nomes());
    }

    @Test
    void moverTiraDaOrigemELevaParaODestinoSemFechar() {
        GerenciadorSalas salas = new GerenciadorSalas();
        Sala vendas = salas.criar("vendas", "");
        ClienteConectado ana = cliente("ana");
        salas.padrao().clientes().registrar(ana);

        salas.mover(ana, salas.padrao(), vendas);

        assertEquals(0, salas.padrao().clientes().total());
        assertEquals(List.of("ana"), vendas.clientes().nicks());
        assertFalse(ana.fechado());
    }

    @Test
    void moverParaAMesmaSalaERejeitado() {
        GerenciadorSalas salas = new GerenciadorSalas();
        ClienteConectado ana = cliente("ana");
        salas.padrao().clientes().registrar(ana);

        IllegalArgumentException erro = assertThrows(IllegalArgumentException.class,
                () -> salas.mover(ana, salas.padrao(), salas.padrao()));
        assertTrue(erro.getMessage().contains("já está nessa sala"));
        assertEquals(1, salas.padrao().clientes().total());
    }

    @Test
    void moverComColisaoDeNickNaoTiraDaOrigem() {
        GerenciadorSalas salas = new GerenciadorSalas();
        Sala vendas = salas.criar("vendas", "");
        vendas.clientes().registrar(cliente("ANA"));
        ClienteConectado ana = cliente("ana");
        salas.padrao().clientes().registrar(ana);

        IllegalArgumentException erro = assertThrows(IllegalArgumentException.class,
                () -> salas.mover(ana, salas.padrao(), vendas));
        assertTrue(erro.getMessage().contains("já em uso"));
        assertEquals(1, salas.padrao().clientes().total());
        assertEquals(1, vendas.clientes().total());
    }

    @Test
    void broadcastTodasChegaEmTodasAsSalas() {
        GerenciadorSalas salas = new GerenciadorSalas();
        Sala vendas = salas.criar("vendas", "");
        ClienteConectado ana = cliente("ana");
        ClienteConectado bia = cliente("bia");
        salas.padrao().clientes().registrar(ana);
        vendas.clientes().registrar(bia);

        salas.broadcastTodas("FIM|aviso geral");

        assertEquals("FIM|aviso geral", ana.semBloquear());
        assertEquals("FIM|aviso geral", bia.semBloquear());
    }

    @Test
    void totalSomaTodasAsSalas() {
        GerenciadorSalas salas = new GerenciadorSalas();
        Sala vendas = salas.criar("vendas", "");
        salas.padrao().clientes().registrar(cliente("ana"));
        salas.padrao().clientes().registrar(cliente("bia"));
        vendas.clientes().registrar(cliente("carlos"));

        assertEquals(3, salas.total());
    }
}
