package cliente;

import javax.swing.*;
import java.awt.*;
import java.awt.GraphicsEnvironment;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/**
 * Dirige a ClienteGUI de verdade contra um servidor de verdade: preenche os
 * campos, CLICA nos botoes e confere o que a tela mostra de volta.
 *
 * As outras suites provam que o servidor responde certo e que o cliente monta o
 * JSON certo. Esta prova a unica coisa que faltava: que os botoes estao ligados
 * nas operacoes que dizem estar, e que o resultado chega na tela. Um botao
 * desligado passaria por todas as outras verificacoes sem levantar suspeita.
 *
 * Os nomes dos testes citam as letras dos itens avaliados da EP-2, para a
 * conferencia antes da entrega ser direta.
 *
 * A janela abre FORA da area visivel: ela precisa estar "visible" para os
 * dialogos modais bombearem eventos, mas nao precisa atrapalhar quem esta
 * usando a maquina. Numa maquina sem ambiente grafico a suite inteira e pulada.
 *
 * Uso: java cliente.VerificaTelaDoCliente <porta-de-um-servidor-ja-rodando>
 */
public class VerificaTelaDoCliente {

    static int ok = 0, falhas = 0;
    static final List<String> DIALOGOS = Collections.synchronizedList(new ArrayList<>());
    static ClienteGUI gui;

    /** Sufixo aleatorio, para os cadastros de teste nao colidirem entre si. */
    static final String SUFIXO = sufixo();
    static final String JOAO = "joao" + SUFIXO;
    static final String MARIA = "maria" + SUFIXO;
    static final String JOAO_NOVO = "joaosilva" + SUFIXO;
    static final String NOME_SALA = "Sala Teste " + SUFIXO;
    static final String SALA_RESERVA = "Sala Reserva " + SUFIXO;

    public static void main(String[] args) throws Exception {
        int porta = Integer.parseInt(args[0]);
        if (GraphicsEnvironment.isHeadless()) {
            System.out.println("  PULADO  tela do cliente (maquina sem ambiente grafico)");
            System.out.println("\n===== tela do cliente: pulada =====");
            return;
        }
        criarAlvos(porta);
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        vigiaDeDialogos();

        // A janela do Swing e uma thread NAO-daemon: sem este try/finally, uma
        // excecao no meio do roteiro deixaria a JVM viva para sempre, e a
        // bateria inteira ficaria pendurada em vez de acusar a falha.
        try {
            roteiro(porta);
        } catch (Exception e) {
            falhas++;
            System.out.println("  FALHA roteiro interrompido: " + e);
        } finally {
            try {
                naEdt(gui::dispose);
            } catch (RuntimeException ignorado) {
                // Ja estava indo embora.
            }
        }

        System.out.println("\n===== tela do cliente: " + ok + " passaram, "
                + falhas + " falharam =====");
        System.exit(falhas > 0 ? 1 : 0);
    }

