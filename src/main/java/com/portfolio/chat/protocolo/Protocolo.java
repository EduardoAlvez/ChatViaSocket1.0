package com.portfolio.chat.protocolo;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Protocolo do chat: monta, valida e interpreta quadros.
 *
 * <p>Um quadro é uma string UTF com campos separados por {@code |}:
 * o primeiro campo é o {@link TipoQuadro} e os demais dependem do tipo.
 * O texto de mensagens é sempre o último campo, então pode conter {@code |}
 * sem quebrar o parse (use {@link #textoApos(Quadro, int)}).</p>
 */
public final class Protocolo {

    /** Porta padrão do servidor. */
    public static final int PORTA_PADRAO = 6666;
    /** Tamanho máximo do nome (apelido). */
    public static final int MAX_NOME = 16;
    /** Tamanho máximo do texto de uma mensagem. */
    public static final int MAX_TEXTO = 500;
    /** Quantidade de mensagens guardadas para quem entra na sala. */
    public static final int MAX_HISTORICO = 20;
    /** Limite de clientes simultâneos na sala. */
    public static final int MAX_CLIENTES = 100;
    /** Tempo máximo (ms) que o servidor espera o primeiro ENTRAR de um cliente. */
    public static final int TIMEOUT_ENTRADA_MS = 30_000;
    /** Tamanho mínimo de uma senha de sala. */
    public static final int MIN_SENHA = 4;
    /** Tamanho máximo de uma senha de sala. */
    public static final int MAX_SENHA = 32;
    /** Nome que ninguém pode usar (é o do broadcast do servidor). */
    public static final String NICK_RESERVADO = "servidor";

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    private Protocolo() {
    }

    // ---------------------------------------------------------------
    // Parse
    // ---------------------------------------------------------------

    /**
     * Interpreta um quadro cru vindo do outro lado da conexão.
     *
     * @throws ProtocoloException se o quadro estiver vazio ou o tipo for desconhecido
     */
    public static Quadro parse(String linha) throws ProtocoloException {
        if (linha == null || linha.isEmpty()) {
            throw new ProtocoloException("quadro vazio");
        }
        String[] partes = linha.split("\\|", -1);
        TipoQuadro tipo;
        try {
            tipo = TipoQuadro.valueOf(partes[0]);
        } catch (IllegalArgumentException e) {
            throw new ProtocoloException("tipo de quadro desconhecido: " + partes[0]);
        }
        List<String> campos = new ArrayList<>();
        for (int i = 1; i < partes.length; i++) {
            campos.add(partes[i]);
        }
        return new Quadro(tipo, campos);
    }

    /**
     * Junta os campos a partir de {@code inicio} como texto único
     * (preserva {@code |} dentro da mensagem).
     */
    public static String textoApos(Quadro quadro, int inicio) {
        List<String> campos = quadro.campos();
        if (inicio >= campos.size()) {
            return "";
        }
        return String.join("|", campos.subList(inicio, campos.size()));
    }

    // ---------------------------------------------------------------
    // Validações
    // ---------------------------------------------------------------

    /**
     * Valida e normaliza um nome de usuário.
     *
     * @return o nome validado (sem espaços nas pontas)
     * @throws IllegalArgumentException se o nome for inválido
     */
    public static String validarNome(String nome) {
        if (nome == null) {
            throw new IllegalArgumentException("nome vazio");
        }
        String limpo = nome.trim();
        if (limpo.isEmpty()) {
            throw new IllegalArgumentException("nome vazio");
        }
        if (limpo.length() > MAX_NOME) {
            throw new IllegalArgumentException("nome muito longo (máximo " + MAX_NOME + " caracteres)");
        }
        if (limpo.equalsIgnoreCase(NICK_RESERVADO)) {
            throw new IllegalArgumentException("esse nome está reservado");
        }
        for (char c : limpo.toCharArray()) {
            if (!Character.isLetterOrDigit(c) && c != '_' && c != '-') {
                throw new IllegalArgumentException("o nome só pode ter letras, números, _ e -");
            }
        }
        return limpo;
    }

    /**
     * Valida e normaliza o texto de uma mensagem.
     *
     * @return o texto validado
     * @throws IllegalArgumentException se o texto for vazio, longo demais ou tiver controle
     */
    public static String validarTexto(String texto) {
        if (texto == null) {
            throw new IllegalArgumentException("mensagem vazia");
        }
        String limpo = texto.trim();
        if (limpo.isEmpty()) {
            throw new IllegalArgumentException("mensagem vazia");
        }
        if (limpo.length() > MAX_TEXTO) {
            throw new IllegalArgumentException("mensagem muito longa (máximo " + MAX_TEXTO + " caracteres)");
        }
        for (char c : limpo.toCharArray()) {
            if (Character.isISOControl(c)) {
                throw new IllegalArgumentException("a mensagem tem caracteres inválidos");
            }
        }
        return limpo;
    }

    /**
     * Valida e normaliza a senha de uma sala.
     * Senha vazia ou nula é aceita e significa "sala aberta".
     *
     * @return a senha validada (sem espaços nas pontas)
     * @throws IllegalArgumentException se a senha tiver tamanho inválido ou caracteres de controle
     */
    public static String validarSenha(String senha) {
        if (senha == null) {
            return "";
        }
        String limpo = senha.trim();
        if (limpo.isEmpty()) {
            return "";
        }
        if (limpo.length() < MIN_SENHA) {
            throw new IllegalArgumentException(
                    "a senha precisa ter pelo menos " + MIN_SENHA + " caracteres");
        }
        if (limpo.length() > MAX_SENHA) {
            throw new IllegalArgumentException(
                    "a senha pode ter no máximo " + MAX_SENHA + " caracteres");
        }
        for (char c : limpo.toCharArray()) {
            if (Character.isISOControl(c)) {
                throw new IllegalArgumentException("a senha tem caracteres inválidos");
            }
        }
        return limpo;
    }

    /** Hora atual no formato HH:mm (fuso do sistema). */
    public static String horaAtual() {
        return LocalTime.now().format(HORA);
    }

    // ---------------------------------------------------------------
    // Quadros do cliente para o servidor
    // ---------------------------------------------------------------

    public static String entrar(String nome) {
        return montar(TipoQuadro.ENTRAR, validarNome(nome));
    }

    public static String sair() {
        return montar(TipoQuadro.SAIR);
    }

    public static String mensagem(String texto) {
        return montar(TipoQuadro.MSG, validarTexto(texto));
    }

    public static String privado(String para, String texto) {
        return montar(TipoQuadro.PRIVADO, validarNome(para), validarTexto(texto));
    }

    public static String lista() {
        return montar(TipoQuadro.LISTA);
    }

    /**
     * Monta o pedido de criação de sala. Com senha, o cliente gera o salt e
     * manda só o hash — a senha não sai da máquina. Senha vazia = sala aberta.
     */
    public static String criarSala(String sala, String senha) {
        String nome = validarNome(sala);
        String senhaLimpa = validarSenha(senha);
        if (senhaLimpa.isEmpty()) {
            return montar(TipoQuadro.CRIARSALA, nome, "", "");
        }
        String salt = SenhaHash.novoSalt();
        return montar(TipoQuadro.CRIARSALA, nome, salt, SenhaHash.daSenha(senhaLimpa, salt));
    }

    /**
     * Pedido de troca de sala. Sala com senha responde CHAVE e o cliente
     * completa com {@link #entrarSalaComResposta(String, String)}.
     */
    public static String entrarSala(String sala) {
        return montar(TipoQuadro.ENTRASALA, validarNome(sala));
    }

    /** Resposta ao desafio CHAVE: prova de que o cliente sabe a senha. */
    public static String entrarSalaComResposta(String sala, String respostaHex) {
        return montar(TipoQuadro.ENTRASALAH, validarNome(sala), respostaHex);
    }

    public static String pedirSalas() {
        return montar(TipoQuadro.SALAS);
    }

    // ---------------------------------------------------------------
    // Quadros do servidor para o cliente
    // ---------------------------------------------------------------

    public static String ok(String nome) {
        return montar(TipoQuadro.OK, nome);
    }

    public static String erro(String motivo) {
        return montar(TipoQuadro.ERRO, cortar(motivo, 200));
    }

    public static String entrou(String nome) {
        return montar(TipoQuadro.ENTROU, nome);
    }

    public static String saiu(String nome) {
        return montar(TipoQuadro.SAIU, nome);
    }

    /** Mensagem pública com carimbo de hora. */
    public static String mensagemHora(String hora, String nome, String texto) {
        return montar(TipoQuadro.MSG, hora, nome, texto);
    }

    /** Mensagem privada: de quem, para quem e o texto. */
    public static String privadoHora(String hora, String de, String para, String texto) {
        return montar(TipoQuadro.PRIVADO, hora, de, para, texto);
    }

    /** Lista de apelidos separada por vírgula. */
    public static String listaDe(List<String> nicks) {
        return montar(TipoQuadro.LISTA, String.join(",", nicks));
    }

    /** Mensagem do histórico entregue a quem acaba de entrar. */
    public static String historico(String hora, String nome, String texto) {
        return montar(TipoQuadro.HIST, hora, nome, texto);
    }

    public static String fim(String motivo) {
        return montar(TipoQuadro.FIM, cortar(motivo, 200));
    }

    /** Confirmação de que o cliente está na sala (inicial ou após trocar). */
    public static String salaOk(String sala) {
        return montar(TipoQuadro.SALOK, sala);
    }

    /** Desafio de senha: cliente responde com {@link #entrarSalaComResposta}. */
    public static String chave(String saltHex, String nonceHex) {
        return montar(TipoQuadro.CHAVE, saltHex, nonceHex);
    }

    /** Lista de salas separada por vírgula (resposta ao pedido SALAS). */
    public static String salasDe(List<String> nomes) {
        return montar(TipoQuadro.SALAS, String.join(",", nomes));
    }

    // ---------------------------------------------------------------
    // Auxiliares
    // ---------------------------------------------------------------

    /** Monta o quadro final juntando tipo e campos com {@code |}. */
    public static String montar(TipoQuadro tipo, String... campos) {
        StringBuilder sb = new StringBuilder(tipo.name());
        for (String campo : campos) {
            sb.append('|').append(campo);
        }
        return sb.toString();
    }

    private static String cortar(String texto, int max) {
        if (texto == null) {
            return "";
        }
        return texto.length() <= max ? texto : texto.substring(0, max);
    }
}
