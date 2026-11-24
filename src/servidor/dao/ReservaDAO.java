package servidor.dao;

import servidor.modelo.Reserva;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Acesso a tabela 'reservas'.
 *
 * Todas as consultas trazem o nome da sala e o usuario pelo JOIN, porque o
 * protocolo os exige dentro de cada item devolvido.
 *
 * Sobre a coluna 'ativa': ela diz que a reserva NAO foi cancelada, e nada
 * mais. Nao e a "reserva ativa" da regra 9.1, que e a que ainda nao terminou
 * -- essa depende da hora, e quem responde por ela e Reserva.estaEncerrada().
 * As duas condicoes andam juntas em quase toda consulta, e e por isso que o
 * modelo inverte o nome para 'cancelada': a palavra "ativa" no meio do codigo
 * significaria duas coisas diferentes conforme a linha.
 *
 * Nenhum metodo daqui tranca nada: a secao critica da regra 5.1 e do handler,
 * que precisa envolver a VERIFICACAO e a GRAVACAO juntas. Uma tranca aqui
 * dentro protegeria so a gravacao, e seria protecao nenhuma -- as duas
 * requisicoes ja teriam consultado antes de chegar.
 */
public final class ReservaDAO {

    /** As colunas que todo SELECT devolve, na mesma ordem que ler() espera. */
    private static final String CAMPOS =
            "r.id, r.sala_id, s.nome, r.usuario_id, u.usuario, r.dia, r.inicio, r.fim,"
                    + " r.assunto, r.participantes, r.ativa, r.criada_em";

    private static final String DE =
            " FROM reservas r"
                    + " JOIN salas s ON s.id = r.sala_id"
                    + " JOIN usuarios u ON u.id = r.usuario_id";

    private ReservaDAO() {
    }

    /**
     * Grava uma reserva e devolve o id gerado.
     *
     * Nao confere conflito: quem confere e o handler, dentro da tranca da sala.
     */
    public static int criar(int salaId, int usuarioId, LocalDate dia, LocalTime inicio,
                            LocalTime fim, String assunto, int participantes)
            throws SQLException {
        try (Connection c = Banco.conexao();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO reservas (sala_id, usuario_id, dia, inicio, fim,"
                             + " assunto, participantes) VALUES (?, ?, ?, ?, ?, ?, ?)",
                     Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, salaId);
            ps.setInt(2, usuarioId);
            ps.setDate(3, Date.valueOf(dia));
            ps.setTime(4, Time.valueOf(inicio));
            ps.setTime(5, Time.valueOf(fim));
            ps.setString(6, assunto);
            ps.setInt(7, participantes);
            ps.executeUpdate();
            try (ResultSet chaves = ps.getGeneratedKeys()) {
                chaves.next();
                return chaves.getInt(1);
            }
        }
    }

