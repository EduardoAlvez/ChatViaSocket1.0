package com.portfolio.chat.servidor;

import com.portfolio.chat.Log;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Persiste salas e histórico em arquivos TSV dentro de um diretório.
 *
 * <p>O texto validado pelo protocolo não tem tabulação nem quebra de linha,
 * então o formato não quebra. Falha de disco nunca derruba o chat: só gera
 * log e o servidor segue com tudo em memória.</p>
 */
public final class HistoricoArquivo {

    private static final String ARQUIVO_SALAS = "salas.tsv";
    private static final int MAX_LINHAS_PADRAO = 1000;
    private static final long MAX_BYTES_PADRAO = 512 * 1024;

    private final Path dir;
    private final int maxLinhas;
    private final long maxBytes;

    public HistoricoArquivo(Path dir) {
        this(dir, MAX_LINHAS_PADRAO, MAX_BYTES_PADRAO);
    }

    /** Permite limites menores nos testes. */
    HistoricoArquivo(Path dir, int maxLinhas, long maxBytes) {
        this.dir = dir;
        this.maxLinhas = maxLinhas;
        this.maxBytes = maxBytes;
    }

    /** Registra uma mensagem no arquivo da sala. */
    public void anexar(String sala, String hora, String de, String texto) {
        Path arquivo = arquivo(sala);
        anexarLinha(arquivo, hora + "\t" + de + "\t" + texto);
        rotacionarSePreciso(arquivo);
    }

    /** Mensagens do arquivo da sala: [hora, de, texto], na ordem gravada. */
    public List<String[]> lerLinhas(String sala) {
        return lerCampos(arquivo(sala), 3);
    }

    /** Registra uma sala com senha (salt/hash vazios = sala aberta). */
    public void anexarSala(String nome, String saltHex, String hashHex) {
        anexarLinha(dir.resolve(ARQUIVO_SALAS), nome + "\t" + saltHex + "\t" + hashHex);
    }

    /** Salas gravadas: [nome, salt, hash]. */
    public List<String[]> lerSalas() {
        return lerCampos(dir.resolve(ARQUIVO_SALAS), 3);
    }

    private Path arquivo(String sala) {
        return dir.resolve(sala.trim().toLowerCase() + ".txt");
    }

    private void anexarLinha(Path arquivo, String linha) {
        try {
            Files.createDirectories(dir);
            Files.write(arquivo, List.of(linha), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            Log.erro("não consegui gravar " + arquivo + ": " + e.getMessage());
        }
    }

    /**
     * Quando o arquivo de uma sala passa do limite de bytes, reescreve
     * guardando só as últimas {@code maxLinhas} linhas — o histórico nunca
     * cresce sem fim. Falha de disco aqui também só gera log.
     */
    private void rotacionarSePreciso(Path arquivo) {
        try {
            if (!Files.exists(arquivo) || Files.size(arquivo) <= maxBytes) {
                return;
            }
            List<String> todas = Files.readAllLines(arquivo, StandardCharsets.UTF_8);
            List<String> finais = todas.size() <= maxLinhas
                    ? todas
                    : todas.subList(todas.size() - maxLinhas, todas.size());
            Files.write(arquivo, finais, StandardCharsets.UTF_8,
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            Log.info("histórico " + arquivo.getFileName()
                    + " rotacionado: " + todas.size() + " -> " + finais.size() + " linhas");
        } catch (IOException e) {
            Log.erro("não consegui rotacionar " + arquivo + ": " + e.getMessage());
        }
    }

    private List<String[]> lerCampos(Path arquivo, int minimo) {
        List<String[]> linhas = new ArrayList<>();
        List<String> brutas;
        try {
            if (!Files.exists(arquivo)) {
                return linhas;
            }
            brutas = Files.readAllLines(arquivo, StandardCharsets.UTF_8);
        } catch (IOException e) {
            Log.erro("não consegui ler " + arquivo + ": " + e.getMessage());
            return linhas;
        }
        for (String bruta : brutas) {
            String[] partes = bruta.split("\t", -1);
            if (partes.length < minimo) {
                continue;
            }
            linhas.add(partes);
        }
        return linhas;
    }
}
