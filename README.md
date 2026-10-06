# ChatViaSocket

[![CI](https://github.com/EduardoAlvez/ChatViaSocket1.0/actions/workflows/ci.yml/badge.svg)](https://github.com/EduardoAlvez/ChatViaSocket1.0/actions/workflows/ci.yml)
[![Licença MIT](https://img.shields.io/badge/licen%C3%A7a-MIT-blue.svg)](LICENSE)
![Java](https://img.shields.io/badge/Java-17-E76F00?logo=openjdk&logoColor=white)
![Maven](https://img.shields.io/badge/Maven-3.9-C71A36?logo=apachemaven&logoColor=white)
![JUnit](https://img.shields.io/badge/JUnit-5-25A162?logo=junit5&logoColor=white)

## 📌 Visão Geral

Bate-papo em tempo real usando **sockets TCP**, com **protocolo próprio**,
broadcast que **nunca trava**, histórico das últimas mensagens e mensagens
privadas — feito em Java puro, sem bibliotecas de rede.

![Chat rodando](chat.png)

## ✨ Funcionalidades

- **Múltiplos clientes** conectados ao mesmo tempo (limite de 100 na sala)
- **Broadcast** de mensagens públicas com **hora carimbada pelo servidor**
- **Entrada/saída anunciadas** para a sala inteira (`fulano entrou` / `saiu`)
- **Mensagens privadas** (`/w`) — só o destinatário e quem enviou enxergam
- **Histórico**: quem entra recebe as **últimas 20 mensagens** na hora
- **Lista de participantes** a qualquer momento (`/lista`)
- **Servidor também fala**: o operador digita no console e todos recebem
- **Broadcast não bloqueante**: fila de envio por cliente — um cliente lento
  é desconectado em vez de travar o chat de todo mundo
- **Graceful shutdown**: `/sair` no servidor avisa todos os clientes antes de fechar
- **Validação nos dois lados**: nome (16 chars, sem espaço) e mensagem (500 chars)
- **IP e porta configuráveis** por linha de comando (dá para jogar em outra máquina)

## 🚀 Como Executar

### ✅ Pré-requisitos

- **JDK 17** ou superior
- **Maven 3.9+** (ou só o JDK, se baixar o jar da [Release](https://github.com/EduardoAlvez/ChatViaSocket1.0/releases))

### 1️⃣ Compilar

```bash
mvn package
```

### 2️⃣ Subir o servidor

```bash
java -jar target/chat-via-socket-1.0.0.jar          # porta 6666
java -jar target/chat-via-socket-1.0.0.jar 7777      # porta outra
```

### 3️⃣ Abrir um ou mais clientes (cada janela/terminal é um usuário)

```bash
java -cp target/chat-via-socket-1.0.0.jar com.portfolio.chat.cliente.ClienteChat
java -cp target/chat-via-socket-1.0.0.jar com.portfolio.chat.cliente.ClienteChat 192.168.0.10 6666
```

Pela IDE: rode as classes `ServidorChat` e `ClienteChat` (mains separados).

No primeiro prompt, **digite seu nome** e pronto para conversar.

## 🕹️ Comandos do Cliente

| Comando | O que faz |
|---------|-----------|
| `/lista` | mostra quem está na sala |
| `/w <nick> <mensagem>` | mensagem privada só para aquela pessoa |
| `/ajuda` | resumo dos comandos |
| `/sair` | sai da sala e desconecta |
| texto livre | vira mensagem pública para todos |

No **console do servidor**: qualquer texto vira aviso para a sala e `/sair`
encerra o servidor avisando todo mundo.

## 📦 Protocolo

Os quadros são strings UTF com campos separados por `|` — simples de ler no
tcpdump e de testar sem dependência nenhuma:

| Quadro | Origem | Significado |
|--------|--------|-------------|
| `ENTRAR\|nome` | cliente → servidor | pedido de entrada |
| `OK\|nome` | servidor → cliente | entrada aceita |
| `ERRO\|motivo` | servidor → cliente | recusa ou uso inválido |
| `ENTROU\|nome` / `SAIU\|nome` | servidor → sala | notificação da sala |
| `MSG\|hora\|de\|texto` | cliente → servidor → sala | mensagem pública |
| `PRIVADO\|hora\|de\|para\|texto` | cliente → servidor → alvo | mensagem privada |
| `LISTA` / `LISTA\|nicks` | cliente ↔ servidor | quem está na sala |
| `HIST\|hora\|de\|texto` | servidor → cliente | mensagens anteriores ao entrar |
| `SAIR` | cliente → servidor | sair da sala |
| `FIM\|motivo` | servidor → sala | servidor encerrou |

O texto é sempre o **último** campo, então pode conter `|` sem quebrar o parse.

## ⚙️ Como Funciona Por Dentro

- **Fila por cliente**: o broadcast só faz `offer()` na fila de cada um — quem
  não lê enche a fila e é **desconectado**, nunca trava os outros
- **Ordem consistente**: registro, broadcast e histórico correm sob a mesma
  trava, então todo mundo vê a sequência na mesma ordem
- **Duas threads por cliente** no servidor: uma lê, outra escreve (drena a fila)
- **Cliente**: uma thread só para receber, a principal só digita — a janela
  nunca trava esperando rede

## 🏗️ Estrutura do Projeto

```plain
ChatViaSocket1.0/
├── pom.xml                               # Build Maven (Java 17, JUnit 5)
├── .github/workflows/ci.yml              # CI: build + testes + jar
├── chat.png                              # Print do chat rodando
└── src/
    ├── main/java/com/portfolio/chat/
    │   ├── Log.java                      # Log com hora no console
    │   ├── protocolo/                    # Protocolo, Quadro, TipoQuadro...
    │   ├── servidor/                     # ServidorChat, Servidor, fila de envio
    │   └── cliente/                      # ClienteChat, RecebedorMensagens, Renderizador
    └── test/java/com/portfolio/chat/     # 55 testes JUnit 5
```

## 🧪 Testes

```bash
mvn test
```

**55 testes** em 4 suítes:

- **Protocolo** — parse, validação de nome/mensagem, montagem dos quadros
- **Sala** — registro, nomes duplicados, broadcast, cliente lento, histórico
- **Renderização** — cada quadro vira a linha certa no console
- **Integração** — servidor de verdade em porta efêmera conversando por
  socket real: entrada, mensagem pública, whisper, histórico, sala cheia,
  encerramento e timeout de quem entra e não fala nada

Roda em qualquer máquina, sem Docker e sem rede externa.

## 🛠️ Tecnologias

- **Java 17** — `ServerSocket`/`Socket` e `DataInputStream`/`DataOutputStream`
- **Threads + ExecutorService** — um atendimento por cliente, escrita em fila
- **JUnit 5** + Maven Surefire
- **GitHub Actions** — build e testes a cada push

## 📄 Licença

Distribuído sob a licença [MIT](LICENSE).
