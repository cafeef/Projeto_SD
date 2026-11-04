package servidor.handlers;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import servidor.dao.UsuarioDAO;
import servidor.modelo.Usuario;

import java.util.List;

/**
 * admin_list_users -- lista todos os cadastros.
 *
 * Nunca devolve senha nem hash: o item de 'users' tem exatamente user, email e
 * role, como o esquema Usuario do protocolo define.
 *
 * Lista vazia nao e erro -- responde 200 com count "0" e users vazio. Na
 * pratica ela nunca fica vazia, porque o administrador inicial sempre existe.
 */
public class AdminListUsersHandler extends HandlerAdmin {

    public AdminListUsersHandler() {
        super("admin_list_users", "Token em formato invalido");
    }

    @Override
    protected JsonObject tratarComoAdmin(JsonObject requisicao, Usuario autor, String token)
            throws Exception {
        List<Usuario> usuarios = UsuarioDAO.listarTodos();

        JsonArray lista = new JsonArray();
        for (Usuario u : usuarios) {
            JsonObject item = new JsonObject();
            item.addProperty("user", u.getUsuario());
            item.addProperty("email", u.getEmail());
            item.addProperty("role", u.getRole());
            lista.add(item);
        }

        JsonObject resposta = resposta("200", "Consulta realizada com sucesso");
        // Todos os valores sao strings (regra 2.7), inclusive a contagem.
        resposta.addProperty("count", String.valueOf(usuarios.size()));
        resposta.add("users", lista);
        return resposta;
    }
}
