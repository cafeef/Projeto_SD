package cliente;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Interface grafica do cliente.
 *
 * Atende a obs. 5 da grade -- um campo para o IP e outro para a porta. As
 * mensagens JSON trocadas (obs. 3) saem no TERMINAL que executa o cliente.
 *
 * Duas telas, trocadas por CardLayout:
 *
 *   ACESSO  -- abas de entrar (login) e cadastrar (register)
 *   SESSAO  -- meus dados (read_user), alterar (update_user),
 *              excluir (delete_user) e sair (logout)
 *
 * O token fica so em memoria (regra 3.2) e e descartado em todo 401, que e a
 * reacao que a aba "Codigos de Status" manda ter.
 *
 * Nenhuma operacao de rede roda na EDT: toda troca vai para um executor de uma
 * thread so, e o retorno volta para a tela via invokeLater.
 */
public final class ClienteGUI extends JFrame {

    private static final long serialVersionUID = 1L;

    private static final Gson GSON = new Gson();
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm:ss");

    private static final String CARTAO_ACESSO = "acesso";
    private static final String CARTAO_SESSAO = "sessao";

    // --- conexao ---
    private final JTextField campoIp = new JTextField("127.0.0.1", 12);
    private final JTextField campoPorta = new JTextField("5000", 6);
    private final JButton botaoConectar = new JButton("Conectar");
    private final JButton botaoDesconectar = new JButton("Desconectar");
    private final JLabel rotuloStatus = new JLabel("Desconectado");

    // --- login ---
    private final JTextField loginEmail = new JTextField(20);
    private final JPasswordField loginSenha = new JPasswordField(20);
    private final JButton botaoEntrar = new JButton("Entrar");

    // --- cadastro ---
    private final JTextField cadEmail = new JTextField(20);
    private final JTextField cadUsuario = new JTextField(20);
    private final JPasswordField cadSenha = new JPasswordField(20);
    private final JButton botaoCadastrar = new JButton("Cadastrar");

    // --- sessao ---
    private final JLabel dadosUsuario = new JLabel("-");
    private final JLabel dadosEmail = new JLabel("-");
    private final JLabel dadosRole = new JLabel("-");
    private final JLabel dadosCriadoEm = new JLabel("-");
    private final JTextField novoUsuario = new JTextField(16);
    private final JPasswordField novaSenha = new JPasswordField(16);
    private final JButton botaoRecarregar = new JButton("Recarregar");
    private final JButton botaoSalvar = new JButton("Salvar alteracoes");
    private final JButton botaoExcluir = new JButton("Excluir cadastro");
    private final JButton botaoSair = new JButton("Sair (logout)");

    private final CardLayout cartas = new CardLayout();
    private final JPanel painelCartas = new JPanel(cartas);
    private final JTabbedPane abasAcesso = new JTabbedPane();