    /**
     * Busca uma reserva NAO cancelada pelo id.
     *
     * Cancelada conta como inexistente: o cancelamento e o fim da reserva, e
     * quem pergunta por ela depois recebe 404. Essa e a leitura do testador
     * oficial da turma -- cancelar duas vezes responde 404 na segunda, e nao
     * 409 --, e seguir a mesma leitura e o que faz o nosso servidor combinar
     * com os clientes dos outros grupos.
     *
     * A linha continua no banco: some das consultas, mas o historico fica.
     *
     * @return a reserva, ou null se o id nao existe ou foi cancelada
     */
    public static Reserva buscarPorId(int id) throws SQLException {
        try (Connection c = Banco.conexao();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT " + CAMPOS + DE + " WHERE r.id = ? AND r.ativa = TRUE")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? ler(rs) : null;
            }
        }
    }

    /**
     * Lista reservas NAO canceladas, com os filtros opcionais do protocolo.
     *
     * Os filtros se acumulam, e cada um ausente simplesmente nao entra no
     * WHERE. Sala inexistente nao da 404: devolve lista vazia (regra 9.12).
     *
     * @param usuarioId do dono, ou null para o scope "all" (so admin)
     * @param dia       filtro de data, ou null
     * @param salaId    filtro de sala, ou null
     */
    public static List<Reserva> listar(Integer usuarioId, LocalDate dia, Integer salaId)
            throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT " + CAMPOS + DE
                + " WHERE r.ativa = TRUE");
        List<Object> valores = new ArrayList<>();
        if (usuarioId != null) {
            sql.append(" AND r.usuario_id = ?");
            valores.add(usuarioId);
        }
        if (dia != null) {
            sql.append(" AND r.dia = ?");
            valores.add(Date.valueOf(dia));
        }
        if (salaId != null) {
            sql.append(" AND r.sala_id = ?");
            valores.add(salaId);
        }
        // Ordem estavel e util: a agenda mais proxima primeiro.
        sql.append(" ORDER BY r.dia, r.inicio, r.id");

        List<Reserva> reservas = new ArrayList<>();
        try (Connection c = Banco.conexao();
             PreparedStatement ps = c.prepareStatement(sql.toString())) {
            for (int i = 0; i < valores.size(); i++) {
                ps.setObject(i + 1, valores.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    reservas.add(ler(rs));
                }
            }
        }
        return reservas;
    }

    /**
     * Cancela a reserva. Marca em vez de apagar: a reserva cancelada sai das
     * consultas, mas o registro continua, e com ele a resposta honesta a quem
     * perguntar pelo id dela depois.
     *
     * @return false se o id nao existe ou ja estava cancelada
     */
    public static boolean cancelar(int id) throws SQLException {
        try (Connection c = Banco.conexao();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE reservas SET ativa = FALSE"
                             + " WHERE id = ? AND ativa = TRUE")) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * Diz se a sala ja tem reserva que COLIDE com o periodo (regra 5.4).
     *
     * Colidem quando uma comeca antes de a outra terminar e termina depois de
     * a outra comecar. Encostar nao e colidir: a reserva das 13:00 as 13:50 e
     * a das 13:50 as 14:40 convivem, e por isso as comparacoes sao estritas.
     *
     * @param ignorar id de uma reserva a desconsiderar (a propria, no update),
     *                ou null
     */
    public static boolean temConflito(int salaId, LocalDate dia, LocalTime inicio,
                                      LocalTime fim, Integer ignorar) throws SQLException {
        String sql = "SELECT 1 FROM reservas"
                + " WHERE sala_id = ? AND ativa = TRUE AND dia = ?"
                + "   AND inicio < ? AND fim > ?"
                + (ignorar == null ? "" : " AND id <> ?")
                + " LIMIT 1";
        try (Connection c = Banco.conexao();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, salaId);
            ps.setDate(2, Date.valueOf(dia));
            ps.setTime(3, Time.valueOf(fim));
            ps.setTime(4, Time.valueOf(inicio));
            if (ignorar != null) {
                ps.setInt(5, ignorar);
            }
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /** Quantas reservas nao canceladas existem. Usado pelos testes. */
    public static int contar() throws SQLException {
        try (Connection c = Banco.conexao();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT COUNT(*) FROM reservas WHERE ativa = TRUE");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private static Reserva ler(ResultSet rs) throws SQLException {
        Timestamp criadaEm = rs.getTimestamp(12);
        return new Reserva(
                rs.getInt(1),
                rs.getInt(2),
                rs.getString(3),
                rs.getInt(4),
                rs.getString(5),
                rs.getDate(6).toLocalDate(),
                rs.getTime(7).toLocalTime(),
                rs.getTime(8).toLocalTime(),
                rs.getString(9),
                rs.getInt(10),
                !rs.getBoolean(11),   // coluna 'ativa' -> campo 'cancelada'

                criadaEm == null ? null : criadaEm.toLocalDateTime());
    }
}
