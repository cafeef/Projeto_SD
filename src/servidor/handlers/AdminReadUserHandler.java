package servidor.handlers;

import com.google.gson.JsonObject;

import protocolo.CampoInvalidoException;
import protocolo.Validador;
import servidor.dao.UsuarioDAO;
import servidor.modelo.Usuario;

/**
 * admin_read_user -- le o cadastro de qualquer usuario.
 *
 * O alvo vem de 'target_user'; o autor continua saindo do token (regra 3.8).
 * Um admin pode ler o proprio cadastro por aqui, informando o proprio user.
 */
public class AdminReadUserHandler extends HandlerAdmin {

    public AdminReadUserHandler() {
        super("admin_read_user", "Dados em formato invalido");
    }

    @Override
    protected JsonObject tratarComoAdmin(JsonObject requisicao, Usuario autor, String token)
            throws Exception {
        String alvo;
        try {
            alvo = Validador.obrigatorio(requisicao, "target_user");
        } catch (CampoInvalidoException e) {
            return erro400();
        }

        Usuario usuario = UsuarioDAO.buscarPorUsuario(alvo);
        if (usuario == null) {
            return resposta("404", "Usuario nao encontrado");
        }

        JsonObject resposta = resposta("200", "Consulta realizada com sucesso");
        resposta.addProperty("user", usuario.getUsuario());
        resposta.addProperty("email", usuario.getEmail());
        resposta.addProperty("role", usuario.getRole());
        resposta.addProperty("created_at", usuario.getCriadoEmFormatado());
        return resposta;
    }
}
