package com.portfolio.chat.cliente.swing;

/**
 * Restabelece a conexão depois que ela cai: recebe a janela morta,
 * abre um socket novo e ergue uma janela nova no lugar.
 */
@FunctionalInterface
public interface Reconector {

    void reconectar(TelaChat telaAntiga);
}
