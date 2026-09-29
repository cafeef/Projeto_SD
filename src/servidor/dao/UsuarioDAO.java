package servidor.dao;

import servidor.Senhas;
import servidor.modelo.Usuario;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** Acesso a tabela 'usuarios'. */
public final class UsuarioDAO {

    /** Codigo de erro do H2 para violacao de chave unica. */
    private static final int H2_CHAVE_DUPLICADA = 23505;

    private UsuarioDAO() {
    }

    /**
     * Cria um cadastro. Todo cadastro nasce com role "user" (regra 3.6).
     *
     * @throws DadoDuplicadoException se o usuario ou o email ja existem (409)
     */
    public static int criar(String usuario, String email, String senha)
            throws SQLException, DadoDuplicadoException {
        String salt = Senhas.novoSalt();
        try (Connection c = Banco.conexao();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO usuarios (usuario, email, senha_hash, salt, role)"
                             + " VALUES (?, ?, ?, ?, 'user')",
                     Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, usuario);
            ps.setString(2, email);
            ps.setString(3, Senhas.hash(senha, salt));
            ps.setString(4, salt);
            ps.executeUpdate();
            try (ResultSet chaves = ps.getGeneratedKeys()) {
                chaves.next();
                return chaves.getInt(1);
            }
        } catch (SQLException e) {
            if (e.getErrorCode() == H2_CHAVE_DUPLICADA) {
                throw new DadoDuplicadoException("usuario ou email ja cadastrado", e);
            }
            throw e;
        }
    }

    public static Usuario buscarPorId(int id) throws SQLException {
        return buscarPor("id", String.valueOf(id));
    }

    public static Usuario buscarPorEmail(String email) throws SQLException {
        return buscarPor("email", email);
    }

    public static Usuario buscarPorUsuario(String usuario) throws SQLException {
        return buscarPor("usuario", usuario);
    }

    /**
     * Confere as credenciais do login.
     *
     * Devolve null tanto para email inexistente quanto para senha errada, de
     * proposito: a aba de mensagens manda responder a MESMA mensagem 401 nos
     * dois casos, para nao revelar quais emails estao cadastrados.
     */
    public static Usuario autenticar(String email, String senha) throws SQLException {
        try (Connection c = Banco.conexao();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id, usuario, email, role, criado_em, senha_hash, salt"
                             + " FROM usuarios WHERE email = ?")) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                if (!Senhas.confere(senha, rs.getString("salt"), rs.getString("senha_hash"))) {
                    return null;
                }
                return montar(rs);
            }
        }
    }

    /** Confere a senha de um usuario ja identificado (confirmacao do delete_user). */
    public static boolean conferirSenha(int id, String senha) throws SQLException {
        try (Connection c = Banco.conexao();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT senha_hash, salt FROM usuarios WHERE id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next()
                        && Senhas.confere(senha, rs.getString("salt"), rs.getString("senha_hash"));
            }
        }
    }

    /**
     * Atualiza o cadastro. Parametro null significa "nao alterar", que e como a
     * regra 2.11 define o campo ausente ou vazio na requisicao.
     *
     * O email nao entra aqui de proposito: e imutavel depois do cadastro
     * (RNF 5.c), e o handler recusa a requisicao antes de chegar no DAO.
     */
    public static boolean atualizar(int id, String novoUsuario, String novaSenha)
            throws SQLException, DadoDuplicadoException {
        List<String> colunas = new ArrayList<>();
        List<String> valores = new ArrayList<>();
        if (novoUsuario != null) {
            colunas.add("usuario = ?");
            valores.add(novoUsuario);
        }
        String salt = null;
        if (novaSenha != null) {
            salt = Senhas.novoSalt();
            colunas.add("senha_hash = ?");
            valores.add(Senhas.hash(novaSenha, salt));
            colunas.add("salt = ?");
            valores.add(salt);
        }
        if (colunas.isEmpty()) {
            return true;   // nada a alterar e sucesso, nao erro
        }
        String sql = "UPDATE usuarios SET " + String.join(", ", colunas) + " WHERE id = ?";
        try (Connection c = Banco.conexao();
             PreparedStatement ps = c.prepareStatement(sql)) {
            int i = 1;
            for (String valor : valores) {
                ps.setString(i++, valor);
            }
            ps.setInt(i, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            if (e.getErrorCode() == H2_CHAVE_DUPLICADA) {
                throw new DadoDuplicadoException("usuario ja esta em uso", e);
            }
            throw e;
        }
    }

    /** Altera o role. So o admin faz isso (regra 3.6); usado pelo admin_update_user. */
    public static boolean alterarRole(int id, String role) throws SQLException {
        try (Connection c = Banco.conexao();
             PreparedStatement ps = c.prepareStatement("UPDATE usuarios SET role = ? WHERE id = ?")) {
            ps.setString(1, role);
            ps.setInt(2, id);
            return ps.executeUpdate() > 0;
        }
    }

    /** Remove o cadastro. As sessoes caem junto, pelo ON DELETE CASCADE. */
    public static boolean remover(int id) throws SQLException {
        try (Connection c = Banco.conexao();
             PreparedStatement ps = c.prepareStatement("DELETE FROM usuarios WHERE id = ?")) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        }
    }

    /** Quantos administradores existem -- base da regra do ultimo administrador. */
    public static int contarAdministradores() throws SQLException {
        try (Connection c = Banco.conexao();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT COUNT(*) FROM usuarios WHERE role = 'admin'");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }

    /** Lista todos os cadastros, para o admin_list_users da EP-2. */
    public static List<Usuario> listarTodos() throws SQLException {
        List<Usuario> usuarios = new ArrayList<>();
        try (Connection c = Banco.conexao();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id, usuario, email, role, criado_em FROM usuarios ORDER BY id");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                usuarios.add(montar(rs));
            }
        }
        return usuarios;
    }

    private static Usuario buscarPor(String coluna, String valor) throws SQLException {
        try (Connection c = Banco.conexao();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id, usuario, email, role, criado_em FROM usuarios WHERE "
                             + coluna + " = ?")) {
            // 'coluna' nunca vem do cliente: e uma constante escrita aqui dentro.
            ps.setString(1, valor);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? montar(rs) : null;
            }
        }
    }

    private static Usuario montar(ResultSet rs) throws SQLException {
        return new Usuario(
                rs.getInt("id"),
                rs.getString("usuario"),
                rs.getString("email"),
                rs.getString("role"),
                rs.getTimestamp("criado_em"));
    }
}