    static void roteiro(int porta) throws Exception {

        SwingUtilities.invokeAndWait(() -> {
            gui = new ClienteGUI();
            // Fora da area visivel: a janela precisa estar "visible" para os
            // dialogos modais bombearem eventos, mas nao precisa atrapalhar
            // quem esta usando a maquina.
            gui.setLocation(-4000, -4000);
            gui.setVisible(true);
        });

        // --- conectar ---
        naEdt(() -> {
            campo("campoIp").setText("127.0.0.1");
            campo("campoPorta").setText(String.valueOf(porta));
        });
        naEdt(() -> botao("Conectar").doClick());
        boolean conectou = esperar(() -> botao("Desconectar").isEnabled());
        confere("conecta no servidor em 127.0.0.1:" + porta, conectou);
        if (!conectou) {
            // Sem servidor nao ha roteiro: seguir so produziria uma cascata de
            // falhas que nao dizem nada sobre a tela.
            throw new IllegalStateException(
                    "a tela nao conectou em 127.0.0.1:" + porta + "; dialogos: " + DIALOGOS);
        }

        // --- login como administrador ---
        naEdt(() -> {
            campo("loginEmail").setText("admin@admin.com");
            ((JPasswordField) campo("loginSenha")).setText("admin");
        });
        limparDialogos();
        clicar(botao("Entrar"));
        // Espera por uma aba que SO existe depois de um login de admin.
        // "Salas" nao serve: ela e montada junto com a janela e esta la antes
        // de qualquer login -- a conferencia passava mesmo com o login
        // falhando, e a culpa caia na asserção seguinte.
        boolean entrou = esperar(() -> nomesDasAbas().contains("Usuários"));
        if (!entrou) {
            System.out.println("  abas: " + nomesDasAbas() + "  dialogos: " + DIALOGOS);
        }
        confere("login do administrador", entrou);
        confere("a aba de reservas aparece na sessao",
                nomesDasAbas().contains("Minhas reservas"));
        confere("a aba de administracao de salas aparece para administrador",
                nomesDasAbas().contains("Administrar salas"));

        // ======================= CLIENTE, item a) =======================
        aba("Usuários");
        JTable tabelaUsuarios = tabelaDe("painelUsuarios");
        limparDialogos();
        clicar(botaoEm("painelUsuarios", "Listar usuários"));
        confere("a) ADM lista TODOS os usuarios",
                esperar(() -> tabelaUsuarios.getRowCount() >= 3));
        confere("a) a listagem traz admin, joao e maria",
                textoDaTabela(tabelaUsuarios).contains("admin")
                        && textoDaTabela(tabelaUsuarios).contains(JOAO)
                        && textoDaTabela(tabelaUsuarios).contains(MARIA));
        confere("a) a listagem nao vaza senha nem hash",
                !textoDaTabela(tabelaUsuarios).toLowerCase().contains("senha")
                        && !textoDaTabela(tabelaUsuarios).contains("$"));

        // --- leitura de um usuario (apoia o item a) ---
        naEdt(() -> campoDe("painelUsuarios", "alvo").setText(JOAO));
        limparDialogos();
        clicar(botaoEm("painelUsuarios", "Ver detalhes"));
        confere("ADM le o cadastro de outro usuario",
                esperar(() -> algumDialogo(JOAO)));

        // ======================= CLIENTE, item b) =======================
        naEdt(() -> {
            campoDe("painelUsuarios", "alvo").setText(JOAO);
            campoDe("painelUsuarios", "novoUsuario").setText(JOAO_NOVO);
        });
        limparDialogos();
        clicar(botaoEm("painelUsuarios", "Salvar alterações"));
        confere("b) ADM atualiza dados de usuario diferente do seu",
                esperar(() -> algumDialogo("atualizados com sucesso")));
        clicar(botaoEm("painelUsuarios", "Listar usuários"));
        confere("b) a mudanca aparece na listagem",
                esperar(() -> textoDaTabela(tabelaUsuarios).contains(JOAO_NOVO)));

        // --- promover, que e o caso dificil da regra 3.14 ---
        naEdt(() -> {
            campoDe("painelUsuarios", "alvo").setText(MARIA);
            comboDe("painelUsuarios", "perfil").setSelectedItem("admin");
        });
        limparDialogos();
        clicar(botaoEm("painelUsuarios", "Salvar alterações"));
        clicar(botaoEm("painelUsuarios", "Listar usuários"));
        confere("b) ADM promove outro usuario a administrador", esperar(() -> {
            String t2 = textoDaTabela(tabelaUsuarios);
            // Dois "administrador": o admin inicial e a maria recem-promovida.
            return t2.indexOf("administrador") != t2.lastIndexOf("administrador");
        }));

        // ======================= CLIENTE, item c) =======================
        naEdt(() -> campoDe("painelUsuarios", "alvo").setText(JOAO_NOVO));
        limparDialogos();
        clicar(botaoEm("painelUsuarios", "Excluir usuário"));
        confere("c) ADM exclui cadastro de usuario comum",
                esperar(() -> algumDialogo("removido com sucesso")));
        clicar(botaoEm("painelUsuarios", "Listar usuários"));
        confere("c) o usuario sai da listagem",
                esperar(() -> !textoDaTabela(tabelaUsuarios).contains(JOAO_NOVO))
                        && tabelaUsuarios.getRowCount() >= 2);

        // ============ CLIENTE, itens d) e) f) g) da EP-2: CRUD de salas ============
        // O formulario abre em janela modal, entao quem o preenche e o vigia
        // de dialogos: a thread do roteiro fica presa no showConfirmDialog.
        aba("Administrar salas");
        JTable tabelaSalas = tabelaDe("painelAdminSalas");

        aoAbrirDialogo("salaNome", d -> {
            escrever(d, "salaNome", NOME_SALA);
            escrever(d, "salaCapacidade", "12");
            escrever(d, "salaLocal", "Bloco B - 1 andar");
            escrever(d, "salaRecursos", "projetor, tv");
            // 2026-12-01 e uma terca (digito 3 na grade da UTFPR).
            marcarSlots(d, "salaHorarios", "3T1, 3T2, 3T3");
        });
        limparDialogos();
        clicar(botaoEm("painelAdminSalas", "Cadastrar sala"));
        confere("d) ADM cadastra uma sala (nome, capacidade e horarios)",
                esperar(() -> algumDialogo("Sala cadastrada com sucesso")));
        confere("d) a sala aparece na tabela",
                esperar(() -> textoDaTabela(tabelaSalas).contains(NOME_SALA)));
        // Espera pela LINHA da nossa sala, e nao pelo texto da tabela num
        // instante: a tabela se recarrega sozinha depois de cada operacao, e
        // ler "agora" pegava ora o estado anterior, ora o seguinte.
        confere("d) a sala nasce ativa, com os recursos e os horarios marcados",
                esperar(() -> {
                    String linha = linhaDe(tabelaSalas, NOME_SALA);
                    return linha != null && linha.contains("ativa")
                            && linha.contains("projetor, tv")
                            && linha.contains("terça 13:00-15:30");
                }));

        // ======================= CLIENTE, item e) =======================
        limparDialogos();
        clicar(botaoEm("painelAdminSalas", "Atualizar lista"));
        confere("e) ADM le os registros das salas",
                esperar(() -> tabelaSalas.getRowCount() >= 1));
        confere("e) a tabela traz capacidade, local e estado",
                esperar(() -> {
                    String linha = linhaDe(tabelaSalas, NOME_SALA);
                    return linha != null && linha.contains("12")
                            && linha.contains("Bloco B") && linha.contains("ativa");
                }));

        // ======================= CLIENTE, item f) =======================
        // Seleciona pelo NOME, e nao pela primeira linha: o servidor desta
        // suite e compartilhado com as outras, que tambem cadastram salas.
        confere("f) a sala de teste esta selecionavel",
                selecionar(tabelaSalas, NOME_SALA));
        aoAbrirDialogo("salaCapacidade", d -> {
            escrever(d, "salaCapacidade", "20");
            Component estado = porNome(d, "salaEstado");
            if (estado instanceof JComboBox<?> combo) {
                combo.setSelectedItem("inactive");
            }
        });
        limparDialogos();
        clicar(botaoEm("painelAdminSalas", "Editar sala"));
        confere("f) ADM atualiza dados da sala",
                esperar(() -> algumDialogo("Sala atualizada com sucesso")));
        confere("f) a capacidade e o estado novos aparecem na tabela",
                esperar(() -> {
                    String linha = linhaDe(tabelaSalas, NOME_SALA);
                    return linha != null && linha.contains("20")
                            && linha.contains("inativa");
                }));

        // ======================= CLIENTE, item g) =======================
        selecionar(tabelaSalas, NOME_SALA);
        limparDialogos();
        clicar(botaoEm("painelAdminSalas", "Excluir sala"));
        confere("g) ADM remove uma sala",
                esperar(() -> algumDialogo("Sala removida com sucesso")));
        confere("g) a sala sai dos registros",
                esperar(() -> !textoDaTabela(tabelaSalas).contains(NOME_SALA)));

        // ============== CLIENTE, EP-3: a grade do dia e as reservas ==============
        // Uma sala propria, criada depois do item g: a dos itens d a g foi
        // removida, e uma reserva nela impediria justamente essa remocao.
        String diaDaReserva = proximaTerca();
        aoAbrirDialogo("salaNome", d -> {
            escrever(d, "salaNome", SALA_RESERVA);
            escrever(d, "salaCapacidade", "12");
            escrever(d, "salaLocal", "Bloco C");
            escrever(d, "salaRecursos", "tv");
            marcarSlots(d, "salaHorarios", "3T1, 3T2, 3T3");
        });
        limparDialogos();
        clicar(botaoEm("painelAdminSalas", "Cadastrar sala"));
        confere("sala para as reservas criada",
                esperar(() -> textoDaTabela(tabelaSalas).contains(SALA_RESERVA)));

        // --- item b) ver o dia: a grade de blocos ---
        aba("Salas");
        naEdt(() -> campoDe("painelSalas", "campoDia").setText(diaDaReserva));
        limparDialogos();
        clicar(botaoEm("painelSalas", "Ver o dia"));
        confere("b) a grade do dia mostra as salas com horario livre",
                esperar(() -> textoDe("painelSalas").contains(SALA_RESERVA)));
        confere("b) e mostra os blocos livres",
                textoDe("painelSalas").contains("livre"));

        // Clicar num bloco livre escolhe o horario -- sem digitar nada.
        confere("b) clicar num bloco livre escolhe sala e horario",
                esperar(() -> clicarNoPrimeiroBlocoLivre("painelSalas"))
                        && esperar(() -> textoDe("painelSalas").contains("13:00-13:50")));

        // --- item j/b) conferir a disponibilidade daquele recorte ---
        limparDialogos();
        clicar(botaoEm("painelSalas", "Conferir disponibilidade"));
        confere("b) conferir a disponibilidade do bloco escolhido",
                esperar(() -> algumDialogo("LIVRE")));

        // --- item d) reservar o bloco escolhido ---
        // O formulario carrega a lista de salas e a agenda do dia pela rede;
        // so da para confirmar depois que a grade aparece com a escolha.
        aoAbrirDialogo("reservaAssunto", (JDialog d) -> {
            if (!grideDesenhada(d)) {
                return false;
            }
            escrever(d, "reservaAssunto", "Reuniao de projeto");
            escrever(d, "reservaParticipantes", "6");
            return true;
        });
        limparDialogos();
        clicar(botaoEm("painelSalas", "Reservar este horário"));
        confere("d) usuario reserva o bloco escolhido na grade",
                esperar(() -> algumDialogo("Reserva realizada com sucesso")));
        confere("d) o bloco deixa de aparecer como livre para outros",
                esperar(() -> textoDe("painelSalas").contains("admin")));

        // --- item c) as minhas reservas ---
        aba("Minhas reservas");
        JTable tabelaReservas = tabelaDe("painelReservas");
        limparDialogos();
        clicar(botaoEm("painelReservas", "Atualizar lista"));
        confere("c) usuario recupera suas reservas",
                esperar(() -> textoDaTabela(tabelaReservas).contains(SALA_RESERVA)));
        confere("c) a lista traz sala, dia, horario e assunto",
                textoDaTabela(tabelaReservas).contains(diaDaReserva)
                        && textoDaTabela(tabelaReservas).contains("13:00")
                        && textoDaTabela(tabelaReservas).contains("Reuniao de projeto"));

        confere("c) a reserva esta selecionavel",
                selecionar(tabelaReservas, SALA_RESERVA));
        limparDialogos();
        clicar(botaoEm("painelReservas", "Ver detalhes"));
        confere("c) os detalhes da reserva abrem pelo id",
                esperar(() -> algumDialogo("Reuniao de projeto")));

        // --- item e) alterar a reserva ---
        confere("e) a reserva a alterar esta selecionada",
                selecionar(tabelaReservas, SALA_RESERVA));
        aoAbrirDialogo("reservaAssunto", (JDialog d) -> {
            if (!grideDesenhada(d)) {
                return false;
            }
            escrever(d, "reservaAssunto", "Reuniao remarcada");
            escrever(d, "reservaParticipantes", "8");
            return true;
        });
        limparDialogos();
        clicar(botaoEm("painelReservas", "Alterar reserva"));
        confere("e) usuario atualiza o topico e os participantes",
                esperar(() -> algumDialogo("Reserva atualizada com sucesso")));
        confere("e) os dados novos aparecem na lista",
                esperar(() -> textoDaTabela(tabelaReservas).contains("Reuniao remarcada")
                        && textoDaTabela(tabelaReservas).contains("8")));

        // Reservar no passado: a tela avisa antes de mandar, porque o servidor
        // responderia "Dados da reserva em formato invalido" (regra 9.3).
        aoAbrirDialogo("reservaAgenda", (JDialog d) -> {
            // Põe o dia passado e manda a agenda recarregar; só confirma
            // quando a grade daquele dia estiver desenhada.
            // Uma terca que ja passou: a sala de teste so funciona as tercas,
            // e num dia em que ela nao abre a agenda viria vazia -- sem bloco
            // nenhum, nao haveria o que esperar.
            aplicarData(d, "reservaData", tercaPassada());
            if (!grideDesenhada(d)) {
                return false;
            }
            escrever(d, "reservaAssunto", "Reuniao no passado");
            escrever(d, "reservaParticipantes", "2");
            return true;
        });
        limparDialogos();
        clicar(botaoEm("painelReservas", "Nova reserva"));
        confere("d) reservar no passado diz que o dia passou",
                esperar(() -> algumDialogo("já passou")));
        confere("d) e nao fala em \"dados invalidos\"",
                !algumDialogo("formato inv"));

        // --- item f) cancelar a reserva ---
        confere("f) a reserva a cancelar esta selecionada",
                selecionar(tabelaReservas, SALA_RESERVA));
        limparDialogos();
        clicar(botaoEm("painelReservas", "Cancelar reserva"));
        confere("f) usuario cancela uma reserva ativa",
                esperar(() -> algumDialogo("Reserva cancelada com sucesso")));
        confere("f) a reserva sai da lista",
                esperar(() -> !textoDaTabela(tabelaReservas).contains(SALA_RESERVA)));

        // Limpeza: sem reserva ativa, a sala volta a poder ser removida.
        aba("Administrar salas");
        limparDialogos();
        clicar(botaoEm("painelAdminSalas", "Atualizar lista"));
        esperar(() -> textoDaTabela(tabelaSalas).contains(SALA_RESERVA));
        selecionar(tabelaSalas, SALA_RESERVA);
        limparDialogos();
        clicar(botaoEm("painelAdminSalas", "Excluir sala"));
        confere("a sala das reservas e removida no fim",
                esperar(() -> !textoDaTabela(tabelaSalas).contains(SALA_RESERVA)));

        // ======== queda da conexao e sessao presa (regras 1.9, 1.11 e 3.5) ========
        // O servidor fecha o socket com 300 s de inatividade, mas a sessao
        // continua viva por ate 30 minutos. Fechar a conexao aqui reproduz
        // exatamente o estado em que a proxima acao a encontra.
        aba("Minhas reservas");
        naEdt(() -> ((Conexao) ler("conexao")).fechar());
        limparDialogos();
        clicar(botaoEm("painelReservas", "Atualizar lista"));
        confere("a acao depois da queda funciona, sem pedir login de novo",
                esperar(() -> !algumDialogo("Falha na comunicação")
                        && !algumDialogo("Conecte-se ao servidor")));
        confere("e a sessao continua aberta (as abas nao sumiram)",
                nomesDasAbas().contains("Minhas reservas"));

        // --- logout, fora das abas ---
        limparDialogos();
        clicar(botao("Sair (logout)"));
        confere("logout encerra a sessao",
                esperar(() -> algumDialogo("Logout realizado com sucesso")));

        // --- "Desconectar" encerra a sessao antes de fechar (regra 3.5) ---
        // Entra de novo (a conexao ja esta de pe, entao nao ha o que
        // reconectar) e desconecta pelo botao.
        naEdt(() -> {
            campo("loginEmail").setText("admin@admin.com");
            ((JPasswordField) campo("loginSenha")).setText("admin");
        });
        limparDialogos();
        clicar(botao("Entrar"));
        confere("entra de novo para testar o Desconectar",
                esperar(() -> nomesDasAbas().contains("Salas")));

        limparDialogos();
        clicar(botao("Desconectar"));
        confere("desconectar volta para a tela de acesso",
                esperar(() -> botao("Conectar").isEnabled()));

        // A prova e por socket cru, e nao pela tela: se a sessao tivesse
        // ficado de pe, este login responderia 409 por ate 30 minutos. E o
        // socket cru nao depende de botao nenhum ficar habilitado na hora
        // certa -- a tela ja foi conferida acima.
        confere("a conta ficou livre: o login seguinte entra na hora",
                loginCruFunciona(porta));

    }

