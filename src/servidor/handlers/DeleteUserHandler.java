package servidor.handlers;

import com.google.gson.JsonObject;

import protocolo.CampoInvalidoException;
import protocolo.Validador;
import servidor.dao.SessaoDAO;
import servidor.dao.UsuarioDAO;
import servidor.modelo.Usuario;

/**
 * delete_user -- remove o proprio cadastro.
 *
 * O alvo vem do token e a senha confirma a operacao. Sequencia de checagens:
 *
 *   1. token fora de formato ........ 400
 *   2. token invalido ou expirado ... 401   (as duas na classe base)
 *   3. senha fora de formato ........ 400
 *   4. senha incorreta .............. 401
 *   5. ultimo administrador ......... 403
 *
 * O passo 4 merece atencao: a planilha diz "Senha incorreta responde 401" e
 * define uma unica mensagem de 401 para esta operacao, "Token invalido ou
 * expirado". Entao e essa a mensagem que sai, ainda que o token esteja bom e o
 * problema seja a senha. Foi assim que ficou combinado, e mudar por conta
 * propria quebraria a interoperabilidade com os outros grupos.
 */
public class DeleteUserHandler extends HandlerAutenticado {

    public DeleteUserHandler() {
        super("delete_user", "Senha em formato invalido");
    }

    @Override
    protected JsonObject tratarAutenticado(JsonObject requisicao, Usuario autor, String token)
            throws Exception {
        String senha;
        try {
            senha = Validador.obrigatorio(requisicao, "password");
        } catch (CampoInvalidoException e) {
            return erro400();
        }

        if (!UsuarioDAO.conferirSenha(autor.getId(), senha)) {
            return resposta("401", "Token invalido ou expirado");
        }

        // Sem esta regra, o sistema poderia ficar sem nenhum administrador e as
        // operacoes admin_* se tornariam inalcancaveis para sempre.
        if (autor.isAdmin() && UsuarioDAO.contarAdministradores() <= 1) {
            return resposta("403", "Nao e possivel remover o ultimo administrador");
        }

        // As sessoes caem junto pelo ON DELETE CASCADE; a invalidacao explicita
        // deixa o efeito visivel mesmo que o esquema mude no futuro.
        SessaoDAO.invalidarTodasDoUsuario(autor.getId());
        UsuarioDAO.remover(autor.getId());

        return resposta("200", "Usuario removido com sucesso");
    }
}
