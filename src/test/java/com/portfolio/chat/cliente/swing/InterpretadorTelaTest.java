package com.portfolio.chat.cliente.swing;

import com.portfolio.chat.cliente.swing.InterpretadorTela.EventoTela;
import com.portfolio.chat.protocolo.Protocolo;
import com.portfolio.chat.protocolo.Quadro;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;

class InterpretadorTelaTest {

    private static Quadro quadro(String bruto) {
        try {
            return Protocolo.parse(bruto);
        } catch (Exception e) {
            fail("quadro inválido no teste: " + bruto);
            throw new AssertionError();
        }
    }

    private static EventoTela interpretar(String eu, String bruto) {
        return new InterpretadorTela(eu).interpretar(quadro(bruto));
    }

    private static EventoTela.Linha linha(String eu, String bruto) {
        EventoTela evento = interpretar(eu, bruto);
        return assertInstanceOf(EventoTela.Linha.class, evento);
    }

    // ---------------------------------------------------------------
    // Mensagens
    // ---------------------------------------------------------------

    @Test
    void mensagemPublicaViraLinhaPublica() {
        EventoTela.Linha linha = linha("bia", "MSG|10:00|ana|oi tudo");

        assertEquals(InterpretadorTela.TipoTexto.PUBLICA, linha.tipo());
        assertEquals("10:00", linha.hora());
        assertEquals("ana", linha.autor());
        assertEquals("oi tudo", linha.texto());
    }

    @Test
    void historicoViraLinhaDeHistorico() {
        EventoTela.Linha linha = linha("bia", "HIST|10:00|ana|ontem");

        assertEquals(InterpretadorTela.TipoTexto.HISTORICO, linha.tipo());
        assertEquals("ontem", linha.texto());
    }

    @Test
    void textoComPipeFicaIntacto() {
        EventoTela.Linha linha = linha("bia", "MSG|10:00|ana|a|b");

        assertEquals("a|b", linha.texto());
    }

    @Test
    void privadaRecebidaVaiParaMim() {
        EventoTela.Linha linha = linha("bia", "PRIVADO|10:00|ana|bia|psst");

        assertEquals(InterpretadorTela.TipoTexto.PRIVADA_RECEBIDA, linha.tipo());
        assertEquals("ana", linha.autor());
        assertEquals("psst", linha.texto());
    }

    @Test
    void privadaEnviadaEhAMinha() {
        EventoTela.Linha linha = linha("ana", "PRIVADO|10:00|ana|bia|psst");

        assertEquals(InterpretadorTela.TipoTexto.PRIVADA_ENVIADA, linha.tipo());
        assertEquals("bia", linha.autor());
        assertEquals("psst", linha.texto());
    }

    // ---------------------------------------------------------------
    // Sistema
    // ---------------------------------------------------------------

    @Test
    void entradaESaidaDoProprioEDiferente() {
        assertEquals("Você entrou na sala", linha("ana", "ENTROU|ana").texto());
        assertEquals("• bia entrou", linha("ana", "ENTROU|bia").texto());
        assertEquals("• bia saiu", linha("ana", "SAIU|bia").texto());
    }

    @Test
    void erroOkEFimSaoSistema() {
        assertEquals(InterpretadorTela.TipoTexto.SISTEMA, linha("ana", "ERRO|deu ruim").tipo());
        assertEquals("deu ruim", linha("ana", "ERRO|deu ruim").texto());
        assertEquals("Conectado como ana", linha("ana", "OK|ana").texto());
        assertEquals("Servidor encerrado", linha("ana", "FIM|Servidor encerrado").texto());
    }

    // ---------------------------------------------------------------
    // Listas e salas
    // ---------------------------------------------------------------

    @Test
    void listaViraParticipantes() {
        EventoTela evento = interpretar("ana", "LISTA|ana,bia,carlos");

        EventoTela.Participantes participantes = assertInstanceOf(EventoTela.Participantes.class, evento);
        assertEquals(List.of("ana", "bia", "carlos"), participantes.nicks());
    }

    @Test
    void listaVaziaFicaVazia() {
        EventoTela evento = interpretar("ana", "LISTA|");

        EventoTela.Participantes participantes = assertInstanceOf(EventoTela.Participantes.class, evento);
        assertEquals(List.of(), participantes.nicks());
    }

    @Test
    void salokViraSalaAtual() {
        EventoTela evento = interpretar("ana", "SALOK|vendas");

        EventoTela.SalaAtual sala = assertInstanceOf(EventoTela.SalaAtual.class, evento);
        assertEquals("vendas", sala.nome());
    }

    @Test
    void salasViraListaDeSalas() {
        EventoTela evento = interpretar("ana", "SALAS|geral,vendas");

        EventoTela.SalasDisponiveis salas = assertInstanceOf(EventoTela.SalasDisponiveis.class, evento);
        assertEquals(List.of("geral", "vendas"), salas.nomes());
    }

    @Test
    void quadrosDeEnvioDoClienteNaoViramLinha() {
        assertNull(interpretar("ana", "ENTRAR|ana"));
        assertNull(interpretar("ana", "SAIR"));
        assertNull(interpretar("ana", "CRIARSALA|vip|senha1"));
        assertNull(interpretar("ana", "ENTRASALA|vip|senha1"));
    }
}
