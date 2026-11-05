# Sistema Inteligente de Reserva de Salas

Projeto final da disciplina de **Sistemas Distribuídos** — Bacharelado em Ciência
da Computação, UTFPR Campus Ponta Grossa (DAINF). Prof. Dr. Richard Ribeiro.

Aplicação cliente/servidor que se comunica por **troca de mensagens JSON via
sockets TCP**, seguindo o protocolo definido em conjunto pela turma.

## Estado atual

| Entrega | Escopo | Situação |
|---|---|---|
| **EP-1** | Autenticação e CRUD do próprio cadastro | ✅ completa, em conformidade com a v2.6 |
| EP-2 | Operações de administrador e CRUD de salas | ✅ todas as operações do protocolo, servidor e cliente |
| EP-3 | CRUD de reservas e controle de concorrência | pendente |

Operações implementadas, com todos os códigos de status previstos no protocolo:

- **EP-1** — `register`, `login`, `logout`, `read_user`, `update_user`,
  `delete_user`
- **EP-2** — `admin_list_users`, `admin_read_user`, `admin_update_user`,
  `admin_delete_user`, `create_room`, `list_rooms`, `read_room`, `update_room`,
  `delete_room`

O poder de administrador sai de uma **whitelist de tokens** (regra 3.14), e não
do perfil gravado no banco nem de qualquer campo da requisição. Um token entra
nela no login de uma conta `admin` ou quando um admin promove alguém que já está
logado, e sai no rebaixamento, no logout e na remoção da conta — sempre na hora,
sem exigir login novo. Um `role` enviado pelo cliente é ignorado.

Nas salas, **cadastrar, alterar e remover exigem administrador; ler e listar,
não** (regra 3.7) — quem vai reservar precisa ver a sala. O `room_id` é gerado
pelo servidor, nunca escolhido pelo cliente, e a sala nasce `active`.

O cadastro de sala segue o `create_room` do protocolo — `name`, `capacity`,
`location` e `resources`. **Dias da semana e horários de funcionamento da sala
ainda não existem no protocolo** (versão 2.6), e por isso não aparecem aqui;
quando a turma fechar esses campos, eles entram na tabela `salas` e nos dois
formulários. A agenda de quem reservou cada sala vem de `list_reservations`
filtrando por `room_id`, que é operação da EP-3.

No cliente, a aba **Usuários** só existe enquanto a sessão for de
administrador, e o mesmo vale para o grupo de administração dentro da aba
**Salas**. Isso é conveniência de interface, não segurança: esconder um botão
apenas evita oferecer uma ação que o servidor responderia com 403.

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

O cliente de console aceita, além de `raw <json>` (que envia um JSON
arbitrário) e `/sair`:

| Entrega | Comandos |
|---|---|
| EP-1 | `register`, `login`, `read`, `update`, `delete`, `logout` |
| EP-2 · usuários | `users`, `user`, `setuser`, `deluser` |
| EP-2 · salas | `rooms`, `room`, `newroom`, `setroom`, `delroom` |

Os comandos de sala separam os campos por `;`, e não por espaço, porque o nome
e o local têm espaço dentro:

```
newroom Sala de Estudo B12;12;Bloco B - 1 andar;projetor, tv
```

Um `-` no lugar de um campo significa "não alterar" nas atualizações e "sem
filtro" nas listagens. O `resources` é a exceção: ele viaja sempre como lista
completa, então o campo vazio quer dizer "sala sem recurso nenhum".

## Como testar

```bash
./testar.sh
```

Compila tudo, sobe um servidor com banco em memória e roda 401 verificações em
sete suítes: validação de campos, tradução das mensagens, as requisições que o
cliente monta, banco de dados, transporte e erros de protocolo, as quinze
operações já implementadas, e as telas mais a conexão do cliente. No fim,
dispara três clientes simultâneos contra o mesmo servidor.

Os testes de transporte e de operações montam os bytes na mão, **sem usar as
classes do projeto** — reproduzem a situação do teste de interoperabilidade, em
que o outro lado não compartilha código nenhum com o nosso.

A suíte do cliente confere o JSON que a interface **monta**, sem abrir janela:
é esse JSON que o servidor de outro grupo vai receber, então uma chave
obrigatória que falte aparece ali, e não na frente da turma.

### Testador oficial do protocolo

```bash
./conformidade.sh
```

Sobe o servidor e roda contra ele o testador que a turma mantém junto do
protocolo, que confere cada resposta contra a especificação. É o mesmo programa
usado no teste de interoperabilidade.

Depende do Node e do repositório do protocolo clonado ao lado deste; sem um dos
dois, o script avisa e sai. Por isso ele é separado do `testar.sh`, que roda só
com o JDK e as bibliotecas de `lib/`.

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
| e-mail | `admin@admin.com` |
| senha | `admin` |

Ele existe porque todo cadastro feito por `register` nasce com perfil `user`, e
sem um administrador não haveria como exercitar as regras que dependem desse
perfil. Para começar do zero, basta apagar a pasta `dados/`.

