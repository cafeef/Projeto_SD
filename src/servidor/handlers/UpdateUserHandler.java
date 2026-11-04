package servidor.handlers;

import com.google.gson.JsonObject;

import protocolo.CampoInvalidoException;
import protocolo.Validador;
import servidor.dao.DadoDuplicadoException;
import servidor.dao.UsuarioDAO;
import servidor.modelo.Usuario;

/**
 * update_user -- altera o proprio cadastro.
 *
 * Regras especificas:
 *
 *  - campo vazio ("") significa "nao alterar"; campo nulo responde 400 (regra 2.11)
 *  - 'user' e 'password' vazios ao mesmo tempo respondem 400 "Nenhum dado para
 *    atualizar" (regra 2.13): alterar exige mudar pelo menos um campo
 *  - o email NAO pode ser alterado: se a chave 'email' vier, responde 400 (RNF 5.c)
 *  - ao trocar o proprio 'user', o token continua valido -- e por isso que o
 *    token identifica a sessao por id interno, e nao pelo nome do usuario
 */
public class UpdateUserHandler extends HandlerAutenticado {

    public UpdateUserHandler() {
        super("update_user", "Dados em formato invalido");
    }

    @Override
    protected JsonObject tratarAutenticado(JsonObject requisicao, Usuario autor, String token)
            throws Exception {
        String novoUsuario;
        String novaSenha;
        try {
            // RNF 5.c: o email e imutavel. A simples presenca da chave e 400,
            // mesmo que o valor seja o email atual -- e o que a planilha manda.
            Validador.proibido(requisicao, "email");

            novoUsuario = Validador.opcionalAtualizacao(requisicao, "user");
            novaSenha = Validador.opcionalAtualizacao(requisicao, "password");
        } catch (CampoInvalidoException e) {
            return erro400();
        }

        // "" quer dizer "nao alterar", que para o DAO e o mesmo que nao informar.
        if (novoUsuario != null && novoUsuario.isEmpty()) {
            novoUsuario = null;
        }
        if (novaSenha != null && novaSenha.isEmpty()) {
            novaSenha = null;
        }

        // Regra 2.13: sem nada a alterar o 200 seria mentira -- nada mudaria.
        // Mensagem propria, diferente do 400 de formato que erro400() devolve.
        if (novoUsuario == null && novaSenha == null) {
            return resposta("400", "Nenhum dado para atualizar");
        }

        try {
            UsuarioDAO.atualizar(autor.getId(), novoUsuario, novaSenha);
        } catch (DadoDuplicadoException e) {
            return resposta("409", "Usuario ja esta em uso");
        }

        return resposta("200", "Dados atualizados com sucesso");
    }
}