    /**
     * Cria por socket cru os dois cadastros que servirao de alvo.
     *
     * Pela tela seria possivel, mas exigiria entrar e sair de sessao duas vezes
     * antes de comecar -- e o que esta suite quer exercitar sao as operacoes de
     * administrador, nao o cadastro, que as outras ja cobrem.
     */
    /** O valor de uma coluna na linha selecionada da tabela. */
    static String valorSelecionado(JTable tabela, int coluna) {
        String[] valor = new String[1];
        naEdt(() -> {
            int linha = tabela.getSelectedRow();
            valor[0] = linha < 0 ? null
                    : String.valueOf(tabela.getModel().getValueAt(linha, coluna));
        });
        return valor[0];
    }

    /**
     * A proxima terca-feira, que e o dia em que a sala de teste funciona.
     *
     * Calculada, e nao escrita a mao: uma data fixa vira passado depois de
     * alguns meses, e a reserva passaria a ser recusada pela regra 9.3 -- a
     * suite falharia sozinha, culpando a tela pela passagem do tempo.
     */
    /** Uma terca-feira que ja passou, para o teste do horario no passado. */
    static String tercaPassada() {
        java.time.LocalDate dia = java.time.LocalDate.now().minusDays(1);
        while (dia.getDayOfWeek() != java.time.DayOfWeek.TUESDAY) {
            dia = dia.minusDays(1);
        }
        return dia.toString();
    }