As tabelas são `usuarios`, `sessoes`, `salas`, `sala_recursos` e `reservas`. Os
recursos de cada sala ficam em tabela própria, uma linha por recurso, em vez de
concatenados numa coluna: concatenar exigiria escolher um separador, e o
protocolo não define formato para os itens de `resources` — qualquer separador
poderia aparecer dentro de um recurso e partir a lista em duas.

A tabela `reservas` é da EP-3, mas nasce já na EP-2 porque duas respostas desta
entrega dependem dela: o campo `available` do `list_rooms` e o 409 "Sala possui
reservas ativas" do `delete_room`. Sem a tabela, as duas seriam chutes; com ela,
são consultas de verdade que ainda não encontram nada.

**Os cadastros sobrevivem ao desligamento do servidor; as sessões, não.** Toda
vez que o servidor passa a escutar, as sessões anteriores são encerradas — afinal
todas as conexões caíram junto com ele. Sem isso, quem estava logado antes
receberia `409 Usuario ja possui sessao ativa` ao tentar entrar de novo, sem
saída a não ser esperar os 30 minutos de expiração, já que o token da sessão
anterior se perdeu com o cliente.

### Olhando o banco durante a execução

```bash
./run-servidor.sh --console
```

Sobe o servidor normalmente e, junto, o **H2 Console** — a interface web que já
vem dentro de `lib/h2-2.5.250.jar`, sem instalar nada. O terminal imprime o
endereço:

```
Banco: console web em http://localhost:8082
Banco: na tela de login use a URL "jdbc:h2:file:./dados/reservasalas;DB_CLOSE_DELAY=-1", usuario "sa" e senha vazia.
```

Abra o endereço no navegador, cole a URL da segunda linha no campo **JDBC URL**,
deixe `sa` no usuário e a senha em branco, e clique em *Connect*. As tabelas
aparecem na árvore à esquerda, e dá para rodar qualquer consulta — por exemplo:

```sql
SELECT id, usuario, email, role, criado_em FROM usuarios ORDER BY id;
```

E uma para as salas, que junta os recursos de cada uma numa coluna só:

```sql
SELECT s.id, s.nome, s.capacidade, s.localizacao, s.estado,
       COALESCE(LISTAGG(r.recurso, ', ') WITHIN GROUP (ORDER BY r.ordem), '') AS recursos
  FROM salas s LEFT JOIN sala_recursos r ON r.sala_id = s.id
 GROUP BY s.id, s.nome, s.capacidade, s.localizacao, s.estado
 ORDER BY s.id;
```

Funciona com `run-servidor-console.sh` e com os `.bat` do Windows do mesmo jeito.

Dois detalhes que valem saber:

- **Por que o console roda dentro do servidor.** Em modo arquivo embutido o H2
  tranca o arquivo do banco, e um programa de fora (DBeaver, por exemplo)
  receberia o erro 90020 enquanto o servidor estivesse no ar. Rodando na mesma
  JVM, o console usa o banco pelo processo que já o detém e enxerga cada linha no
  instante em que ela é gravada.
- **Ele não se atualiza sozinho.** Os dados são os de verdade, ao vivo, mas a
  tela só muda quando você clica em *Run* outra vez. Depois de cadastrar um
  usuário no cliente, rode a consulta de novo e a linha nova estará lá.

O console escuta apenas em `localhost` e sobe só com `--console`: sem a opção,
nada muda e nenhuma porta extra é aberta.

## Estrutura

```
src/
├── protocolo/     Enquadramento das mensagens, validação de campos e
│                  montagem das respostas. Compartilhado pelos dois lados.
├── servidor/
│   ├── dao/       Acesso ao H2: usuários, sessões e salas
│   ├── modelo/    Entidades
│   └── handlers/  Uma classe por operação do protocolo
├── cliente/       Interface gráfica (abas de cadastro, salas e usuários),
│                  modo console e conexão
└── ui/            Elementos visuais comuns às duas telas

testes/            Suítes executadas pelo testar.sh
lib/               Bibliotecas, versionadas para o projeto rodar sem baixar nada
```

## Protocolo

A especificação é mantida pela turma e hoje vive em um documento AsyncAPI
(`asyncapi.yaml`) no repositório `protocolo-reserva-salas`. Este código está em
conformidade com a **versão 2.6**.

O arquivo `Protocolo de troca de Mensagens.xlsx` neste repositório é o retrato da
planilha original (versão 2.0), em que a EP-1 foi escrita, e fica como
histórico. As diferenças da 2.0 para a 2.6 que afetam a EP-1 são cinco: e-mail
aceita domínio de vários níveis; `delete_user` com senha errada responde 403
"Senha incorreta" sem derrubar a sessão; alterar usuário sem mudar nada responde
400 "Nenhum dado para atualizar"; o login checa e cria a sessão na mesma seção
crítica; e o administrador inicial usa o e-mail `admin@admin.com`, igual em
todos os grupos.

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
