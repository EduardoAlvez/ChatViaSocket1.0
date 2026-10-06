package com.portfolio.chat.servidor;

import com.portfolio.chat.protocolo.Protocolo;
import com.portfolio.chat.protocolo.Quadro;
import com.portfolio.chat.protocolo.TipoQuadro;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GerenciadorClientesTest {

    private static ClienteConectado cliente(String nome) {
        return cliente(nome, 5);
    }

    private static ClienteConectado cliente(String nome, int capacidadeFila) {
        DataOutputStream saida = new DataOutputStream(new ByteArrayOutputStream());
        return new ClienteConectado(nome, saida, capacidadeFila);
    }

    @Test
    void registraClientesENomesOrdenados() {
        GerenciadorClientes gerenciador = new GerenciadorClientes();
        gerenciador.registrar(cliente("bia"));
        gerenciador.registrar(cliente("ana"));

        assertEquals(List.of("ana", "bia"), gerenciador.nicks());
        assertEquals(2, gerenciador.total());
    }

    @Test
    void nomeDuplicadoIgnoraMaiusculas() {
        GerenciadorClientes gerenciador = new GerenciadorClientes();
        gerenciador.registrar(cliente("ana"));

        IllegalArgumentException erro = assertThrows(IllegalArgumentException.class,
                () -> gerenciador.registrar(cliente("ANA")));
        assertTrue(erro.getMessage().contains("já em uso"));
        assertEquals(1, gerenciador.total());
    }

    @Test
    void salaCheiaRejeitaNovoCliente() {
        GerenciadorClientes gerenciador = new GerenciadorClientes(1, 20);
        gerenciador.registrar(cliente("ana"));

        IllegalArgumentException erro = assertThrows(IllegalArgumentException.class,
                () -> gerenciador.registrar(cliente("bia")));
        assertTrue(erro.getMessage().contains("sala cheia"));
    }

    @Test
    void removerTiraDaSala() {
        GerenciadorClientes gerenciador = new GerenciadorClientes();
        ClienteConectado ana = cliente("ana");
        gerenciador.registrar(ana);
        gerenciador.remover("ANA");

        assertFalse(gerenciador.estaConectado("ana"));
        assertEquals(0, gerenciador.total());
        assertTrue(ana.fechado());
    }

    @Test
    void broadcastEntregaParaTodosSemBloquear() {
        GerenciadorClientes gerenciador = new GerenciadorClientes();
        ClienteConectado ana = cliente("ana");
        ClienteConectado bia = cliente("bia");
        gerenciador.registrar(ana);
        gerenciador.registrar(bia);

        gerenciador.broadcast("MSG|10:00|sistema|ola");

        assertEquals("MSG|10:00|sistema|ola", ana.semBloquear());
        assertEquals("MSG|10:00|sistema|ola", bia.semBloquear());
    }

    @Test
    void clienteLentoEhRemovidoEmVezDeTravarOsOutros() {
        GerenciadorClientes gerenciador = new GerenciadorClientes();
        ClienteConectado devagar = cliente("devagar", 1);
        ClienteConectado rapido = cliente("rapido");
        gerenciador.registrar(devagar);
        gerenciador.registrar(rapido);

        gerenciador.broadcast("1");
        gerenciador.broadcast("2");

        assertEquals(1, gerenciador.total());
        assertFalse(gerenciador.estaConectado("devagar"));
        assertTrue(devagar.fechado());
        assertEquals("1", rapido.semBloquear());
        assertEquals("2", rapido.semBloquear());
    }

    @Test
    void enviarParaEntregaSomenteNoAlvo() {
        GerenciadorClientes gerenciador = new GerenciadorClientes();
        ClienteConectado ana = cliente("ana");
        ClienteConectado bia = cliente("bia");
        gerenciador.registrar(ana);
        gerenciador.registrar(bia);

        assertTrue(gerenciador.enviarPara("ana", "PRIVADO|10:00|bia|ana|psst"));
        assertEquals("PRIVADO|10:00|bia|ana|psst", ana.semBloquear());
        assertNull(bia.semBloquear());
        assertFalse(gerenciador.enviarPara("ze", "qualquer"));
    }

    @Test
    void historicoGuardaSomenteOUltimoLimite() throws Exception {
        GerenciadorClientes gerenciador = new GerenciadorClientes(10, 2);
        gerenciador.publicarMensagem("10:00", "ana", "uma");
        gerenciador.publicarMensagem("10:01", "ana", "duas");
        gerenciador.publicarMensagem("10:02", "ana", "três");

        List<String> quadros = gerenciador.quadrosDoHistorico();
        assertEquals(2, quadros.size());
        Quadro primeiro = Protocolo.parse(quadros.get(0));
        Quadro ultimo = Protocolo.parse(quadros.get(1));
        assertEquals(TipoQuadro.HIST, primeiro.tipo());
        assertEquals("duas", Protocolo.textoApos(primeiro, 2));
        assertEquals("três", Protocolo.textoApos(ultimo, 2));
    }

    @Test
    void publicarMensagemTambemFazBroadcast() throws Exception {
        GerenciadorClientes gerenciador = new GerenciadorClientes();
        ClienteConectado ana = cliente("ana");
        gerenciador.registrar(ana);

        gerenciador.publicarMensagem("10:00", "ana", "oi");

        String recebido = ana.semBloquear();
        Quadro quadro = Protocolo.parse(recebido);
        assertEquals(TipoQuadro.MSG, quadro.tipo());
        assertEquals("oi", Protocolo.textoApos(quadro, 2));
    }

    @Test
    void encerrarTodosEntregaDespedidaEFechaTudo() {
        GerenciadorClientes gerenciador = new GerenciadorClientes();
        ClienteConectado ana = cliente("ana");
        gerenciador.registrar(ana);

        gerenciador.encerrarTodos(Protocolo.fim("Servidor encerrado"));

        assertEquals("FIM|Servidor encerrado", ana.semBloquear());
        assertTrue(ana.fechado());
        assertEquals(0, gerenciador.total());
    }

    @Test
    void ofertaDepoisDeEncerradoRetornaFalso() throws IOException {
        ClienteConectado ana = cliente("ana");
        ana.encerrar();
        assertFalse(ana.oferecer("MSG|x"));
    }
}
