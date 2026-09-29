package cliente;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import ui.Estilo;

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
import java.awt.Component;
import java.awt.Dimension;
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
 * Interface gráfica do cliente.
 *
 * Atende a obs. 5 da grade -- um campo para o IP e outro para a porta. As
 * mensagens JSON trocadas (obs. 3) saem no TERMINAL que executa o cliente.
 *
 * Duas telas, trocadas por CardLayout:
 *
 *   ACESSO  -- abas de entrar (login) e cadastrar (register)
 *   SESSÃO  -- meus dados (read_user), alterar (update_user),
 *              excluir (delete_user) e sair (logout)
 *
 * O token fica só em memória (regra 3.2) e é descartado em todo 401, que é a
 * reação que a aba "Codigos de Status" manda ter.
 *
 * Nenhuma operação de rede roda na EDT: toda troca vai para um executor de uma
 * thread só, e o retorno volta para a tela via invokeLater.
 *
 * O texto desta tela usa acentuação normal, e as mensagens vindas do servidor
 * são acentuadas por {@link Mensagens} na hora de exibir. No fio elas continuam
 * exatamente como a regra 2.8 exige: em português, sem acento.
 */
public final class ClienteGUI extends JFrame {

    private static final long serialVersionUID = 1L;

    private static final Gson GSON = new Gson();
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm:ss");

    private static final String CARTAO_ACESSO = "acesso";
    private static final String CARTAO_SESSAO = "sessao";
    private static final Dimension TAMANHO_CAMPO = new Dimension(240, 30);

    // --- conexão ---
    private final JTextField campoIp = new JTextField("127.0.0.1", 12);
    private final JTextField campoPorta = new JTextField("5000", 6);
    private final JButton botaoConectar = Estilo.botaoPrincipal("Conectar");
    private final JButton botaoDesconectar = new JButton("Desconectar");
    private final Estilo.Indicador indicador = new Estilo.Indicador("Desconectado", false);

    // --- login ---
    private final JTextField loginEmail = new JTextField();
    private final JPasswordField loginSenha = new JPasswordField();
    private final JButton botaoEntrar = Estilo.botaoPrincipal("Entrar");

    // --- cadastro ---
    private final JTextField cadEmail = new JTextField();
    private final JTextField cadUsuario = new JTextField();
    private final JPasswordField cadSenha = new JPasswordField();
    private final JButton botaoCadastrar = Estilo.botaoPrincipal("Cadastrar");

    // --- sessão ---
    private final JLabel dadosUsuario = Estilo.valor("-");
    private final JLabel dadosEmail = Estilo.valor("-");
    private final JLabel dadosPerfil = Estilo.valor("-");
    private final JLabel dadosCriadoEm = Estilo.valor("-");
    private final JTextField novoUsuario = new JTextField();
    private final JPasswordField novaSenha = new JPasswordField();
    private final JButton botaoRecarregar = new JButton("Recarregar");
    private final JButton botaoSalvar = Estilo.botaoPrincipal("Salvar alterações");
    private final JButton botaoExcluir = Estilo.botaoDestrutivo("Excluir cadastro");
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

    /** Token da sessão. Vive só aqui, em memória (regra 3.2). */
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

