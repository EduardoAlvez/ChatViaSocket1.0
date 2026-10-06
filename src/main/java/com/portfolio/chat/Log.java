package com.portfolio.chat;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Log simples com hora no formato HH:mm:ss para o console.
 */
public final class Log {

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm:ss");

    private Log() {
    }

    public static void info(String mensagem) {
        System.out.println(LocalTime.now().format(HORA) + " " + mensagem);
    }

    public static void erro(String mensagem) {
        System.err.println(LocalTime.now().format(HORA) + " " + mensagem);
    }
}
