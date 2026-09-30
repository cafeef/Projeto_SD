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

**JDK 17 ou superior.** Nada além disso — as bibliotecas estão em `lib/` e o
banco de dados é embutido, então não há nada para instalar nem configurar.

- [Gson 2.14.0](https://github.com/google/gson) — serialização JSON
- [H2 2.5.250](https://h2database.com) — banco de dados embutido, em arquivo

O código é compilado com `--release 17` de propósito: o bytecode gerado roda em
qualquer JDK a partir do 17.

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

### Pelo Eclipse

O projeto já vem com a configuração do Eclipse versionada, então é só importar:

1. **File → Import…**
2. **General → Existing Projects into Workspace → Next**
3. Em *Select root directory*, aponte para a pasta do projeto
4. O projeto aparece marcado na lista → **Finish**

Não use *New Java Project*: isso criaria um projeto do zero, ignorando o
`.classpath` que já traz as bibliotecas de `lib/`.

Para executar, clique com o botão direito em `ServidorGUI.java` ou
`ClienteGUI.java` → **Run As → Java Application**. As mensagens JSON aparecem na
aba **Console** do próprio Eclipse, que faz o papel do terminal.

Se o Eclipse reclamar do nível de compilação, confira em **Window → Preferences →
Java → Installed JREs** qual JDK está registrado. O projeto exige 17 ou superior.

### No Windows

Os mesmos comandos existem como `.bat`, com o separador de classpath correto:

```bat
compilar.bat
```

```bat
run-servidor.bat
```

```bat
run-cliente.bat
```

No Prompt de Comando, digite o nome do arquivo direto — **sem `./`**, que é
sintaxe de Linux. No PowerShell, use `.\compilar.bat`. Para abrir um terminal já
na pasta certa, digite `cmd` na barra de endereço do Explorador de Arquivos.

Os `run-*.bat` também funcionam por duplo clique: eles usam `java` e não `javaw`,
então a janela de console fica aberta enquanto a aplicação roda — e é nela que as
mensagens JSON aparecem. Já o `compilar.bat` é melhor rodar pelo terminal, para
conseguir ler as mensagens caso a compilação falhe.

A suíte de testes (`testar.sh`) é um script de shell e precisa de Git Bash ou
WSL. As aplicações em si não precisam de nada disso.

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

## Levando o projeto para outra máquina

A pasta inteira pode ser copiada — inclusive entre sistemas operacionais
diferentes. As bibliotecas estão em `lib/` e o banco é embutido, então não há
nada instalado fora do projeto.

O que conferir no destino:

1. **JDK 17 ou superior instalado** (`java -version`).
2. **Recompilar** com `compilar.sh` ou `compilar.bat`. O conteúdo de `bin/` até
   funcionaria como está, já que bytecode é portável, mas recompilar confirma de
   saída que o JDK do destino dá conta.
3. **A pasta `dados/`** pode ser copiada ou deixada para trás: se não existir, o
   banco é recriado do zero na primeira execução.
4. **Firewall**, se o cliente e o servidor ficarem em máquinas diferentes. O
   Windows bloqueia conexões de entrada por padrão e vai pedir liberação na
   primeira vez que o servidor abrir a porta — é preciso permitir, ou nenhum
   cliente de fora consegue conectar.

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

**Os cadastros sobrevivem ao desligamento do servidor; as sessões, não.** Toda
vez que o servidor passa a escutar, as sessões anteriores são encerradas — afinal
todas as conexões caíram junto com ele. Sem isso, quem estava logado antes
receberia `409 Usuario ja possui sessao ativa` ao tentar entrar de novo, sem
saída a não ser esperar os 30 minutos de expiração, já que o token da sessão
anterior se perdeu com o cliente.

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
