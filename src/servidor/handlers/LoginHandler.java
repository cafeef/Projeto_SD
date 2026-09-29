package servidor.handlers;

import com.google.gson.JsonObject;

import protocolo.CampoInvalidoException;
import protocolo.Resposta;
import protocolo.Validador;
import servidor.Handler;
import servidor.dao.SessaoDAO;
import servidor.dao.UsuarioDAO;
import servidor.modelo.Usuario;

/**
 * login -- autentica por email e senha e devolve token e role.
 *
 * Duas decisoes vindas da planilha:
 *
 *  - email inexistente e senha errada respondem a MESMA mensagem 401, para nao
 *    revelar quais emails estao cadastrados;
 *  - a checagem de sessao ativa (409) vem DEPOIS da checagem de credenciais,
 *    senao quem errasse a senha descobriria, pelo 409, que aquele email existe
 *    e esta logado -- exatamente o que a regra anterior quer evitar.
 */
public class LoginHandler implements Handler {

    @Override
    public JsonObject tratar(JsonObject requisicao) throws Exception {
        String email;
        String senha;
        try {
            email = Validador.obrigatorio(requisicao, "email");
            senha = Validador.obrigatorio(requisicao, "password");
        } catch (CampoInvalidoException e) {
            return Resposta.de("login", "400", "Email ou senha em formato invalido");
        }

        Usuario usuario = UsuarioDAO.autenticar(email, senha);
        if (usuario == null) {
            return Resposta.de("login", "401", "Email ou senha incorretos");
        }

        // Regra 3.5: uma sessao ativa por usuario. Sessao vencida nao bloqueia.
        if (SessaoDAO.temSessaoAtiva(usuario.getId())) {
            return Resposta.de("login", "409", "Usuario ja possui sessao ativa");
        }

        String token = SessaoDAO.criar(usuario.getId());   // regra 3.1

        JsonObject resposta = Resposta.de("login", "200", "Login realizado com sucesso");
        resposta.addProperty("token", token);
        resposta.addProperty("role", usuario.getRole());
        return resposta;
    }
}
