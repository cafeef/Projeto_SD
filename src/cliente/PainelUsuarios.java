package cliente;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import ui.Estilo;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Dimension;

/**
 * Aba de administracao de usuarios -- as quatro operacoes admin_* da EP-2.
 *
 * A aba inteira so aparece para quem esta logado numa conta de administrador.
 * De novo: isso e conveniencia, nao seguranca. O servidor confere a whitelist
 * de tokens (regra 3.14) a cada requisicao, entao esconder a aba so evita
 * oferecer um botao que responderia 403.
 *
 * Tres detalhes do protocolo aparecem direto na tela:
 *
 *  - o alvo e identificado por 'target_user', nunca por token: o token diz quem
 *    FAZ a operacao, e o 'target_user' diz sobre QUEM ela e (regra 3.8);
 *  - 'user', 'password' e 'role' sao obrigatorios no admin_update_user, e ""
 *    significa "nao alterar" -- e por isso que o combo tem a opcao "(manter)";
 *  - o admin_delete_user NAO pede senha. A senha do delete_user confirma que
 *    quem esta no teclado e o dono da conta; aqui o dono da conta nem esta
 *    presente, e exigir a senha dele seria exigir o impossivel.
 */
final class PainelUsuarios extends JPanel {

    /** Opcao do combo de perfil que deixa o 'role' como esta. */
    private static final String MANTER = "(manter)";

    private static final long serialVersionUID = 1L;

    private final transient Canal canal;

    private final JTable tabela = Estilo.tabela("Usuário", "E-mail", "Perfil");
    private final JButton botaoListar = Estilo.botaoPrincipal("Listar usuários");
    private final JButton botaoDetalhes = new JButton("Ver detalhes");

    private final JTextField alvo = new JTextField(16);
    private final JTextField novoUsuario = new JTextField(16);
    private final JPasswordField novaSenha = new JPasswordField(16);
    private final JComboBox<String> perfil =
            new JComboBox<>(new String[] {MANTER, "user", "admin"});
    private final JButton botaoSalvar = Estilo.botaoPrincipal("Salvar alterações");
    private final JButton botaoExcluir = Estilo.botaoDestrutivo("Excluir usuário");

    PainelUsuarios(Canal canal) {
        this.canal = canal;
        montar();
        ligarAcoes();
    }

    // ----------------------------------------------------------------- layout

    private void montar() {
        setLayout(new BorderLayout(0, 12));

        JScrollPane rolagem = new JScrollPane(tabela);
        rolagem.setPreferredSize(new Dimension(640, 200));

        JPanel acoes = new JPanel();
        acoes.setLayout(new BoxLayout(acoes, BoxLayout.X_AXIS));
        acoes.add(botaoListar);
        acoes.add(Box.createHorizontalStrut(8));
        acoes.add(botaoDetalhes);
        acoes.add(Box.createHorizontalGlue());

        JPanel centro = new JPanel(new BorderLayout(0, 8));
        centro.add(acoes, BorderLayout.NORTH);
        centro.add(rolagem, BorderLayout.CENTER);

        JPanel botoes = new JPanel();
        botoes.setLayout(new BoxLayout(botoes, BoxLayout.X_AXIS));
        botoes.add(botaoSalvar);
        botoes.add(Box.createHorizontalGlue());
        botoes.add(botaoExcluir);

        JPanel grupo = Estilo.grupo("Alterar ou remover um usuário");
        new Estilo.Grade(grupo)
                .campo("Usuário alvo:", alvo)
                .campo("Novo nome de usuário:", novoUsuario)
                .campo("Nova senha:", novaSenha)
                .campo("Perfil:", perfil)
                .linhaCheia(botoes)
                .dica("Deixe em branco o que não quiser alterar.")
                .dica("Clique num usuário da tabela para carregá-lo aqui.")
                .dica("Promover ou rebaixar vale na hora, sem a pessoa precisar"
                        + " entrar de novo.");

        add(centro, BorderLayout.CENTER);
        add(grupo, BorderLayout.SOUTH);
    }

    void habilitar(boolean habilitado) {
        botaoListar.setEnabled(habilitado);
        botaoDetalhes.setEnabled(habilitado);
        botaoSalvar.setEnabled(habilitado);
        botaoExcluir.setEnabled(habilitado);
    }

    void limpar() {
        Estilo.modeloDe(tabela).setRowCount(0);
        limparFormulario();
    }

    // ------------------------------------------------------------------ ações

