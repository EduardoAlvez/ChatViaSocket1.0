# ChatViaSocket1.0
Bate papo usando Socket em java!

![Java](https://img.shields.io/badge/Java-17%2B-blue)
![License](https://img.shields.io/badge/License-MIT-green)
![Status](https://img.shields.io/badge/Status-Completado-brightgreen)

## 📌 Visão Geral

Um sistema de chat em tempo real usando sockets TCP, onde múltiplos clientes podem se conectar a um servidor central e trocar mensagens entre si.

## ✨ Funcionalidades Principais

- **Conexão simultânea** de múltiplos clientes
- **Broadcast de mensagens** para todos os participantes
- **Identificação** por nome de usuário
- **Interface simples** via linha de comando
- **Comando 'sair'** para desconexão segura

## 🗂️ Estrutura do Projeto

```plain
ChatTCP/
├── Servidor/
│   └── src/
│       ├── ServidorChat.java          # Classe principal do servidor
│       ├── ClientHandler.java         # Lida com as conexões dos clientes
│       ├── ServidorEnviador.java      # Envia mensagens do servidor
│       ├── ClienteChat.java           # Classe principal do cliente
│       └── RecebedorMensagens.java    # Thread que recebe mensagens
```


## 🚀 Como Executar

### ✅ Pré-requisitos

- JDK 17 ou superior instalado  
- Terminal/CMD disponível  

### ▶️ Executando o Servidor

```bash
cd C:\Users\[]\ChatViaSocket1.0
java -cp bin ServidorChat
```
### ▶️ Executando o Cliente

```bash
cd C:\Users\[]\ChatViaSocket1.0
java -cp bin ClienteChat
```

## 🖥️ Comandos do Usuário

- Ao iniciar, digite **seu nome**
- Escreva **mensagens normalmente**
- Use **`sair`** para desconectar
- Mensagens do servidor começam com **[Servidor]**

## 🛠️ Tecnologias

- **Java Sockets** – Comunicação TCP
- **Threads** – Conexões simultâneas
- **ConcurrentHashMap** – Lista thread-safe de clientes
