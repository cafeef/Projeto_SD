package servidor.handlers;

import com.google.gson.JsonObject;

import servidor.dao.SessaoDAO;
import servidor.modelo.Usuario;

/**
 * logout -- invalida o token da sessao (regra 3.4).
 *
 * Um token ja invalidado nao passa pela validacao da classe base e recebe 401,
 * que e o comportamento descrito na planilha.
 */
public class LogoutHandler extends HandlerAutenticado {

    public LogoutHandler() {
        super("logout", "Token em formato invalido");
    }

    @Override
    protected JsonObject tratarAutenticado(JsonObject requisicao, Usuario autor, String token)
            throws Exception {
        SessaoDAO.invalidar(token);
        return resposta("200", "Logout realizado com sucesso");
    }
}