    static String proximaTerca() {
        java.time.LocalDate dia = java.time.LocalDate.now().plusDays(1);
        while (dia.getDayOfWeek() != java.time.DayOfWeek.TUESDAY) {
            dia = dia.plusDays(1);
        }
        return dia.toString();
    }

    /**
     * Faz login e logout por socket cru e diz se o login foi aceito.
     *
     * Serve para provar que a sessao anterior nao ficou presa: com ela de pe,
     * o servidor responderia 409 "Usuario ja possui sessao ativa".
     */
    static boolean loginCruFunciona(int porta) {
        try (java.net.Socket s = new java.net.Socket("127.0.0.1", porta)) {
            s.setSoTimeout(5000);
            var saida = s.getOutputStream();
            var entrada = s.getInputStream();
            saida.write(("{\"op\":\"login\",\"email\":\"admin@admin.com\","
                    + "\"password\":\"admin\"}\n").getBytes("UTF-8"));
            saida.flush();
            String resposta = lerLinha(entrada);
            if (resposta == null || !resposta.contains("\"status\":\"200\"")) {
                return false;
            }
            // Devolve a conta para as proximas suites.
            String token = resposta.replaceAll(".*\"token\":\"([a-f0-9]{64})\".*", "$1");
            saida.write(("{\"op\":\"logout\",\"token\":\"" + token + "\"}\n")
                    .getBytes("UTF-8"));
            saida.flush();
            lerLinha(entrada);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    static String lerLinha(java.io.InputStream entrada) throws Exception {
        java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
        int b;
        while ((b = entrada.read()) != -1 && b != '\n') {
            buf.write(b);
        }
        return buf.size() == 0 ? null : buf.toString("UTF-8");
    }

    static void criarAlvos(int porta) throws Exception {
        try (java.net.Socket s = new java.net.Socket("127.0.0.1", porta)) {
            s.setSoTimeout(5000);
            var saida = s.getOutputStream();
            var entrada = s.getInputStream();
            for (String user : new String[] {JOAO, MARIA}) {
                String json = "{\"op\":\"register\",\"email\":\"" + user
                        + "@email.com\",\"user\":\"" + user
                        + "\",\"password\":\"senha123\"}\n";
                saida.write(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                saida.flush();
                int b;
                while ((b = entrada.read()) != -1 && b != '\n') {
                    // consome a resposta ate o fim da linha
                }
            }
        }
    }

    /** Dez letras minusculas: o 'user' do protocolo nao aceita numero. */
    static String sufixo() {
        java.security.SecureRandom rnd = new java.security.SecureRandom();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            sb.append((char) ('a' + rnd.nextInt(26)));
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ apoio

    /** Fecha os dialogos modais que a tela abre, guardando o texto antes. */
    /**
     * O que fazer com o proximo dialogo antes de confirma-lo.
     *
     * Os formularios de sala e de reserva abrem em janela modal. O vigia
     * confirma qualquer dialogo que apareca, entao para TESTAR um formulario
     * e preciso preenche-lo no instante em que ele surge -- e so o vigia
     * chega la, porque a thread do roteiro fica presa no showConfirmDialog.
     */
    static final java.util.concurrent.atomic.AtomicReference<Object[]> AO_ABRIR =
            new java.util.concurrent.atomic.AtomicReference<>();

    /** Quantas vezes o preenchedor pendente ja pediu para esperar. */
    static final java.util.concurrent.atomic.AtomicInteger TENTATIVAS =
            new java.util.concurrent.atomic.AtomicInteger();

    /**
     * Registra o preenchimento do proximo formulario modal.
     *
     * @param campoChave nome de um campo que SO existe nesse formulario. O
     *                   preenchimento espera por ele: um dialogo de erro ou de
     *                   confirmacao que apareca no meio do caminho nao consome
     *                   a acao, e o formulario certo continua sendo preenchido
     *                   quando abrir. Sem isso o teste ficava intermitente --
     *                   um aviso inesperado roubava o preenchedor, o
     *                   formulario abria vazio e tudo o que vinha depois
     *                   falhava por uma causa que nao era a testada.
     */
    static void aoAbrirDialogo(String campoChave, java.util.function.Consumer<JDialog> acao) {
        aoAbrirDialogo(campoChave, d -> {
            acao.accept(d);
            return true;
        });
    }

    /**
     * Versao que pode ESPERAR: devolve false enquanto o dialogo ainda nao
     * esta pronto para ser confirmado.
     *
     * O formulario de reserva carrega a lista de salas e a agenda do dia pela
     * REDE, depois de abrir. Confirmar na primeira passada do vigia pegaria o
     * formulario vazio. Devolvendo false, o vigia deixa o dialogo de pe e
     * tenta de novo 60 ms depois -- que e o tempo de a resposta chegar e a
     * grade se desenhar.
     */
    static void aoAbrirDialogo(String campoChave, java.util.function.Predicate<JDialog> acao) {
        TENTATIVAS.set(0);
        AO_ABRIR.set(new Object[] {campoChave, acao});
    }

    static void vigiaDeDialogos() {
        Thread t = new Thread(() -> {
            while (true) {
                SwingUtilities.invokeLater(() -> {
                    for (Window w : Window.getWindows()) {
                        if (w instanceof JDialog d && d.isVisible()) {
                            Object[] pendente = AO_ABRIR.get();
                            if (pendente != null && porNome(d, (String) pendente[0]) != null) {
                                @SuppressWarnings("unchecked")
                                java.util.function.Predicate<JDialog> preencher =
                                        (java.util.function.Predicate<JDialog>) pendente[1];
                                // O limite evita a espera infinita: se o que
                                // o preenchedor espera nunca chegar, o teste
                                // falha pela conferencia -- com os dialogos no
                                // relatorio --, e nao travando a suite inteira.
                                if (!preencher.test(d) && TENTATIVAS.incrementAndGet() < 100) {
                                    continue;
                                }
                                TENTATIVAS.set(0);
                                AO_ABRIR.set(null);
                            }
                            DIALOGOS.add(textosDe(d));
                            JButton confirmar = acharBotao(d, "OK", "Sim", "Yes");
                            if (confirmar != null) {
                                confirmar.doClick();
                            } else {
                                d.dispose();
                            }
                        }
                    }
                });
                try {
                    Thread.sleep(60);
                } catch (InterruptedException e) {
                    return;
                }
            }
        });
        t.setDaemon(true);
        t.start();
    }

    /**
     * O texto da linha cuja segunda coluna contem o alvo, ou null.
     *
     * Olhar a LINHA, e nao a tabela inteira, evita dois enganos: confundir a
     * nossa sala com a de outra suite, e dar por boa uma tabela em que os
     * pedacos procurados estao em linhas diferentes.
     */
    static String linhaDe(JTable tabela, String alvo) {
        String[] achada = {null};
        naEdt(() -> {
            for (int i = 0; i < tabela.getRowCount(); i++) {
                StringBuilder linha = new StringBuilder();
                boolean ehEssa = false;
                for (int j = 0; j < tabela.getColumnCount(); j++) {
                    Object v = tabela.getValueAt(i, j);
                    String texto = v == null ? "" : v.toString();
                    if (texto.contains(alvo)) {
                        ehEssa = true;
                    }
                    linha.append(texto).append(" | ");
                }
                if (ehEssa) {
                    achada[0] = linha.toString();
                    return;
                }
            }
        });
        return achada[0];
    }

    /** Todo o texto visivel de um painel da janela -- rotulos e botoes. */
    static String textoDe(String painel) {
        StringBuilder sb = new StringBuilder();
        naEdt(() -> sb.append(textosDe((Container) ler(painel))));
        return sb.toString();
    }

    /**
     * Clica no primeiro bloco "livre" da grade do dia.
     *
     * A grade e feita de botoes, e nao de linhas de tabela: escolher um
     * horario e clicar num deles, que e exatamente o que o usuario faz.
     */
    static boolean clicarNoPrimeiroBlocoLivre(String painel) {
        boolean[] clicou = {false};
        naEdt(() -> {
            for (Component c : todosOsComponentes((Container) ler(painel))) {
                if (c instanceof JButton b && "livre".equals(b.getText()) && b.isEnabled()) {
                    b.doClick();
                    clicou[0] = true;
                    return;
                }
            }
        });
        return clicou[0];
    }

    static List<Component> todosOsComponentes(Container c) {
        List<Component> saida = new ArrayList<>();
        for (Component x : c.getComponents()) {
            saida.add(x);
            if (x instanceof Container k) {
                saida.addAll(todosOsComponentes(k));
            }
        }
        return saida;
    }

    /** Acha um componente pelo nome dentro de um dialogo (ou de qualquer painel). */
    static Component porNome(Container c, String nome) {
        for (Component x : c.getComponents()) {
            if (nome.equals(x.getName())) {
                return x;
            }
            if (x instanceof Container k) {
                Component achado = porNome(k, nome);
                if (achado != null) {
                    return achado;
                }
            }
        }
        return null;
    }

    /** Escreve num campo de texto do dialogo. */
    static void escrever(JDialog d, String nome, String valor) {
        Component c = porNome(d, nome);
        if (c instanceof JTextField campo) {
            campo.setText(valor);
        }
    }

    /** Escolhe um horario numa lista do dialogo. */
    static void escolherHora(JDialog d, String nome, String hora) {
        Component c = porNome(d, nome);
        if (c instanceof cliente.ComboDeHorario combo) {
            combo.selecionarHora(hora);
        }
    }

    /** Clica no primeiro bloco "livre" da grade dentro do dialogo. */
    static boolean clicarBlocoLivre(JDialog d) {
        for (Component c : todosOsComponentes(d)) {
            if (c instanceof JButton b && "livre".equals(b.getText()) && b.isEnabled()) {
                b.doClick();
                return true;
            }
        }
        return false;
    }

    /** Diz se a grade do dialogo ja desenhou algum bloco. */
    static boolean grideDesenhada(JDialog d) {
        for (Component c : todosOsComponentes(d)) {
            if (c instanceof JButton b && b.getText() != null
                    && (b.getText().equals("livre") || b.getText().equals("já passou")
                            || b.getText().equals("—") || b.getText().equals("escolhido"))) {
                return true;
            }
        }
        return false;
    }

    /** Manda o campo de data avisar que mudou, para a agenda recarregar. */
    static void aplicarData(JDialog d, String nome, String valor) {
        Component c = porNome(d, nome);
        if (c instanceof JTextField campo && !valor.equals(campo.getText())) {
            campo.setText(valor);
            campo.postActionEvent();
        }
    }

    /** Marca horarios na grade de blocos do dialogo. */
    static void marcarSlots(JDialog d, String nome, String codigos) {
        Component c = porNome(d, nome);
        if (c instanceof cliente.GradeDeSlots grade) {
            grade.selecionar(codigos);
        }
    }

    static String textosDe(Container c) {
        StringBuilder sb = new StringBuilder();
        for (Component x : c.getComponents()) {
            if (x instanceof JLabel l && l.getText() != null) {
                sb.append(l.getText()).append(' ');
            }
            // Os blocos da grade do dia sao botoes: sem este ramo, a grade
            // seria invisivel para o teste.
            if (x instanceof javax.swing.AbstractButton b && b.getText() != null) {
                sb.append(b.getText()).append(' ');
            }
            // Diálogo com área de rolagem -- o do "dia de todas as salas" --
            // guarda o texto numa JTextArea, e não numa JLabel. Sem este ramo
            // o vigia veria uma janela vazia e o teste culparia a tela por um
            // limite do próprio teste.
            if (x instanceof javax.swing.JTextArea a && a.getText() != null) {
                sb.append(a.getText()).append(' ');
            }
            if (x instanceof Container k) {
                sb.append(textosDe(k));
            }
        }
        return sb.toString();
    }

    static JButton acharBotao(Container c, String... textos) {
        for (Component x : c.getComponents()) {
            if (x instanceof JButton b) {
                for (String t : textos) {
                    if (t.equalsIgnoreCase(b.getText())) {
                        return b;
                    }
                }
            }
            if (x instanceof Container k) {
                JButton r = acharBotao(k, textos);
                if (r != null) {
                    return r;
                }
            }
        }
        return null;
    }

    static void limparDialogos() {
        DIALOGOS.clear();
    }

    /**
     * Se ALGUM dialogo desta acao trouxe o texto.
     *
     * Nao basta olhar o ultimo: uma acao pode abrir mais de um aviso em
     * sequencia, e qual deles fica por ultimo depende do tempo de resposta do
     * servidor. Perguntar "apareceu?" e estavel; "foi o ultimo?" nao e.
     */
    static boolean algumDialogo(String texto) {
        synchronized (DIALOGOS) {
            for (String d : DIALOGOS) {
                if (d.contains(texto)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * O botao com esse texto DENTRO de um painel.
     *
     * Precisa existir porque "Salvar alterações" aparece duas vezes na janela
     * -- uma em "Meu cadastro" e outra em "Usuários". Procurando na janela
     * inteira, o teste clicava sempre no primeiro e achava que tinha clicado no
     * outro.
     */
    static JButton botaoEm(String painel, String texto) {
        AtomicReference<JButton> r = new AtomicReference<>();
        naEdt(() -> r.set(acharBotao((Container) ler(painel), texto)));
        if (r.get() == null) {
            throw new IllegalStateException("botao nao encontrado em " + painel + ": " + texto);
        }
        return r.get();
    }

    static JButton botao(String texto) {
        AtomicReference<JButton> r = new AtomicReference<>();
        naEdt(() -> r.set(acharBotao(gui.getContentPane(), texto)));
        if (r.get() == null) {
            throw new IllegalStateException("botao nao encontrado: " + texto);
        }
        return r.get();
    }

    /**
     * Clica e espera a tela terminar de reagir.
     *
     * Tres esperas, e cada uma corrige um jeito diferente de o teste mentir:
     *
     *  1. o botao precisa estar HABILITADO. A tela desabilita os botoes
     *     enquanto uma requisicao esta no ar, e doClick() num botao desabilitado
     *     nao faz nada -- o teste seguiria achando que clicou;
     *  2. o dialogo modal precisa ter sido fechado. Ele bloqueia a EDT, entao o
     *     que vem depois dele (limpar o formulario, recarregar a lista) so roda
     *     quando ele sai -- e o teste preencheria os campos seguintes antes
     *     disso, para ve-los apagados logo em seguida;
     *  3. o botao precisa estar habilitado de novo, que e o sinal de que a
     *     troca terminou de verdade.
     */
    static void clicar(JButton b) {
        if (!esperar(b::isEnabled)) {
            throw new IllegalStateException("o botao \"" + b.getText()
                    + "\" continuou desabilitado");
        }
        naEdt(b::doClick);
        esperar(() -> !temDialogoVisivel());
        esperar(b::isEnabled);
        // Um respiro para o invokeLater que reabilita os botoes e recarrega as
        // listas acabar de rodar.
        dormir(150);
    }

    static boolean temDialogoVisivel() {
        AtomicReference<Boolean> r = new AtomicReference<>(false);
        naEdt(() -> {
            for (Window w : Window.getWindows()) {
                if (w instanceof JDialog d && d.isVisible()) {
                    r.set(true);
                }
            }
        });
        return r.get();
    }

    static void dormir(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    static void aba(String nome) {
        naEdt(() -> {
            JTabbedPane abas = (JTabbedPane) ler("abasSessao");
            for (int i = 0; i < abas.getTabCount(); i++) {
                if (abas.getTitleAt(i).trim().equals(nome)) {
                    abas.setSelectedIndex(i);
                    return;
                }
            }
        });
    }

    static List<String> nomesDasAbas() {
        List<String> nomes = new ArrayList<>();
        naEdt(() -> {
            JTabbedPane abas = (JTabbedPane) ler("abasSessao");
            for (int i = 0; i < abas.getTabCount(); i++) {
                nomes.add(abas.getTitleAt(i).trim());
            }
        });
        return nomes;
    }

    /**
     * Seleciona a linha cuja primeira coluna de texto contem o alvo.
     *
     * Clicar na linha 0 seria mais curto e estaria errado: o servidor desta
     * suite atende as outras verificacoes tambem, e elas deixam salas
     * cadastradas. A linha 0 pode nao ser a nossa.
     */
    static boolean selecionar(JTable tabela, String alvo) {
        AtomicReference<Boolean> achou = new AtomicReference<>(false);
        naEdt(() -> {
            for (int l = 0; l < tabela.getRowCount(); l++) {
                for (int c = 0; c < tabela.getColumnCount(); c++) {
                    Object v = tabela.getValueAt(l, c);
                    if (v != null && v.toString().contains(alvo)) {
                        tabela.setRowSelectionInterval(l, l);
                        achou.set(true);
                        return;
                    }
                }
            }
        });
        return achou.get();
    }

    static String textoDaTabela(JTable t) {
        StringBuilder sb = new StringBuilder();
        naEdt(() -> {
            for (int l = 0; l < t.getRowCount(); l++) {
                for (int c = 0; c < t.getColumnCount(); c++) {
                    sb.append(t.getValueAt(l, c)).append(" | ");
                }
                sb.append('\n');
            }
        });
        return sb.toString();
    }

    static JTable tabelaDe(String painel) {
        AtomicReference<JTable> r = new AtomicReference<>();
        naEdt(() -> r.set((JTable) campoDeObjeto(ler(painel), "tabela")));
        return r.get();
    }

    static JTextField campo(String nome) {
        return (JTextField) ler(nome);
    }

    static JTextField campoDe(String painel, String campo) {
        return (JTextField) campoDeObjeto(ler(painel), campo);
    }

    /** Lista de escolha de horario: "13:00" em vez de um campo de texto. */
    static cliente.ComboDeHorario hora(String painel, String campo) {
        return (cliente.ComboDeHorario) campoDeObjeto(ler(painel), campo);
    }

    /** Lista de escolha dos horarios de funcionamento da sala. */
    static cliente.GradeDeSlots slots(String painel, String campo) {
        return (cliente.GradeDeSlots) campoDeObjeto(ler(painel), campo);
    }

    @SuppressWarnings("unchecked")
    static JComboBox<String> comboDe(String painel, String campo) {
        return (JComboBox<String>) campoDeObjeto(ler(painel), campo);
    }

    static Object ler(String nome) {
        return campoDeObjeto(gui, nome);
    }

    static Object campoDeObjeto(Object alvo, String nome) {
        try {
            Field f = alvo.getClass().getDeclaredField(nome);
            f.setAccessible(true);
            return f.get(alvo);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("campo " + nome + ": " + e, e);
        }
    }

    static void naEdt(Runnable r) {
        try {
            if (SwingUtilities.isEventDispatchThread()) {
                r.run();
            } else {
                SwingUtilities.invokeAndWait(r);
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Espera a condicao virar verdadeira, ate 6 segundos. */
    static boolean esperar(BooleanSupplier condicao) {
        for (int i = 0; i < 120; i++) {
            AtomicReference<Boolean> r = new AtomicReference<>(false);
            naEdt(() -> {
                try {
                    r.set(condicao.getAsBoolean());
                } catch (RuntimeException e) {
                    r.set(false);
                }
            });
            if (r.get()) {
                return true;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                return false;
            }
        }
        return false;
    }

    static void confere(String nome, boolean passou) {
        if (passou) { ok++; System.out.println("  OK    " + nome); }
        else {
            falhas++;
            System.out.println("  FALHA " + nome);
            System.out.println("         dialogos: " + DIALOGOS);
        }
    }
}
