package servidor.modelo;

import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;

/**
 * Um cadastro de usuario, como esta no banco.
 *
 * Nao carrega a senha: o hash e o salt ficam no DAO e nunca sobem ate os
 * handlers, o que torna impossivel devolver a senha por descuido numa resposta.
 */
public class Usuario {

    /** Formato de 'created_at' na aba Dicionario: AAAA-MM-DD HH:MM:SS. */
    private static final DateTimeFormatter FORMATO_DATA_HORA =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final int id;
    private final String usuario;
    private final String email;
    private final String role;
    private final Timestamp criadoEm;

    public Usuario(int id, String usuario, String email, String role, Timestamp criadoEm) {
        this.id = id;
        this.usuario = usuario;
        this.email = email;
        this.role = role;
        this.criadoEm = criadoEm;
    }

    public int getId() {
        return id;
    }

    /** Corresponde ao campo 'user' do protocolo ('user' e palavra reservada em SQL). */
    public String getUsuario() {
        return usuario;
    }

    public String getEmail() {
        return email;
    }

    public String getRole() {
        return role;
    }

    public boolean isAdmin() {
        return "admin".equals(role);
    }

    /** 'created_at' ja no formato do protocolo. */
    public String getCriadoEmFormatado() {
        return criadoEm == null ? "" : FORMATO_DATA_HORA.format(criadoEm.toLocalDateTime());
    }

    @Override
    public String toString() {
        return "Usuario{" + usuario + ", " + email + ", " + role + "}";
    }
}
