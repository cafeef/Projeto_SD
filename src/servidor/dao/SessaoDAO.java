package servidor.dao;

import servidor.Senhas;
import servidor.modelo.Usuario;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Acesso a tabela 'sessoes' -- emissao, validacao e invalidacao de tokens.
 *
 * Regras cobertas:
 *
 *  3.1 Token gerado no login: 64 hexadecimais minusculos
 *  3.2 Guardado no banco, associado ao usuario
 *  3.3 Validade de 30 minutos sem uso; cada requisicao valida renova a contagem
 *  3.4 O logout invalida o token, e um token invalidado nunca volta a valer
 *  3.5 No maximo uma sessao ativa por usuario
 */
public final class SessaoDAO {

    /** Regra 3.3: 30 minutos sem uso. */
    public static final int VALIDADE_MINUTOS = 30;

    private SessaoDAO() {
    }

    /** Emite um token novo para o usuario (regra 3.1). */
    public static String criar(int usuarioId) throws SQLException {
        String token = Senhas.novoToken();
        Timestamp agora = Timestamp.from(Instant.now());
        try (Connection c = Banco.conexao();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO sessoes (token, usuario_id, criado_em, ultimo_uso, ativa)"
                             + " VALUES (?, ?, ?, ?, TRUE)")) {
            ps.setString(1, token);
            ps.setInt(2, usuarioId);
            ps.setTimestamp(3, agora);
            ps.setTimestamp(4, agora);
            ps.executeUpdate();
        }
        return token;
    }

    /**
     * Valida o token e devolve o dono da sessao, ou null se o token nao existe,
     * ja foi invalidado ou expirou.
     *
     * Devolver o usuario e nao apenas um "sim" atende a regra 3.8: o autor da
     * requisicao sai do token, e o cliente nunca informa quem e.
     *
     * A renovacao da contagem (regra 3.3) acontece aqui, no mesmo caminho da
     * validacao, para nao existir requisicao valida que deixe de renovar.
     */
    public static Usuario validarERenovar(String token) throws SQLException {
        Timestamp limite = Timestamp.from(
                Instant.now().minus(VALIDADE_MINUTOS, ChronoUnit.MINUTES));
        try (Connection c = Banco.conexao()) {
            int usuarioId;
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT usuario_id FROM sessoes"
                            + " WHERE token = ? AND ativa = TRUE AND ultimo_uso >= ?")) {
                ps.setString(1, token);
                ps.setTimestamp(2, limite);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return null;
                    }
                    usuarioId = rs.getInt(1);
                }
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE sessoes SET ultimo_uso = ? WHERE token = ?")) {
                ps.setTimestamp(1, Timestamp.from(Instant.now()));
                ps.setString(2, token);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT id, usuario, email, role, criado_em FROM usuarios WHERE id = ?")) {
                ps.setInt(1, usuarioId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return null;
                    }
                    return new Usuario(rs.getInt("id"), rs.getString("usuario"),
                            rs.getString("email"), rs.getString("role"),
                            rs.getTimestamp("criado_em"));
                }
            }
        }
    }

    /**
     * Invalida o token do logout (regra 3.4).
     *
     * A linha NAO e apagada: fica marcada como inativa, para que um token ja
     * usado nunca seja aceito de novo nem reemitido por acaso.
     *
     * @return true se havia mesmo uma sessao ativa com esse token
     */
    public static boolean invalidar(String token) throws SQLException {
        try (Connection c = Banco.conexao();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE sessoes SET ativa = FALSE WHERE token = ? AND ativa = TRUE")) {
            ps.setString(1, token);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * Diz se o usuario ja tem sessao ativa (regra 3.5) -- base do 409 no login.
     *
     * Sessao vencida nao conta: a propria mensagem do 409 diz para "fazer logout
     * ou aguardar a expiracao de 30 min", logo depois de vencida ela nao pode
     * mais bloquear um login novo.
     */
    public static boolean temSessaoAtiva(int usuarioId) throws SQLException {
        Timestamp limite = Timestamp.from(
                Instant.now().minus(VALIDADE_MINUTOS, ChronoUnit.MINUTES));
        try (Connection c = Banco.conexao();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT COUNT(*) FROM sessoes"
                             + " WHERE usuario_id = ? AND ativa = TRUE AND ultimo_uso >= ?")) {
            ps.setInt(1, usuarioId);
            ps.setTimestamp(2, limite);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1) > 0;
            }
        }
    }

    /** Derruba todas as sessoes do usuario (usado no delete_user). */
    public static int invalidarTodasDoUsuario(int usuarioId) throws SQLException {
        try (Connection c = Banco.conexao();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE sessoes SET ativa = FALSE WHERE usuario_id = ? AND ativa = TRUE")) {
            ps.setInt(1, usuarioId);
            return ps.executeUpdate();
        }
    }

    /**
     * Invalida TODAS as sessoes ativas. Chamado quando o servidor comeca a
     * escutar (ver {@link servidor.ServidorSocket#iniciar()}).
     *
     * Quando o servidor cai, todas as conexoes caem junto: nenhum cliente tem
     * como continuar uma sessao aberta antes. Sem isso, as sessoes anteriores
     * continuariam ativas no banco e o usuario levaria 409 "Usuario ja possui
     * sessao ativa" ao tentar entrar de novo, sem saida a nao ser esperar os 30
     * minutos de expiracao -- porque o token da sessao antiga se perdeu junto
     * com o cliente.
     *
     * Nao conflita com o protocolo: a regra 3.4 exige apenas que um token
     * invalidado nunca volte a valer, e a reacao do cliente ao 401 ja esta
     * definida na aba "Codigos de Status" (descartar o token e voltar ao login).
     *
     * @return quantas sessoes foram encerradas
     */
    public static int invalidarTodas() throws SQLException {
        try (Connection c = Banco.conexao();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE sessoes SET ativa = FALSE WHERE ativa = TRUE")) {
            return ps.executeUpdate();
        }
    }

    /** Envelhece uma sessao artificialmente. Existe para os testes de expiracao. */
    public static void envelhecerParaTeste(String token, int minutos) throws SQLException {
        try (Connection c = Banco.conexao();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE sessoes SET ultimo_uso = ? WHERE token = ?")) {
            ps.setTimestamp(1, Timestamp.from(
                    Instant.now().minus(minutos, ChronoUnit.MINUTES)));
            ps.setString(2, token);
            ps.executeUpdate();
        }
    }
}
