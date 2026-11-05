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
        confere("login do administrador", esperar(() -> {
            JTabbedPane abas = (JTabbedPane) ler("abasSessao");
            return abas.getTabCount() == 3;
        }));
        confere("a aba Usuarios aparece para administrador",
                nomesDasAbas().contains("Usuários"));

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

        // ======================= CLIENTE, item d) =======================
        aba("Salas");
        JTable tabelaSalas = tabelaDe("painelSalas");
        naEdt(() -> {
            campoDe("painelSalas", "salaNome").setText(NOME_SALA);
            campoDe("painelSalas", "salaCapacidade").setText("12");
            campoDe("painelSalas", "salaLocal").setText("Bloco B - 1 andar");
            campoDe("painelSalas", "salaRecursos").setText("projetor, tv");
            // 2026-12-01 e uma terca (digito 3 na grade da UTFPR).
            campoDe("painelSalas", "salaHorarios").setText("3T1, 3T2, 3T3");
        });
        limparDialogos();
        clicar(botaoEm("painelSalas", "Cadastrar nova sala"));
        confere("d) ADM cadastra uma sala (nome e capacidade)",
                esperar(() -> algumDialogo("Sala cadastrada com sucesso")));
        confere("d) a sala aparece na tabela",
                esperar(() -> textoDaTabela(tabelaSalas).contains(NOME_SALA)));
        confere("d) a sala nasce ativa e com os recursos",
                textoDaTabela(tabelaSalas).contains("ativa")
                        && textoDaTabela(tabelaSalas).contains("projetor, tv"));

        // ======================= CLIENTE, item e) =======================
        limparDialogos();
        clicar(botaoEm("painelSalas", "Listar salas"));
        confere("e) ADM le os registros das salas",
                esperar(() -> tabelaSalas.getRowCount() >= 1));
        confere("e) a tabela traz capacidade e status",
                textoDaTabela(tabelaSalas).contains("12")
                        && textoDaTabela(tabelaSalas).contains("ativa"));
        confere("e) sem horario, a disponibilidade vem como travessao",
                textoDaTabela(tabelaSalas).contains("—"));

        naEdt(() -> {
            campoDe("painelSalas", "filtroData").setText("2026-12-01");
            campoDe("painelSalas", "filtroInicio").setText("13:00");
            campoDe("painelSalas", "filtroFim").setText("14:40");
        });
        limparDialogos();
        clicar(botaoEm("painelSalas", "Listar salas"));
        confere("e) com data e horario, mostra livre ou ocupada",
                esperar(() -> textoDaTabela(tabelaSalas).contains("livre")));

        // Seleciona pelo NOME, e nao pela primeira linha: o servidor desta
        // suite e compartilhado com as outras, que tambem cadastram salas.
        confere("e) a sala de teste esta na tabela", selecionar(tabelaSalas, NOME_SALA));
        limparDialogos();
        clicar(botaoEm("painelSalas", "Ver detalhes da sala"));
        confere("e) ADM abre os detalhes de uma sala",
                esperar(() -> algumDialogo(NOME_SALA)
                        && algumDialogo("12 lugares")));

        // ======================= CLIENTE, item f) =======================
        selecionar(tabelaSalas, NOME_SALA);
        naEdt(() -> {
            campoDe("painelSalas", "salaCapacidade").setText("20");
            comboDe("painelSalas", "salaEstado").setSelectedItem("inactive");
        });
        limparDialogos();
        clicar(botaoEm("painelSalas", "Salvar na sala selecionada"));
        confere("f) ADM atualiza dados da sala",
                esperar(() -> algumDialogo("Sala atualizada com sucesso")));
        confere("f) a capacidade e o status novos aparecem na tabela",
                esperar(() -> textoDaTabela(tabelaSalas).contains("20")
                        && textoDaTabela(tabelaSalas).contains("inativa")));

        // ======================= CLIENTE, item g) =======================
        selecionar(tabelaSalas, NOME_SALA);
        limparDialogos();
        clicar(botaoEm("painelSalas", "Excluir sala"));
        confere("g) ADM remove uma sala",
                esperar(() -> algumDialogo("Sala removida com sucesso")));
        confere("g) a sala sai dos registros",
                esperar(() -> !textoDaTabela(tabelaSalas).contains(NOME_SALA)));

        // --- logout, fora das abas ---
        limparDialogos();
        clicar(botao("Sair (logout)"));
        confere("logout encerra a sessao",
                esperar(() -> algumDialogo("Logout realizado com sucesso")));

    }

    /**
     * Cria por socket cru os dois cadastros que servirao de alvo.
     *
     * Pela tela seria possivel, mas exigiria entrar e sair de sessao duas vezes
     * antes de comecar -- e o que esta suite quer exercitar sao as operacoes de
     * administrador, nao o cadastro, que as outras ja cobrem.
     */
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
    static void vigiaDeDialogos() {
        Thread t = new Thread(() -> {
            while (true) {
                SwingUtilities.invokeLater(() -> {
                    for (Window w : Window.getWindows()) {
                        if (w instanceof JDialog d && d.isVisible()) {
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

    static String textosDe(Container c) {
        StringBuilder sb = new StringBuilder();
        for (Component x : c.getComponents()) {
            if (x instanceof JLabel l && l.getText() != null) {
                sb.append(l.getText()).append(' ');
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
