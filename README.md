# Sistema Inteligente de Reserva de Salas

Projeto final da disciplina de **Sistemas Distribuídos** — Bacharelado em Ciência
da Computação, UTFPR Campus Ponta Grossa (DAINF). Prof. Dr. Richard Ribeiro.

Aplicação cliente/servidor que se comunica por **troca de mensagens JSON via
sockets TCP**, seguindo o protocolo definido em conjunto pela turma.

## Estado atual

| Entrega | Escopo | Situação |
|---|---|---|
| **EP-1** | Autenticação e CRUD do próprio cadastro | ✅ completa |
| EP-2 | Operações de administrador e CRUD de salas | pendente |
| EP-3 | CRUD de reservas e controle de concorrência | pendente |

Operações implementadas: `register`, `login`, `logout`, `read_user`,
`update_user` e `delete_user`, com todos os códigos de status previstos no
protocolo.

## Requisitos

**JDK 21 ou superior.** Nada além disso — as bibliotecas estão em `lib/` e o
banco de dados é embutido, então não há nada para instalar nem configurar.

- [Gson 2.14.0](https://github.com/google/gson) — serialização JSON
- [H2 2.5.250](https://h2database.com) — banco de dados embutido, em arquivo

O código é compilado com `--release 21` de propósito: o bytecode gerado roda em
qualquer JDK a partir do 21, inclusive 25 e 27.

## Como executar

Compilar:

```bash
./compilar.sh
```

Iniciar o **servidor** (digite a porta na janela e clique em Iniciar):

```bash
./run-servidor.sh
```

Iniciar o **cliente** (informe IP e porta do servidor e clique em Conectar):

```bash
./run-cliente.sh
```

> **Execute pelo terminal.** As mensagens JSON trocadas são exibidas no terminal
> que executa cada aplicação, não nas janelas. Abrindo por duplo clique, o log
> não aparece em lugar nenhum.

Há também versões em linha de comando, úteis para testar rapidamente ou para
conversar com o servidor de outro grupo:

```bash
./run-servidor-console.sh 5000
```

```bash
./run-cliente-console.sh
```

O cliente de console aceita `register`, `login`, `read`, `update`, `delete`,
`logout` e `raw <json>`, que envia um JSON arbitrário.

## Como testar

```bash
./testar.sh
```

Compila tudo, sobe um servidor com banco em memória e roda 187 verificações em
seis suítes: validação de campos, tradução das mensagens, banco de dados,
transporte e erros de protocolo, as seis operações da EP-1, e as telas mais a
conexão do cliente. No fim, dispara três clientes simultâneos contra o mesmo
servidor.

Os testes de transporte e de operações montam os bytes na mão, **sem usar as
classes do projeto** — reproduzem a situação do teste de interoperabilidade, em
que o outro lado não compartilha código nenhum com o nosso.

## Banco de dados

O arquivo é criado automaticamente em `dados/reservasalas.mv.db` na primeira
execução, com o esquema montado em código. Não é preciso importar nada.

Na criação, um administrador inicial é cadastrado:

| Campo | Valor |
|---|---|
| e-mail | `admin@email.com` |
| senha | `admin` |

Ele existe porque todo cadastro feito por `register` nasce com perfil `user`, e
sem um administrador não haveria como exercitar as regras que dependem desse
perfil. Para começar do zero, basta apagar a pasta `dados/`.

## Estrutura

```
src/
├── protocolo/     Enquadramento das mensagens, validação de campos e
│                  montagem das respostas. Compartilhado pelos dois lados.
├── servidor/
│   ├── dao/       Acesso ao H2: usuários e sessões
│   ├── modelo/    Entidades
│   └── handlers/  Uma classe por operação do protocolo
├── cliente/       Interface gráfica, modo console e conexão
└── ui/            Elementos visuais comuns às duas telas

testes/            Suítes executadas pelo testar.sh
lib/               Bibliotecas, versionadas para o projeto rodar sem baixar nada
```

## Protocolo

A especificação é mantida pela turma em uma planilha compartilhada. O arquivo
`Protocolo de troca de Mensagens.xlsx` neste repositório é o retrato da versão
contra a qual este código foi escrito — **versão 2.0**.

Resumo do transporte:

- TCP, uma thread por conexão no servidor
- UTF-8 em todas as mensagens
- Uma mensagem = um objeto JSON em linha única, terminado em `\n`
- Limite de 8192 bytes por mensagem, incluindo o `\n`
- Toda requisição gera exatamente uma resposta, na mesma conexão
- A `op` da resposta é a da requisição mais o sufixo `_response`
- Todos os valores são strings, inclusive códigos e números
- Token de sessão com 64 caracteres hexadecimais, válido por 30 minutos sem uso

As mensagens de texto do protocolo trafegam **sem acento**, como a especificação
exige. A acentuação aparece apenas na interface, na hora de exibir.

## Licença

MIT — veja [LICENSE](LICENSE).
