package servidor.handlers;

import com.google.gson.JsonObject;

import servidor.modelo.Usuario;

/**
 * read_user -- devolve o proprio cadastro.
 *
 * Nao existe campo de usuario alvo: o autor vem do token (regra 3.8). A senha
 * nunca e devolvida -- e o modelo Usuario sequer a carrega, entao nao ha como
 * vazar por descuido.
 */
public class ReadUserHandler extends HandlerAutenticado {

    public ReadUserHandler() {
        super("read_user", "Token em formato invalido");
    }

    @Override
    protected JsonObject tratarAutenticado(JsonObject requisicao, Usuario autor, String token) {
        JsonObject resposta = resposta("200", "Consulta realizada com sucesso");
        resposta.addProperty("user", autor.getUsuario());
        resposta.addProperty("email", autor.getEmail());
        resposta.addProperty("role", autor.getRole());
        resposta.addProperty("created_at", autor.getCriadoEmFormatado());
        return resposta;
    }
}
