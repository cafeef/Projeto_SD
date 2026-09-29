package servidor.handlers;

import com.google.gson.JsonObject;

import protocolo.CampoInvalidoException;
import protocolo.Resposta;
import protocolo.Validador;
import servidor.Handler;
import servidor.dao.DadoDuplicadoException;
import servidor.dao.UsuarioDAO;

/**
 * register -- cadastro de usuario comum.
 *
 * Junto com o login, e a unica operacao que nao envia token (regra 2.5).
 * Todo cadastro nasce com role "user" (regra 3.6) e NAO cria sessao: o cliente
 * precisa fazer login em seguida.
 */
public class RegisterHandler implements Handler {

    @Override
    public JsonObject tratar(JsonObject requisicao) throws Exception {
        String email;
        String usuario;
        String senha;
        try {
            email = Validador.obrigatorio(requisicao, "email");
            usuario = Validador.obrigatorio(requisicao, "user");
            senha = Validador.obrigatorio(requisicao, "password");
        } catch (CampoInvalidoException e) {
            return Resposta.de("register", "400", "Dados de cadastro em formato invalido");
        }

        try {
            UsuarioDAO.criar(usuario, email, senha);
        } catch (DadoDuplicadoException e) {
            // Vem da restricao UNIQUE, nao de um SELECT previo: dois cadastros
            // simultaneos do mesmo usuario nao passam os dois.
            return Resposta.de("register", "409", "Usuario ou email ja cadastrado");
        }

        return Resposta.de("register", "201", "Usuario cadastrado com sucesso");
    }
}
