package com.portfolio.chat.protocolo;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProtocoloTest {

    // ---------------------------------------------------------------
    // Parse
    // ---------------------------------------------------------------

    @Test
    void parseQuebraTipoECampos() throws Exception {
        Quadro quadro = Protocolo.parse("ENTRAR|ana");
        assertEquals(TipoQuadro.ENTRAR, quadro.tipo());
        assertEquals(List.of("ana"), quadro.campos());
    }

    @Test
    void parseDeQuadroSemCampos() throws Exception {
        Quadro quadro = Protocolo.parse("SAIR");
        assertEquals(TipoQuadro.SAIR, quadro.tipo());
        assertTrue(quadro.campos().isEmpty());
    }

    @Test
    void textoComPipeFicaIntacto() throws Exception {
        Quadro quadro = Protocolo.parse("MSG|10:00|ana|oi|tudo bem");
        assertEquals("oi|tudo bem", Protocolo.textoApos(quadro, 2));
    }

    @Test
    void textoAposForaDoIntervaloVazio() throws Exception {
        Quadro quadro = Protocolo.parse("SAIR");
        assertEquals("", Protocolo.textoApos(quadro, 0));
    }

    @Test
    void parseRejeitaTipoDesconhecido() {
        assertThrows(ProtocoloException.class, () -> Protocolo.parse("OI|ana"));
    }

    @Test
    void parseRejeitaQuadroVazio() {
        assertThrows(ProtocoloException.class, () -> Protocolo.parse(""));
        assertThrows(ProtocoloException.class, () -> Protocolo.parse(null));
    }

    // ---------------------------------------------------------------
    // validarNome
    // ---------------------------------------------------------------

    @Test
    void nomeValidoEhNormalizado() {
        assertEquals("ana_01", Protocolo.validarNome("  ana_01  "));
    }

    @Test
    void nomeComAcentoEhValido() {
        assertEquals("João", Protocolo.validarNome("João"));
    }

    @Test
    void nomeVazioRejeitado() {
        assertThrows(IllegalArgumentException.class, () -> Protocolo.validarNome(null));
        assertThrows(IllegalArgumentException.class, () -> Protocolo.validarNome("   "));
    }

    @Test
    void nomeLongoRejeitado() {
        assertThrows(IllegalArgumentException.class,
                () -> Protocolo.validarNome("a".repeat(Protocolo.MAX_NOME + 1)));
    }

    @Test
    void nomeReservadoRejeitado() {
        assertThrows(IllegalArgumentException.class, () -> Protocolo.validarNome("Servidor"));
        assertThrows(IllegalArgumentException.class, () -> Protocolo.validarNome("SERVIDOR"));
    }

    @Test
    void nomeComEspacoOuPipeRejeitado() {
        assertThrows(IllegalArgumentException.class, () -> Protocolo.validarNome("ana silva"));
        assertThrows(IllegalArgumentException.class, () -> Protocolo.validarNome("a|b"));
    }

    // ---------------------------------------------------------------
    // validarTexto
    // ---------------------------------------------------------------

    @Test
    void textoValidoEhNormalizado() {
        assertEquals("oi tudo bem", Protocolo.validarTexto("  oi tudo bem  "));
    }

    @Test
    void textoVazioRejeitado() {
        assertThrows(IllegalArgumentException.class, () -> Protocolo.validarTexto(null));
        assertThrows(IllegalArgumentException.class, () -> Protocolo.validarTexto("  "));
    }

    @Test
    void textoLongoRejeitado() {
        assertThrows(IllegalArgumentException.class,
                () -> Protocolo.validarTexto("a".repeat(Protocolo.MAX_TEXTO + 1)));
    }

    @Test
    void textoComCaracterDeControleRejeitado() {
        assertThrows(IllegalArgumentException.class, () -> Protocolo.validarTexto("oi\u0000ai"));
    }

    // ---------------------------------------------------------------
    // Montagem dos quadros
    // ---------------------------------------------------------------

    @Test
    void montaEntrarComNomeValidado() {
        assertEquals("ENTRAR|ana", Protocolo.entrar(" ana "));
    }

    @Test
    void montaMensagemPreservandoPipe() {
        assertEquals("MSG|10 + 10 = 20", Protocolo.mensagem("10 + 10 = 20"));
        assertEquals("MSG|a|b", Protocolo.mensagem("a|b"));
    }

    @Test
    void montaPrivadoComDoisCampos() {
        assertEquals("PRIVADO|bia|psst", Protocolo.privado("bia", "psst"));
    }

    @Test
    void listaDeJuntaNicksComVirgula() {
        assertEquals("LISTA|ana,bia", Protocolo.listaDe(List.of("ana", "bia")));
    }

    @Test
    void quadrosDeServidorLevamAHora() {
        assertEquals("MSG|10:05|ana|oi", Protocolo.mensagemHora("10:05", "ana", "oi"));
        assertEquals("HIST|10:05|ana|oi", Protocolo.historico("10:05", "ana", "oi"));
        assertEquals("PRIVADO|10:05|ana|bia|oi",
                Protocolo.privadoHora("10:05", "ana", "bia", "oi"));
    }

    @Test
    void erroCortaMotivoLongo() {
        String quadro = Protocolo.erro("x".repeat(1000));
        assertTrue(quadro.length() <= 4 + 1 + 200);
    }

    @Test
    void horaAtualNoFormatoHoraMinuto() {
        assertTrue(Protocolo.horaAtual().matches("\\d{2}:\\d{2}"));
    }

    // ---------------------------------------------------------------
    // Senha e salas
    // ---------------------------------------------------------------

    @Test
    void validarSenhaVaziaOuNulaFazSalaAberta() {
        assertEquals("", Protocolo.validarSenha(""));
        assertEquals("", Protocolo.validarSenha(null));
        assertEquals("", Protocolo.validarSenha("   "));
    }

    @Test
    void validarSenhaValidaETrim() {
        assertEquals("senha1", Protocolo.validarSenha("  senha1  "));
    }

    @Test
    void validarSenhaCurtaLanca() {
        IllegalArgumentException erro = assertThrows(IllegalArgumentException.class,
                () -> Protocolo.validarSenha("abc"));
        assertTrue(erro.getMessage().contains("pelo menos"));
    }

    @Test
    void validarSenhaLongaLanca() {
        IllegalArgumentException erro = assertThrows(IllegalArgumentException.class,
                () -> Protocolo.validarSenha("x".repeat(33)));
        assertTrue(erro.getMessage().contains("máximo"));
    }

    @Test
    void validarSenhaComCaracterDeControleLanca() {
        assertThrows(IllegalArgumentException.class, () -> Protocolo.validarSenha("abcd\nef"));
    }

    @Test
    void criarSalaMontaQuadroComSenha() throws Exception {
        Quadro quadro = Protocolo.parse(Protocolo.criarSala("vip", "senha1"));
        assertEquals(TipoQuadro.CRIARSALA, quadro.tipo());
        assertEquals(List.of("vip", "senha1"), quadro.campos());
    }

    @Test
    void criarSalaComSenhaVaziaFicaAberta() throws Exception {
        Quadro quadro = Protocolo.parse(Protocolo.criarSala("livre", ""));
        assertEquals(List.of("livre", ""), quadro.campos());
    }

    @Test
    void criarSalaComNomeInvalidoLanca() {
        assertThrows(IllegalArgumentException.class, () -> Protocolo.criarSala("x y", "senha1"));
    }

    @Test
    void entrarSalaMontaQuadro() throws Exception {
        Quadro quadro = Protocolo.parse(Protocolo.entrarSala("vip", "senha1"));
        assertEquals(TipoQuadro.ENTRASALA, quadro.tipo());
        assertEquals(List.of("vip", "senha1"), quadro.campos());
    }

    @Test
    void senhaInformadaComPipeFicaIntacta() throws Exception {
        Quadro quadro = Protocolo.parse(Protocolo.entrarSala("vip", "a|b"));
        assertEquals("a|b", Protocolo.textoApos(quadro, 1));
    }

    @Test
    void pedirSalasESalasDeESalaOk() throws Exception {
        assertEquals(TipoQuadro.SALAS, Protocolo.parse(Protocolo.pedirSalas()).tipo());
        assertEquals("geral,vendas",
                Protocolo.textoApos(Protocolo.parse(Protocolo.salasDe(List.of("geral", "vendas"))), 0));
        assertEquals("SALOK|vendas", Protocolo.salaOk("vendas"));
    }
}
