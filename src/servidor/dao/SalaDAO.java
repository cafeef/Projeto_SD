package servidor.dao;

import servidor.modelo.Sala;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Time;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Acesso as tabelas 'salas' e 'sala_recursos'. */
public final class SalaDAO {

    /** Codigo de erro do H2 para violacao de chave unica. */
    private static final int H2_CHAVE_DUPLICADA = 23505;

    private SalaDAO() {
    }

    /**
     * Cadastra uma sala. Ela nasce "active", como o protocolo determina.
     *
     * A sala e os recursos entram na MESMA transacao: se a gravacao de um
     * recurso falhar, a sala tambem nao fica. Sem isso, uma falha no meio
     * deixaria uma sala pela metade, que o 201 ja teria anunciado como completa.
     *
     * @throws DadoDuplicadoException se o nome ja existe (409)
     */
    public static int criar(String nome, int capacidade, String localizacao,
                            List<String> recursos) throws SQLException, DadoDuplicadoException {
        try (Connection c = Banco.conexao()) {
            boolean autoCommitAnterior = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                int id;
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO salas (nome, capacidade, localizacao, estado)"
                                + " VALUES (?, ?, ?, 'active')",
                        Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, nome);
                    ps.setInt(2, capacidade);
                    ps.setString(3, localizacao);
                    ps.executeUpdate();
                    try (ResultSet chaves = ps.getGeneratedKeys()) {
                        chaves.next();
                        id = chaves.getInt(1);
                    }
                }
                gravarRecursos(c, id, recursos);
                c.commit();
                return id;
            } catch (SQLException e) {
                c.rollback();
                if (e.getErrorCode() == H2_CHAVE_DUPLICADA) {
                    throw new DadoDuplicadoException("sala ja cadastrada", e);
                }
                throw e;
            } finally {
                c.setAutoCommit(autoCommitAnterior);
            }
        }
    }

    /** Le uma sala pelo id, ou null se nao existe (404). */
    public static Sala buscarPorId(int id) throws SQLException {
        try (Connection c = Banco.conexao();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id, nome, capacidade, localizacao, estado FROM salas WHERE id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return new Sala(
                        rs.getInt("id"),
                        rs.getString("nome"),
                        rs.getInt("capacidade"),
                        rs.getString("localizacao"),
                        recursosDe(c, id),
                        rs.getString("estado"));
            }
        }
    }

    /**
     * Lista as salas, da mais antiga para a mais nova.
     *
     * @param minCapacidade filtro de capacidade minima, ou null para todas
     */
    public static List<Sala> listar(Integer minCapacidade) throws SQLException {
        String sql = "SELECT id, nome, capacidade, localizacao, estado FROM salas"
                + (minCapacidade == null ? "" : " WHERE capacidade >= ?")
                + " ORDER BY id";
        Map<Integer, Object[]> cruas = new LinkedHashMap<>();
        try (Connection c = Banco.conexao();
             PreparedStatement ps = c.prepareStatement(sql)) {
            if (minCapacidade != null) {
                ps.setInt(1, minCapacidade);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    cruas.put(rs.getInt("id"), new Object[] {
                            rs.getString("nome"),
                            rs.getInt("capacidade"),
                            rs.getString("localizacao"),
                            rs.getString("estado")});
                }
            }

            // Os recursos de TODAS as salas vem numa consulta so, e nao uma por
            // sala: com uma consulta por sala, listar N salas custaria N+1
            // idas ao banco.
            Map<Integer, List<String>> recursos = new LinkedHashMap<>();
            try (PreparedStatement ps2 = c.prepareStatement(
                    "SELECT sala_id, recurso FROM sala_recursos ORDER BY sala_id, ordem");
                 ResultSet rs = ps2.executeQuery()) {
                while (rs.next()) {
                    recursos.computeIfAbsent(rs.getInt("sala_id"), k -> new ArrayList<>())
                            .add(rs.getString("recurso"));
                }
            }

            List<Sala> salas = new ArrayList<>();
            for (Map.Entry<Integer, Object[]> e : cruas.entrySet()) {
                Object[] v = e.getValue();
                salas.add(new Sala(e.getKey(), (String) v[0], (Integer) v[1], (String) v[2],
                        recursos.get(e.getKey()), (String) v[3]));
            }
            return salas;
        }
    }

    /**
     * Altera uma sala. Parametro null significa "nao alterar", que e como a
     * regra 2.11 define o campo vazio na requisicao.
     *
     * O 'recursos' e excecao: ele vem como lista COMPLETA, entao a lista vazia
     * quer dizer "sala sem recurso nenhum" e nao "nao alterar". So o null
     * preserva a lista atual.
     *
     * @throws DadoDuplicadoException se o novo nome ja pertence a outra sala
     */
    public static boolean atualizar(int id, String nome, Integer capacidade, String localizacao,
                                    List<String> recursos, String estado)
            throws SQLException, DadoDuplicadoException {
        List<String> colunas = new ArrayList<>();
        List<Object> valores = new ArrayList<>();
        if (nome != null) {
            colunas.add("nome = ?");
            valores.add(nome);
        }
        if (capacidade != null) {
            colunas.add("capacidade = ?");
            valores.add(capacidade);
        }
        if (localizacao != null) {
            colunas.add("localizacao = ?");
            valores.add(localizacao);
        }
        if (estado != null) {
            colunas.add("estado = ?");
            valores.add(estado);
        }

        try (Connection c = Banco.conexao()) {
            boolean autoCommitAnterior = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                if (!colunas.isEmpty()) {
                    String sql = "UPDATE salas SET " + String.join(", ", colunas) + " WHERE id = ?";
                    try (PreparedStatement ps = c.prepareStatement(sql)) {
                        int i = 1;
                        for (Object valor : valores) {
                            ps.setObject(i++, valor);
                        }
                        ps.setInt(i, id);
                        if (ps.executeUpdate() == 0) {
                            c.rollback();
                            return false;
                        }
                    }
                }
                if (recursos != null) {
                    // Apaga e regrava: a requisicao traz a lista inteira, nao um
                    // delta, entao nao ha o que casar item por item.
                    try (PreparedStatement ps = c.prepareStatement(
                            "DELETE FROM sala_recursos WHERE sala_id = ?")) {
                        ps.setInt(1, id);
                        ps.executeUpdate();
                    }
                    gravarRecursos(c, id, recursos);
                }
                c.commit();
                return true;
            } catch (SQLException e) {
                c.rollback();
                if (e.getErrorCode() == H2_CHAVE_DUPLICADA) {
                    throw new DadoDuplicadoException("sala ja cadastrada", e);
                }
                throw e;
            } finally {
                c.setAutoCommit(autoCommitAnterior);
            }
        }
    }

    /** Remove a sala. Os recursos e as reservas antigas vao com ela (ON DELETE CASCADE). */
    public static boolean remover(int id) throws SQLException {
        try (Connection c = Banco.conexao();
             PreparedStatement ps = c.prepareStatement("DELETE FROM salas WHERE id = ?")) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        }
    }

    public static boolean existeNome(String nome) throws SQLException {
        try (Connection c = Banco.conexao();
             PreparedStatement ps = c.prepareStatement("SELECT 1 FROM salas WHERE nome = ?")) {
            ps.setString(1, nome);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /**
     * Diz se a sala tem reserva ativa de hoje para frente -- a condicao do 409
     * "Sala possui reservas ativas" do delete_room.
     *
     * A comparacao e por DIA, nao por instante: uma reserva de hoje as 8h ainda
     * conta, mesmo que ja tenha passado. Remover a sala apagaria o registro de
     * uma reserva que o dono acabou de usar, e o protocolo oferece caminho
     * melhor para isso -- deixar a sala "inactive".
     */
    public static boolean temReservaFutura(int id) throws SQLException {
        try (Connection c = Banco.conexao();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT 1 FROM reservas"
                             + " WHERE sala_id = ? AND ativa = TRUE AND dia >= ? LIMIT 1")) {
            ps.setInt(1, id);
            ps.setDate(2, Date.valueOf(LocalDate.now()));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /**
     * Os ids das salas com reserva ativa que colide com o periodo informado.
     *
     * Dois periodos colidem quando um comeca antes do outro terminar e termina
     * depois de o outro comecar: {@code inicio < fim_pedido AND fim >
     * inicio_pedido}. As comparacoes sao estritas de proposito -- uma reserva
     * das 14h as 16h e outra das 16h as 18h nao colidem, porque o fim de uma e
     * o inicio da outra.
     */
    public static Set<Integer> ocupadasNoPeriodo(LocalDate dia, LocalTime inicio, LocalTime fim)
            throws SQLException {
        Set<Integer> ocupadas = new HashSet<>();
        try (Connection c = Banco.conexao();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT DISTINCT sala_id FROM reservas"
                             + " WHERE ativa = TRUE AND dia = ?"
                             + " AND inicio < ? AND fim > ?")) {
            ps.setDate(1, Date.valueOf(dia));
            ps.setTime(2, Time.valueOf(fim));
            ps.setTime(3, Time.valueOf(inicio));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ocupadas.add(rs.getInt(1));
                }
            }
        }
        return ocupadas;
    }

    /** Quantas salas existem. Usado pelos testes e pela tela do cliente. */
    public static int contar() throws SQLException {
        try (Connection c = Banco.conexao();
             PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM salas");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static void gravarRecursos(Connection c, int salaId, List<String> recursos)
            throws SQLException {
        if (recursos == null || recursos.isEmpty()) {
            return;
        }
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO sala_recursos (sala_id, ordem, recurso) VALUES (?, ?, ?)")) {
            for (int i = 0; i < recursos.size(); i++) {
                ps.setInt(1, salaId);
                ps.setInt(2, i);
                ps.setString(3, recursos.get(i));
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private static List<String> recursosDe(Connection c, int salaId) throws SQLException {
        List<String> recursos = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT recurso FROM sala_recursos WHERE sala_id = ? ORDER BY ordem")) {
            ps.setInt(1, salaId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    recursos.add(rs.getString(1));
                }
            }
        }
        return recursos;
    }
}