    private void ligarAcoes() {
        botaoListar.addActionListener(e -> listar());
        botaoDetalhes.addActionListener(e -> verDetalhes());
        botaoSalvar.addActionListener(e -> salvar());
        botaoExcluir.addActionListener(e -> excluir());
        tabela.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                carregarSelecionado();
            }
        });
    }

    private void listar() {
        JsonObject req = Canal.requisicao("admin_list_users");
        req.addProperty("token", canal.token());

        canal.trocar(req, resposta -> {
            if (!"200".equals(campo(resposta, "status"))) {
                canal.informar(resposta);
                return;
            }
            var modelo = Estilo.modeloDe(tabela);
            modelo.setRowCount(0);
            JsonArray usuarios = resposta.has("users") && resposta.get("users").isJsonArray()
                    ? resposta.getAsJsonArray("users")
                    : new JsonArray();
            for (JsonElement elemento : usuarios) {
                JsonObject u = elemento.getAsJsonObject();
                modelo.addRow(new Object[] {
                        campo(u, "user"),
                        campo(u, "email"),
                        "admin".equals(campo(u, "role")) ? "administrador" : "comum"});
            }
        });
    }

    private void verDetalhes() {
        String alvoAtual = alvoInformado();
        if (alvoAtual == null) {
            return;
        }
        JsonObject req = Canal.requisicao("admin_read_user");
        req.addProperty("token", canal.token());
        req.addProperty("target_user", alvoAtual);

        canal.trocar(req, resposta -> {
            if (!"200".equals(campo(resposta, "status"))) {
                canal.informar(resposta);
                return;
            }
            String texto = "Usuário: " + campo(resposta, "user") + "\n"
                    + "E-mail: " + campo(resposta, "email") + "\n"
                    + "Perfil: " + ("admin".equals(campo(resposta, "role"))
                            ? "administrador" : "usuário comum") + "\n"
                    + "Cadastrado em: " + campo(resposta, "created_at");
            JOptionPane.showMessageDialog(this, texto, "Detalhes do usuário",
                    JOptionPane.INFORMATION_MESSAGE);
        });
    }

    private void salvar() {
        String alvoAtual = alvoInformado();
        if (alvoAtual == null) {
            return;
        }
        String novoPerfil = perfilEscolhido((String) perfil.getSelectedItem());
        String nome = novoUsuario.getText().trim();
        String senha = new String(novaSenha.getPassword());

        if (nome.isEmpty() && senha.isEmpty() && novoPerfil.isEmpty()) {
            // O servidor responderia 400 "Nenhum dado para atualizar"; avisar
            // aqui poupa a ida e diz a mesma coisa.
            canal.erro("Preencha ao menos um campo para alterar.");
            return;
        }

        JsonObject req = requisicaoDeAlteracao(canal.token(), alvoAtual, nome, senha,
                novoPerfil);

        canal.trocar(req, resposta -> {
            canal.informar(resposta);
            if ("200".equals(campo(resposta, "status"))) {
                limparFormulario();
                listar();
                // Pode ter sido o proprio administrador se rebaixando: nesse
                // caso o token saiu da whitelist agora e esta aba tem que sair
                // da tela junto.
                canal.relerCadastro();
            }
        });
    }

    private void excluir() {
        String alvoAtual = alvoInformado();
        if (alvoAtual == null) {
            return;
        }
        int escolha = JOptionPane.showConfirmDialog(this,
                "Remover definitivamente o usuário \"" + alvoAtual + "\"?\n\n"
                        + "A sessão dele cai na hora, e o nome e o e-mail\n"
                        + "voltam a ficar livres para um novo cadastro.",
                "Excluir usuário", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
        if (escolha != JOptionPane.OK_OPTION) {
            return;
        }

        JsonObject req = requisicaoDeRemocao(canal.token(), alvoAtual);

        canal.trocar(req, resposta -> {
            canal.informar(resposta);
            if ("200".equals(campo(resposta, "status"))) {
                limparFormulario();
                listar();
                canal.relerCadastro();
            }
        });
    }

    // ------------------------------------------------------------------ apoio

    private void carregarSelecionado() {
        int linha = tabela.getSelectedRow();
        if (linha < 0) {
            return;
        }
        Object valor = Estilo.modeloDe(tabela).getValueAt(linha, 0);
        alvo.setText(valor == null ? "" : valor.toString());
        novoUsuario.setText("");
        novaSenha.setText("");
        perfil.setSelectedItem(MANTER);
    }

    private void limparFormulario() {
        alvo.setText("");
        novoUsuario.setText("");
        novaSenha.setText("");
        perfil.setSelectedItem(MANTER);
    }

    private String alvoInformado() {
        String valor = alvo.getText().trim();
        if (valor.isEmpty()) {
            canal.erro("Informe o usuário alvo, ou clique num usuário da tabela.");
            return null;
        }
        return valor;
    }

    // --------------------------------- montagem das requisicoes de admin_*
    //
    // Estaticas e sem Swing: e o JSON que sai daqui que outro grupo vai
    // receber, entao e a parte que mais merece teste -- e testa-la nao deve
    // exigir abrir uma janela.

    /**
     * Monta o admin_update_user.
     *
     * As tres chaves de alteracao sao obrigatorias e "" significa "nao
     * alterar": e o combo "(manter)" que se traduz em "" para o 'role'. O alvo
     * vai em 'target_user', nunca no token -- o token diz quem FAZ a operacao
     * (regra 3.8).
     */
    static JsonObject requisicaoDeAlteracao(String token, String alvo, String novoNome,
                                            String novaSenha, String novoPerfil) {
        JsonObject req = Canal.requisicao("admin_update_user");
        req.addProperty("token", token);
        req.addProperty("target_user", alvo.trim());
        req.addProperty("user", novoNome.trim());
        // A senha nao leva trim: espaco em senha e caractere como qualquer
        // outro, e aparar mudaria silenciosamente o que a pessoa digitou.
        req.addProperty("password", novaSenha);
        req.addProperty("role", novoPerfil);
        return req;
    }

    /**
     * Monta o admin_delete_user.
     *
     * Sem senha, de proposito: a senha do delete_user confirma que quem esta no
     * teclado e o dono da conta; aqui o dono nem esta presente, e pedir a senha
     * dele seria pedir o impossivel.
     */
    static JsonObject requisicaoDeRemocao(String token, String alvo) {
        JsonObject req = Canal.requisicao("admin_delete_user");
        req.addProperty("token", token);
        req.addProperty("target_user", alvo.trim());
        return req;
    }

    /** O valor que o combo de perfil manda no 'role': "" quer dizer "manter". */
    static String perfilEscolhido(String opcaoDoCombo) {
        return MANTER.equals(opcaoDoCombo) ? "" : opcaoDoCombo;
    }

    private static String campo(JsonObject o, String chave) {
        return o.has(chave) && !o.get(chave).isJsonNull() ? o.get(chave).getAsString() : null;
    }
}
