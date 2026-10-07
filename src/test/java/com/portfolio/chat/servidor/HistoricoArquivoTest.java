package com.portfolio.chat.servidor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HistoricoArquivoTest {

    @TempDir
    Path tempDir;

    @Test
    void anexaMensagemERelêDeVolta() {
        HistoricoArquivo arquivo = new HistoricoArquivo(tempDir);

        arquivo.anexar("vendas", "10:00", "ana", "oi tudo bem");
        arquivo.anexar("vendas", "10:01", "bia", "bom dia");

        List<String[]> linhas = arquivo.lerLinhas("vendas");
        assertEquals(2, linhas.size());
        assertEquals(List.of("10:00", "ana", "oi tudo bem"), List.of(linhas.get(0)));
        assertEquals(List.of("10:01", "bia", "bom dia"), List.of(linhas.get(1)));
    }

    @Test
    void arquivoDeSalaEhPorNomeMinusculo() {
        HistoricoArquivo arquivo = new HistoricoArquivo(tempDir);

        arquivo.anexar("Vendas", "10:00", "ana", "oi");

        assertEquals(1, arquivo.lerLinhas("vendas").size());
        assertTrue(Files.exists(tempDir.resolve("vendas.txt")));
    }

    @Test
    void textoComPipeFicaIntacto() {
        HistoricoArquivo arquivo = new HistoricoArquivo(tempDir);

        arquivo.anexar("geral", "10:00", "ana", "a|b|c");

        assertEquals("a|b|c", arquivo.lerLinhas("geral").get(0)[2]);
    }

    @Test
    void diretorioInexistenteEhCriadoNaGravacao() {
        HistoricoArquivo arquivo = new HistoricoArquivo(tempDir.resolve("sub/past"));

        arquivo.anexar("geral", "10:00", "ana", "oi");

        assertEquals(1, arquivo.lerLinhas("geral").size());
    }

    @Test
    void falhaDeDiscoNaoPropaga() throws IOException {
        Path queNaEhDiretorio = tempDir.resolve("arquivo.txt");
        Files.createFile(queNaEhDiretorio);
        HistoricoArquivo arquivo = new HistoricoArquivo(queNaEhDiretorio);

        assertDoesNotThrow(() -> {
            arquivo.anexar("geral", "10:00", "ana", "oi");
            arquivo.anexarSala("vip", "s", "h");
        });
        assertTrue(arquivo.lerLinhas("geral").isEmpty());
        assertTrue(arquivo.lerSalas().isEmpty());
    }

    @Test
    void salasComESemSenhaRoundtrip() {
        HistoricoArquivo arquivo = new HistoricoArquivo(tempDir);

        arquivo.anexarSala("vip", "salts1", "hash1");
        arquivo.anexarSala("livre", "", "");

        List<String[]> salas = arquivo.lerSalas();
        assertEquals(2, salas.size());
        assertEquals(List.of("vip", "salts1", "hash1"), List.of(salas.get(0)));
        assertEquals(List.of("livre", "", ""), List.of(salas.get(1)));
    }

    @Test
    void linhaMalformadaEhIgnorada() throws IOException {
        Files.writeString(tempDir.resolve("geral.txt"),
                "10:00\tana\tboa linha\nlinha quebrada\n10:02\tbia\toutra\n",
                StandardCharsets.UTF_8);
        HistoricoArquivo arquivo = new HistoricoArquivo(tempDir);

        List<String[]> linhas = arquivo.lerLinhas("geral");

        assertEquals(2, linhas.size());
        assertEquals("boa linha", linhas.get(0)[2]);
        assertEquals("outra", linhas.get(1)[2]);
    }

    @Test
    void arquivoInexistenteRetornaVazio() {
        HistoricoArquivo arquivo = new HistoricoArquivo(tempDir);

        assertTrue(arquivo.lerLinhas("naoexiste").isEmpty());
        assertTrue(arquivo.lerSalas().isEmpty());
    }

    @Test
    void arquivoGrandeRotacionaGuardandoSoAsUltimasLinhas() {
        HistoricoArquivo arquivo = new HistoricoArquivo(tempDir, 5, 1);
        for (int i = 1; i <= 8; i++) {
            arquivo.anexar("geral", "10:0" + i, "ana", "msg" + i);
        }

        List<String[]> linhas = arquivo.lerLinhas("geral");
        assertEquals(5, linhas.size());
        assertEquals("msg4", linhas.get(0)[2]);
        assertEquals("msg8", linhas.get(4)[2]);
    }

    @Test
    void arquivoDentroDoLimiteNaoRotaciona() {
        HistoricoArquivo arquivo = new HistoricoArquivo(tempDir, 5, 1024 * 1024);
        for (int i = 1; i <= 4; i++) {
            arquivo.anexar("geral", "10:0" + i, "ana", "msg" + i);
        }

        assertEquals(4, arquivo.lerLinhas("geral").size());
    }
}