    private final transient Conexao conexao = new Conexao();
    private final transient ExecutorService rede = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "rede-cliente");
        t.setDaemon(true);
        return t;
    });

    /** Token da sessao. Vive so aqui, em memoria (regra 3.2). */
    private transient String token;

    public ClienteGUI() {
        super("Cliente - Reserva de Salas");
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        montarTela();
        ligarAcoes();
        habilitarOperacoes(false);
        botaoDesconectar.setEnabled(false);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                conexao.fechar();
                rede.shutdownNow();
                dispose();
                System.exit(0);
            }
        });

        setSize(660, 520);
        setLocationRelativeTo(null);
    }

    // ------------------------------------------------------------------ layout

    private void montarTela() {
        JPanel linhaConexao = new JPanel();
        linhaConexao.setLayout(new BoxLayout(linhaConexao, BoxLayout.X_AXIS));
        linhaConexao.add(new JLabel("IP: "));
        campoIp.setMaximumSize(new Dimension(160, 30));
        linhaConexao.add(campoIp);
        linhaConexao.add(Box.createHorizontalStrut(12));
        linhaConexao.add(new JLabel("Porta: "));
        campoPorta.setMaximumSize(new Dimension(90, 30));
        linhaConexao.add(campoPorta);
        linhaConexao.add(Box.createHorizontalStrut(12));
        linhaConexao.add(botaoConectar);
        linhaConexao.add(Box.createHorizontalStrut(6));
        linhaConexao.add(botaoDesconectar);
        linhaConexao.add(Box.createHorizontalGlue());
        rotuloStatus.setFont(rotuloStatus.getFont().deriveFont(Font.BOLD));
        linhaConexao.add(rotuloStatus);

        abasAcesso.addTab("Entrar", painelLogin());
        abasAcesso.addTab("Cadastrar", painelCadastro());

        painelCartas.add(abasAcesso, CARTAO_ACESSO);
        painelCartas.add(painelSessao(), CARTAO_SESSAO);
        cartas.show(painelCartas, CARTAO_ACESSO);

        JLabel aviso = new JLabel(
                "As mensagens JSON trocadas aparecem no terminal que executa o cliente.");
        aviso.setFont(aviso.getFont().deriveFont(Font.ITALIC));

        JPanel conteudo = new JPanel(new BorderLayout(0, 12));
        conteudo.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        conteudo.add(linhaConexao, BorderLayout.NORTH);
        conteudo.add(painelCartas, BorderLayout.CENTER);
        conteudo.add(aviso, BorderLayout.SOUTH);

        setLayout(new BorderLayout());
        add(conteudo, BorderLayout.CENTER);
    }

    private JPanel painelLogin() {
        JPanel p = new JPanel(new GridBagLayout());
        GridBagConstraints g = grade();
        addLinha(p, g, 0, "Email:", loginEmail);
        addLinha(p, g, 1, "Senha:", loginSenha);
        g.gridx = 1;
        g.gridy = 2;
        g.anchor = GridBagConstraints.WEST;
        p.add(botaoEntrar, g);
        return p;
    }

    private JPanel painelCadastro() {
        JPanel p = new JPanel(new GridBagLayout());
        GridBagConstraints g = grade();
        addLinha(p, g, 0, "Email:", cadEmail);
        addLinha(p, g, 1, "Usuario:", cadUsuario);
        addLinha(p, g, 2, "Senha:", cadSenha);
        g.gridx = 1;
        g.gridy = 3;
        g.anchor = GridBagConstraints.WEST;
        p.add(botaoCadastrar, g);
        g.gridy = 4;
        JLabel dica = new JLabel("Usuario: so letras minusculas. Senha: letras e numeros.");
        dica.setFont(dica.getFont().deriveFont(Font.ITALIC, 11f));
        p.add(dica, g);
        return p;
    }

    private JPanel painelSessao() {
        JPanel dados = new JPanel(new GridBagLayout());
        dados.setBorder(BorderFactory.createTitledBorder("Meus dados (read_user)"));
        GridBagConstraints g = grade();
        addLinha(dados, g, 0, "Usuario:", dadosUsuario);
        addLinha(dados, g, 1, "Email:", dadosEmail);
        addLinha(dados, g, 2, "Perfil:", dadosRole);
        addLinha(dados, g, 3, "Cadastrado em:", dadosCriadoEm);
        g.gridx = 1;
        g.gridy = 4;
        dados.add(botaoRecarregar, g);

        JPanel alterar = new JPanel(new GridBagLayout());
        alterar.setBorder(BorderFactory.createTitledBorder(
                "Alterar (update_user) - deixe em branco o que nao quiser mudar"));
        GridBagConstraints g2 = grade();
        addLinha(alterar, g2, 0, "Novo usuario:", novoUsuario);
        addLinha(alterar, g2, 1, "Nova senha:", novaSenha);
        g2.gridx = 1;
        g2.gridy = 2;
        alterar.add(botaoSalvar, g2);
        g2.gridy = 3;
        JLabel dica = new JLabel("O email nao pode ser alterado depois do cadastro.");
        dica.setFont(dica.getFont().deriveFont(Font.ITALIC, 11f));
        alterar.add(dica, g2);

        JPanel acoes = new JPanel();
        acoes.setLayout(new BoxLayout(acoes, BoxLayout.X_AXIS));
        acoes.add(botaoSair);
        acoes.add(Box.createHorizontalStrut(8));
        acoes.add(botaoExcluir);
        acoes.add(Box.createHorizontalGlue());

        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        dados.setAlignmentX(LEFT_ALIGNMENT);
        alterar.setAlignmentX(LEFT_ALIGNMENT);
        acoes.setAlignmentX(LEFT_ALIGNMENT);
        p.add(dados);
        p.add(Box.createVerticalStrut(10));
        p.add(alterar);
        p.add(Box.createVerticalStrut(10));
        p.add(acoes);
        p.add(Box.createVerticalGlue());
        return p;
    }

    private GridBagConstraints grade() {
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(4, 6, 4, 6);
        g.anchor = GridBagConstraints.WEST;
        return g;
    }

    private void addLinha(JPanel painel, GridBagConstraints g, int linha, String rotulo,
                          java.awt.Component campo) {
        g.gridx = 0;
        g.gridy = linha;
        painel.add(new JLabel(rotulo), g);
        g.gridx = 1;
        painel.add(campo, g);
    }

    // ------------------------------------------------------------------ acoes

    private void ligarAcoes() {
        botaoConectar.addActionListener(e -> conectar());
        botaoDesconectar.addActionListener(e -> desconectar());
        botaoEntrar.addActionListener(e -> entrar());
        loginSenha.addActionListener(e -> entrar());
        botaoCadastrar.addActionListener(e -> cadastrar());
        botaoRecarregar.addActionListener(e -> lerCadastro());
        botaoSalvar.addActionListener(e -> salvarAlteracoes());
        botaoExcluir.addActionListener(e -> excluirCadastro());
        botaoSair.addActionListener(e -> sair());
    }

    private void conectar() {
        String ip = campoIp.getText().trim();
        int porta;
        try {
            porta = Integer.parseInt(campoPorta.getText().trim());
        } catch (NumberFormatException e) {
            erro("Porta invalida: informe um numero.");
            return;
        }
        if (ip.isEmpty()) {
            erro("Informe o IP do servidor.");
            return;
        }

        botaoConectar.setEnabled(false);
        rede.submit(() -> {
            try {
                conexao.conectar(ip, porta);
                registrar("conectado em " + ip + ":" + porta);
                SwingUtilities.invokeLater(() -> {
                    rotuloStatus.setText("Conectado em " + ip + ":" + porta);
                    botaoDesconectar.setEnabled(true);
                    campoIp.setEnabled(false);
                    campoPorta.setEnabled(false);
                    habilitarOperacoes(true);
                });
            } catch (IOException e) {
                registrar("falha ao conectar: " + e.getMessage());
                SwingUtilities.invokeLater(() -> {
                    botaoConectar.setEnabled(true);
                    erro("Nao foi possivel conectar em " + ip + ":" + porta + "\n" + e.getMessage());
                });
            }
        });
    }

    private void desconectar() {
        conexao.fechar();
        token = null;
        registrar("desconectado");
        rotuloStatus.setText("Desconectado");
        botaoConectar.setEnabled(true);
        botaoDesconectar.setEnabled(false);
        campoIp.setEnabled(true);
        campoPorta.setEnabled(true);
        habilitarOperacoes(false);
        cartas.show(painelCartas, CARTAO_ACESSO);
    }

    private void cadastrar() {
        JsonObject req = requisicao("register");
        req.addProperty("email", cadEmail.getText().trim());
        req.addProperty("user", cadUsuario.getText().trim());
        req.addProperty("password", texto(cadSenha));

        trocar(req, resposta -> {
            String status = campo(resposta, "status");
            informar(resposta);
            if ("201".equals(status)) {
                // O cadastro nao cria sessao: o proximo passo e o login.
                loginEmail.setText(cadEmail.getText().trim());
                cadEmail.setText("");
                cadUsuario.setText("");
                cadSenha.setText("");
                abasAcesso.setSelectedIndex(0);
            }
        });
    }

    private void entrar() {
        JsonObject req = requisicao("login");
        req.addProperty("email", loginEmail.getText().trim());
        req.addProperty("password", texto(loginSenha));

        trocar(req, resposta -> {
            if ("200".equals(campo(resposta, "status"))) {
                token = campo(resposta, "token");
                loginSenha.setText("");
                cartas.show(painelCartas, CARTAO_SESSAO);
                lerCadastro();
            } else {
                informar(resposta);
            }
        });
    }

    private void lerCadastro() {
        JsonObject req = requisicao("read_user");
        req.addProperty("token", token);

        trocar(req, resposta -> {
            if ("200".equals(campo(resposta, "status"))) {
                dadosUsuario.setText(campo(resposta, "user"));
                dadosEmail.setText(campo(resposta, "email"));
                dadosRole.setText(campo(resposta, "role"));
                dadosCriadoEm.setText(campo(resposta, "created_at"));
            } else {
                informar(resposta);
            }
        });
    }

    private void salvarAlteracoes() {
        String usuario = novoUsuario.getText().trim();
        String senha = texto(novaSenha);
        if (usuario.isEmpty() && senha.isEmpty()) {
            erro("Preencha ao menos um campo para alterar.");
            return;
        }

        JsonObject req = requisicao("update_user");
        req.addProperty("token", token);
        // Regra 2.11: string vazia significa "nao alterar".
        req.addProperty("user", usuario);
        req.addProperty("password", senha);

        trocar(req, resposta -> {
            informar(resposta);
            if ("200".equals(campo(resposta, "status"))) {
                novoUsuario.setText("");
                novaSenha.setText("");
                lerCadastro();
            }
        });
    }

    private void excluirCadastro() {
        JPasswordField campoSenha = new JPasswordField(16);
        int escolha = JOptionPane.showConfirmDialog(this,
                new Object[]{"Esta acao remove seu cadastro definitivamente.",
                        "Confirme a senha:", campoSenha},
                "Excluir cadastro", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
        if (escolha != JOptionPane.OK_OPTION) {
            return;
        }

        JsonObject req = requisicao("delete_user");
        req.addProperty("token", token);
        req.addProperty("password", texto(campoSenha));

        trocar(req, resposta -> {
            informar(resposta);
            if ("200".equals(campo(resposta, "status"))) {
                token = null;
                limparSessao();
                cartas.show(painelCartas, CARTAO_ACESSO);
            }
        });
    }

    private void sair() {
        JsonObject req = requisicao("logout");
        req.addProperty("token", token);

        trocar(req, resposta -> {
            informar(resposta);
            // O token local e descartado mesmo em 401: nos dois casos ele nao
            // serve mais para nada.
            token = null;
            limparSessao();
            cartas.show(painelCartas, CARTAO_ACESSO);
        });
    }

    // ------------------------------------------------------------------ apoio

    /** Cria a requisicao ja com 'op' como primeira chave (regra 2.1). */
    private JsonObject requisicao(String op) {
        JsonObject req = new JsonObject();
        req.addProperty("op", op);
        return req;
    }

    /**
     * Envia fora da EDT, registra os dois lados no terminal e entrega a resposta
     * ao tratador, ja de volta na EDT.
     */
    private void trocar(JsonObject requisicao, Consumer<JsonObject> aoResponder) {
        if (!conexao.estaConectado()) {
            erro("Conecte-se ao servidor primeiro.");
            return;
        }
        habilitarOperacoes(false);
        String json = GSON.toJson(requisicao);

        rede.submit(() -> {
            try {
                registrar("enviou:  " + json);
                String resposta = conexao.enviarEReceber(json);
                if (resposta == null) {
                    registrar("o servidor encerrou a conexao");
                    SwingUtilities.invokeLater(this::desconectar);
                    return;
                }
                registrar("recebeu: " + resposta);

                JsonObject objeto = JsonParser.parseString(resposta).getAsJsonObject();
                SwingUtilities.invokeLater(() -> {
                    // Reacao ao 401 definida na aba "Codigos de Status": descarta
                    // o token local e volta para a tela de login.
                    if ("401".equals(campo(objeto, "status")) && token != null) {
                        token = null;
                        limparSessao();
                        cartas.show(painelCartas, CARTAO_ACESSO);
                    }
                    aoResponder.accept(objeto);
                });
            } catch (Exception e) {
                registrar("falha na troca: " + e.getMessage());
                SwingUtilities.invokeLater(() -> erro("Falha na comunicacao:\n" + e.getMessage()));
            } finally {
                SwingUtilities.invokeLater(() -> habilitarOperacoes(conexao.estaConectado()));
            }
        });
    }

    private void limparSessao() {
        dadosUsuario.setText("-");
        dadosEmail.setText("-");
        dadosRole.setText("-");
        dadosCriadoEm.setText("-");
        novoUsuario.setText("");
        novaSenha.setText("");
    }

    /** Mostra o 'message' que veio do servidor, com o icone conforme o status. */
    private void informar(JsonObject resposta) {
        String status = campo(resposta, "status");
        String mensagem = campo(resposta, "message");
        boolean sucesso = status != null && status.startsWith("2");
        JOptionPane.showMessageDialog(this,
                (mensagem == null ? "Resposta sem mensagem." : mensagem) + "\n(status " + status + ")",
                sucesso ? "Sucesso" : "Atencao",
                sucesso ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE);
    }

    private static String campo(JsonObject o, String chave) {
        return o.has(chave) && !o.get(chave).isJsonNull() ? o.get(chave).getAsString() : null;
    }

    private static String texto(JPasswordField campo) {
        return new String(campo.getPassword());
    }

    private void habilitarOperacoes(boolean habilitado) {
        botaoEntrar.setEnabled(habilitado);
        botaoCadastrar.setEnabled(habilitado);
        botaoRecarregar.setEnabled(habilitado);
        botaoSalvar.setEnabled(habilitado);
        botaoExcluir.setEnabled(habilitado);
        botaoSair.setEnabled(habilitado);
    }

    /** Log no terminal. System.out ja e sincronizado, entao serve a qualquer thread. */
    private void registrar(String linha) {
        System.out.println(LocalTime.now().format(HORA) + "  " + linha);
    }

    private void erro(String mensagem) {
        System.err.println("ERRO: " + mensagem.replace('\n', ' '));
        JOptionPane.showMessageDialog(this, mensagem, "Cliente", JOptionPane.ERROR_MESSAGE);
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignorado) {
            // Segue com o visual padrao do Swing.
        }
        SwingUtilities.invokeLater(() -> new ClienteGUI().setVisible(true));
    }
}
