package com.portfolio.chat.cliente;

import com.portfolio.chat.protocolo.Protocolo;
import com.portfolio.chat.protocolo.Quadro;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

class RenderizadorTest {

    private static Quadro quadro(String bruto) {
        try {
            return Protocolo.parse(bruto);
        } catch (Exception e) {
            fail("quadro inválido no teste: " + bruto);
            throw new AssertionError();
        }
    }

    private static String render(String eu, String bruto) {
        return new Renderizador(eu).render(quadro(bruto));
    }

    @Test
    void mensagemPublicaComHoraENome() {
        assertEquals("[10:00] ana: oi tudo",
                render("bia", "MSG|10:00|ana|oi tudo"));
    }

    @Test
    void mensagemComPipeNoTexto() {
        assertEquals("[10:00] ana: a|b",
                render("bia", "MSG|10:00|ana|a|b"));
    }

    @Test
    void historicoMarcadoComoTal() {
        assertEquals("[09:00] ana: ontem (histórico)",
                render("bia", "HIST|09:00|ana|ontem"));
    }

    @Test
    void entrouDestacaVoceEDemais() {
        assertEquals("Você entrou na sala", render("ana", "ENTROU|ana"));
        assertEquals("• bia entrou", render("ana", "ENTROU|bia"));
    }

    @Test
    void saiuMostraQuem() {
        assertEquals("• bia saiu", render("ana", "SAIU|bia"));
    }

    @Test
    void privadoRecebidoMostraDeQuem() {
        assertEquals("[10:00] (privado de ana): psst",
                render("bia", "PRIVADO|10:00|ana|bia|psst"));
    }

    @Test
    void privadoEnviadoMostraParaQuem() {
        assertEquals("[10:00] (privado para bia): psst",
                render("ana", "PRIVADO|10:00|ana|bia|psst"));
    }

    @Test
    void erroERealceComExclamacao() {
        assertEquals("[!] nome já em uso", render("ana", "ERRO|nome já em uso"));
    }

    @Test
    void listaComAviso() {
        assertEquals("Na sala: ana,bia", render("ana", "LISTA|ana,bia"));
    }

    @Test
    void boasVindasEDespedida() {
        assertEquals("Conectado como ana", render("ana", "OK|ana"));
        assertEquals("[!] Servidor encerrado", render("ana", "FIM|Servidor encerrado"));
    }

    @Test
    void confirmacaoDeSala() {
        assertEquals("Você está na sala vendas", render("ana", "SALOK|vendas"));
    }

    @Test
    void listaDeSalas() {
        assertEquals("Salas: geral,vendas", render("ana", "SALAS|geral,vendas"));
    }
}
