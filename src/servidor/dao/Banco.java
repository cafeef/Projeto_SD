package servidor.dao;

import servidor.Senhas;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Conexao com o banco H2 embutido e criacao do esquema.
 *
 * O H2 roda dentro da propria JVM do servidor: nao ha processo separado, nao ha
 * instalacao e o arquivo do banco nasce na primeira execucao. E por isso que o
 * esquema e criado aqui em codigo, com CREATE TABLE IF NOT EXISTS, em vez de
 * ser entregue como um arquivo .mv.db pronto -- assim o projeto roda numa
 * maquina limpa sem depender de nada que esteja so na nossa.
 */
public final class Banco {

    /** Caminho relativo: o banco fica dentro da pasta do projeto, em dados/. */
    private static final String CAMINHO_PADRAO = "./dados/reservasalas";

    /** DB_CLOSE_DELAY=-1 mantem o banco aberto enquanto a JVM viver. */
    private static final String OPCOES = ";DB_CLOSE_DELAY=-1";

    private static final String USUARIO_BANCO = "sa";
    private static final String SENHA_BANCO = "";

    private static String url;

    private Banco() {
    }

    /** Abre (ou cria) o banco em arquivo, dentro de dados/. */
    public static synchronized void iniciar() throws SQLException {
        new File("dados").mkdirs();
        iniciarComUrl("jdbc:h2:file:" + CAMINHO_PADRAO + OPCOES);
    }

    /**
     * Abre o banco numa URL especifica. Os testes usam
     * {@code jdbc:h2:mem:<nome>;DB_CLOSE_DELAY=-1} para rodar em memoria, sem
     * tocar no banco de verdade.
     */
    public static synchronized void iniciarComUrl(String urlDesejada) throws SQLException {
        url = urlDesejada;
        try (Connection c = conexao()) {
            criarEsquema(c);
            semearAdministrador(c);
        } catch (SQLException e) {
            // 90020: o arquivo do banco ja esta aberto por outro processo.
            if (e.getErrorCode() == 90020) {
                throw new SQLException("O banco ja esta em uso por outro servidor."
                        + " Encerre o outro servidor antes de iniciar este.", e.getSQLState(),
                        e.getErrorCode(), e);
            }
            throw e;
        }
    }

    /**
     * Abre uma conexao nova. Quem chama fecha, de preferencia com
     * try-with-resources. O H2 embutido aceita varias conexoes simultaneas da
     * mesma JVM, que e o que a arquitetura de uma thread por cliente exige.
     */
    public static Connection conexao() throws SQLException {
        if (url == null) {
            throw new IllegalStateException("Banco.iniciar() nao foi chamado");
        }
        return DriverManager.getConnection(url, USUARIO_BANCO, SENHA_BANCO);
    }

    /** Fecha o banco de verdade, desfazendo o DB_CLOSE_DELAY=-1. */
    public static synchronized void encerrar() {
        if (url == null) {
            return;
        }
        try (Connection c = conexao(); Statement st = c.createStatement()) {
            st.execute("SHUTDOWN");
        } catch (SQLException e) {
            System.err.println("aviso ao encerrar o banco: " + e.getMessage());
        }
        url = null;
    }

    public static synchronized boolean estaIniciado() {
        return url != null;
    }

    private static void criarEsquema(Connection c) throws SQLException {
        try (Statement st = c.createStatement()) {
            // 'usuario' e nao 'user': USER e palavra reservada em SQL.
            // As restricoes UNIQUE sao o que entrega o status 409 de graca --
            // basta capturar a violacao de integridade no DAO.
            st.execute("""
                    CREATE TABLE IF NOT EXISTS usuarios (
                        id         INT AUTO_INCREMENT PRIMARY KEY,
                        usuario    VARCHAR(30)  NOT NULL UNIQUE,
                        email      VARCHAR(100) NOT NULL UNIQUE,
                        senha_hash VARCHAR(64)  NOT NULL,
                        salt       VARCHAR(32)  NOT NULL,
                        role       VARCHAR(5)   NOT NULL DEFAULT 'user',
                        criado_em  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);

            // Uma linha por token emitido. O historico fica: um token invalidado
            // nunca volta a valer (regra 3.4), entao nao se apaga, marca-se.
            st.execute("""
                    CREATE TABLE IF NOT EXISTS sessoes (
                        token      CHAR(64) PRIMARY KEY,
                        usuario_id INT       NOT NULL,
                        criado_em  TIMESTAMP NOT NULL,
                        ultimo_uso TIMESTAMP NOT NULL,
                        ativa      BOOLEAN   NOT NULL DEFAULT TRUE,
                        CONSTRAINT fk_sessao_usuario FOREIGN KEY (usuario_id)
                            REFERENCES usuarios(id) ON DELETE CASCADE
                    )
                    """);

            st.execute("CREATE INDEX IF NOT EXISTS idx_sessoes_usuario ON sessoes(usuario_id, ativa)");
        }
    }

    /**
     * Cria um administrador inicial quando nao existe nenhum.
     *
     * Sem isso nao ha como exercitar as regras que dependem de role "admin" --
     * inclusive a do 403 "Nao e possivel remover o ultimo administrador", que e
     * item avaliado no delete_user. Todo cadastro feito por 'register' nasce
     * como "user" (regra 3.6), entao o primeiro admin tem que vir daqui.
     */
    private static void semearAdministrador(Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT COUNT(*) FROM usuarios WHERE role = 'admin'");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            if (rs.getInt(1) > 0) {
                return;
            }
        }
        String salt = Senhas.novoSalt();
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO usuarios (usuario, email, senha_hash, salt, role)"
                        + " VALUES (?, ?, ?, ?, 'admin')")) {
            ps.setString(1, "admin");
            ps.setString(2, "admin@email.com");
            ps.setString(3, Senhas.hash("admin", salt));
            ps.setString(4, salt);
            ps.executeUpdate();
        }
        System.out.println("Banco: administrador inicial criado"
                + " (user \"admin\", email \"admin@email.com\", senha \"admin\").");
    }
}