        // pack() dimensiona pelo que o conteudo pede, em vez de um tamanho
        // fixo que pode cortar botoes quando a fonte do sistema e maior. O
        // CardLayout ja considera o maior dos dois cartoes.
        pack();
        setSize(Math.max(getWidth(), 700), Math.max(getHeight(), 640));
        setMinimumSize(getSize());
        setLocationRelativeTo(null);
    }

    // ------------------------------------------------------------------ layout

    private void montarTela() {
        JLabel titulo = Estilo.titulo("Reserva de Salas");
        JLabel subtitulo = Estilo.subtitulo("Cliente do sistema de reserva de salas do campus");

        JPanel cabecalho = new JPanel();
        cabecalho.setLayout(new BoxLayout(cabecalho, BoxLayout.Y_AXIS));
        Estilo.alinharEsquerda(titulo, subtitulo);
        cabecalho.add(titulo);
        cabecalho.add(Box.createVerticalStrut(2));
        cabecalho.add(subtitulo);

        JPanel conexaoPainel = Estilo.grupo("Servidor");
        conexaoPainel.setLayout(new BoxLayout(conexaoPainel, BoxLayout.Y_AXIS));

        JPanel linha = new JPanel();
        linha.setLayout(new BoxLayout(linha, BoxLayout.X_AXIS));
        linha.add(new JLabel("IP: "));
        campoIp.setMaximumSize(new Dimension(170, 30));
        linha.add(campoIp);
        linha.add(Box.createHorizontalStrut(14));
        linha.add(new JLabel("Porta: "));
        campoPorta.setMaximumSize(new Dimension(90, 30));
        linha.add(campoPorta);
        linha.add(Box.createHorizontalStrut(14));
        linha.add(botaoConectar);
        linha.add(Box.createHorizontalStrut(8));
        linha.add(botaoDesconectar);
        linha.add(Box.createHorizontalGlue());

        Estilo.alinharEsquerda(linha, indicador);
        conexaoPainel.add(linha);
        conexaoPainel.add(Box.createVerticalStrut(10));
        conexaoPainel.add(indicador);

        abasAcesso.addTab("  Entrar  ", painelLogin());
        abasAcesso.addTab("  Cadastrar  ", painelCadastro());

        painelCartas.add(abasAcesso, CARTAO_ACESSO);
        painelCartas.add(painelSessao(), CARTAO_SESSAO);
        cartas.show(painelCartas, CARTAO_ACESSO);

        JLabel rodape = Estilo.rodape(
                "As mensagens JSON trocadas aparecem no terminal que executa o cliente.");

        JPanel conteudo = new JPanel(new BorderLayout(0, 14));
        conteudo.setBorder(Estilo.margem());
        JPanel topo = new JPanel(new BorderLayout(0, 16));
        topo.add(cabecalho, BorderLayout.NORTH);
        topo.add(conexaoPainel, BorderLayout.SOUTH);
        conteudo.add(topo, BorderLayout.NORTH);
        conteudo.add(painelCartas, BorderLayout.CENTER);
        conteudo.add(rodape, BorderLayout.SOUTH);

        setLayout(new BorderLayout());
        add(conteudo, BorderLayout.CENTER);
    }

    private JPanel painelLogin() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));
        GridBagConstraints g = grade();
        campo(p, g, 0, "E-mail:", loginEmail);
        campo(p, g, 1, "Senha:", loginSenha);
        g.gridx = 1;
        g.gridy = 2;
        g.insets = new Insets(14, 6, 4, 6);
        p.add(botaoEntrar, g);
        return envolver(p);
    }

    private JPanel painelCadastro() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));
        GridBagConstraints g = grade();
        campo(p, g, 0, "E-mail:", cadEmail);
        campo(p, g, 1, "Usuário:", cadUsuario);
        campo(p, g, 2, "Senha:", cadSenha);
        g.gridx = 1;
        g.gridy = 3;
        g.insets = new Insets(14, 6, 4, 6);
        p.add(botaoCadastrar, g);
        g.gridy = 4;
        g.insets = new Insets(4, 6, 4, 6);
        p.add(Estilo.rodape("Usuário: apenas letras minúsculas, sem números nem espaços."), g);
        g.gridy = 5;
        p.add(Estilo.rodape("Senha: apenas letras e números, até 20 caracteres."), g);
        return envolver(p);
    }

    private JPanel painelSessao() {
        JPanel dados = Estilo.grupo("Meus dados");
        dados.setLayout(new GridBagLayout());
        GridBagConstraints g = grade();
        rotuloValor(dados, g, 0, "Usuário:", dadosUsuario);
        rotuloValor(dados, g, 1, "E-mail:", dadosEmail);
        rotuloValor(dados, g, 2, "Perfil:", dadosPerfil);
        rotuloValor(dados, g, 3, "Cadastrado em:", dadosCriadoEm);
        g.gridx = 1;
        g.gridy = 4;
        g.insets = new Insets(12, 6, 2, 6);
        dados.add(botaoRecarregar, g);

        JPanel alterar = Estilo.grupo("Alterar cadastro");
        alterar.setLayout(new GridBagLayout());
        GridBagConstraints g2 = grade();
        campo(alterar, g2, 0, "Novo usuário:", novoUsuario);
        campo(alterar, g2, 1, "Nova senha:", novaSenha);
        g2.gridx = 1;
        g2.gridy = 2;
        g2.insets = new Insets(12, 6, 2, 6);
        alterar.add(botaoSalvar, g2);
        g2.gridy = 3;
        g2.insets = new Insets(6, 6, 2, 6);
        alterar.add(Estilo.rodape("Deixe em branco o que não quiser alterar."), g2);
        g2.gridy = 4;
        alterar.add(Estilo.rodape("O e-mail não pode ser alterado depois do cadastro."), g2);

        JPanel acoes = new JPanel();
        acoes.setLayout(new BoxLayout(acoes, BoxLayout.X_AXIS));
        acoes.add(botaoSair);
        acoes.add(Box.createHorizontalGlue());
        acoes.add(botaoExcluir);

        // Empilhamento com GridBagLayout, e nao BoxLayout: o BoxLayout vertical
        // estica cada componente ate o tamanho maximo dele e acabava espremendo
        // o ultimo grupo, escondendo os botoes de sair e excluir.
        JPanel p = new JPanel(new GridBagLayout());
        GridBagConstraints pilha = new GridBagConstraints();
        pilha.gridx = 0;
        pilha.weightx = 1.0;
        pilha.fill = GridBagConstraints.HORIZONTAL;
        pilha.insets = new Insets(0, 0, 12, 0);

        pilha.gridy = 0;
        p.add(dados, pilha);
        pilha.gridy = 1;
        p.add(alterar, pilha);
        pilha.gridy = 2;
        pilha.insets = new Insets(2, 0, 0, 0);
        p.add(acoes, pilha);

        // Espacador que absorve a altura sobrando e mantem os grupos no alto.
        pilha.gridy = 3;
        pilha.weighty = 1.0;
        pilha.fill = GridBagConstraints.BOTH;
        p.add(new JPanel(), pilha);
        return p;
    }

    /** Envolve o formulário para que ele fique no alto, e não centralizado. */
    private JPanel envolver(JPanel interno) {
        JPanel fora = new JPanel(new BorderLayout());
        fora.add(interno, BorderLayout.NORTH);
        return fora;
    }

    private GridBagConstraints grade() {
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(5, 6, 5, 6);
        g.anchor = GridBagConstraints.WEST;
        return g;
    }

    private void campo(JPanel painel, GridBagConstraints g, int linha, String rotulo,
                       JTextField entrada) {
        entrada.setPreferredSize(TAMANHO_CAMPO);
        rotuloValor(painel, g, linha, rotulo, entrada);
    }

    /**
     * Uma linha "rotulo: valor".
     *
     * A terceira coluna e um espacador com weightx = 1: sem ele o GridBagLayout
     * centraliza o conteudo no painel, e a tela fica com os campos boiando no
     * meio enquanto todo o resto esta alinhado a esquerda.
     */
    private void rotuloValor(JPanel painel, GridBagConstraints g, int linha, String rotulo,
                             Component valor) {
        g.gridx = 0;
        g.gridy = linha;
        g.weightx = 0;
        g.fill = GridBagConstraints.NONE;
        painel.add(new JLabel(rotulo), g);

        g.gridx = 1;
        painel.add(valor, g);

        g.gridx = 2;
        g.weightx = 1.0;
        g.fill = GridBagConstraints.HORIZONTAL;
        painel.add(Box.createHorizontalGlue(), g);

        g.weightx = 0;
        g.fill = GridBagConstraints.NONE;
    }

    // ------------------------------------------------------------------ ações

    private void ligarAcoes() {
        botaoConectar.addActionListener(e -> conectar());
        botaoDesconectar.addActionListener(e -> desconectar());
        botaoEntrar.addActionListener(e -> entrar());
        loginSenha.addActionListener(e -> entrar());
        botaoCadastrar.addActionListener(e -> cadastrar());
        cadSenha.addActionListener(e -> cadastrar());
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
            erro("Porta inválida: informe um número.");
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
                    indicador.atualizar("Conectado em " + ip + ":" + porta, true);
                    botaoDesconectar.setEnabled(true);
                    campoIp.setEnabled(false);
                    campoPorta.setEnabled(false);
                    habilitarOperacoes(true);
                });
            } catch (IOException e) {
                registrar("falha ao conectar: " + e.getMessage());
                SwingUtilities.invokeLater(() -> {
                    botaoConectar.setEnabled(true);
                    erro("Não foi possível conectar em " + ip + ":" + porta + "\n" + e.getMessage());
                });
            }
        });
    }

    private void desconectar() {
        conexao.fechar();
        token = null;
        registrar("desconectado");
        indicador.atualizar("Desconectado", false);
        botaoConectar.setEnabled(true);
        botaoDesconectar.setEnabled(false);
        campoIp.setEnabled(true);
        campoPorta.setEnabled(true);
        habilitarOperacoes(false);
        limparSessao();
        cartas.show(painelCartas, CARTAO_ACESSO);
    }

    private void cadastrar() {
        JsonObject req = requisicao("register");
        req.addProperty("email", cadEmail.getText().trim());
        req.addProperty("user", cadUsuario.getText().trim());
        req.addProperty("password", texto(cadSenha));

        trocar(req, resposta -> {
            informar(resposta);
            if ("201".equals(campo(resposta, "status"))) {
                // O cadastro não cria sessão: o próximo passo é o login.
                loginEmail.setText(cadEmail.getText().trim());
                cadEmail.setText("");
                cadUsuario.setText("");
                cadSenha.setText("");
                abasAcesso.setSelectedIndex(0);
                loginSenha.requestFocusInWindow();
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
                dadosPerfil.setText("admin".equals(campo(resposta, "role"))
                        ? "Administrador" : "Usuário comum");
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
        // Regra 2.11: string vazia significa "não alterar".
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
                new Object[]{"Esta ação remove seu cadastro definitivamente.",
                        "Confirme a senha para continuar:", campoSenha},
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
            // O token local é descartado mesmo em 401: nos dois casos ele não
            // serve mais para nada.
            token = null;
            limparSessao();
            cartas.show(painelCartas, CARTAO_ACESSO);
        });
    }

    // ------------------------------------------------------------------ apoio

    /** Cria a requisição já com 'op' como primeira chave (regra 2.1). */
    private JsonObject requisicao(String op) {
        JsonObject req = new JsonObject();
        req.addProperty("op", op);
        return req;
    }

    /**
     * Envia fora da EDT, registra os dois lados no terminal e entrega a resposta
     * ao tratador, já de volta na EDT.
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
                    // Reação ao 401 definida na aba "Codigos de Status": descarta
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
                SwingUtilities.invokeLater(
                        () -> erro("Falha na comunicação com o servidor:\n" + e.getMessage()));
            } finally {
                SwingUtilities.invokeLater(() -> habilitarOperacoes(conexao.estaConectado()));
            }
        });
    }

    private void limparSessao() {
        dadosUsuario.setText("-");
        dadosEmail.setText("-");
        dadosPerfil.setText("-");
        dadosCriadoEm.setText("-");
        novoUsuario.setText("");
        novaSenha.setText("");
    }

    /**
     * Mostra a mensagem do servidor, acentuada para leitura.
     *
     * O texto exibido passa por {@link Mensagens}; o texto original continua
     * visível no terminal, dentro do JSON cru.
     */
    private void informar(JsonObject resposta) {
        String status = campo(resposta, "status");
        String op = campo(resposta, "op");
        boolean sucesso = status != null && status.startsWith("2");

        StringBuilder texto = new StringBuilder(Mensagens.paraExibicao(campo(resposta, "message")));
        String dica = Mensagens.dica(status, op);
        if (dica != null) {
            texto.append("\n\n").append(dica);
        }

        JOptionPane.showMessageDialog(this, texto.toString(),
                sucesso ? "Tudo certo" : "Atenção",
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

    /**
     * Log no terminal, sem acentos de propósito: o console de outro sistema
     * operacional pode não estar em UTF-8, e caractere quebrado no log durante a
     * avaliação atrapalha mais do que a falta do acento. Na janela, onde o Swing
     * cuida da codificação, o texto é acentuado normalmente.
     */
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
            // Segue com o visual padrão do Swing.
        }
        SwingUtilities.invokeLater(() -> new ClienteGUI().setVisible(true));
    }
}
