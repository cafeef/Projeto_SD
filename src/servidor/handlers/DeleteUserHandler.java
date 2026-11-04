package servidor.handlers;

import com.google.gson.JsonObject;

import protocolo.CampoInvalidoException;
import protocolo.Validador;
import servidor.AutorizacaoAdmin;
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
 *   4. senha incorreta .............. 403
 *   5. ultimo administrador ......... 403
 *
 * O passo 4 mudou na versao 2.1 do protocolo (regra 3.9). Antes respondia 401, e
 * o 401 fazia o cliente descartar um token que o servidor continuava tratando
 * como ativo: o login seguinte batia em 409 e a conta ficava inacessivel por ate
 * 30 minutos. Agora responde 403 "Senha incorreta" e a sessao segue de pe.
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

        // Regra 3.9: a senha errada nao derruba a sessao, so nega a remocao.
        if (!UsuarioDAO.conferirSenha(autor.getId(), senha)) {
            return resposta("403", "Senha incorreta");
        }

        // Sem esta regra, o sistema poderia ficar sem nenhum administrador e as
        // operacoes admin_* se tornariam inalcancaveis para sempre.
        if (autor.isAdmin() && UsuarioDAO.contarAdministradores() <= 1) {
            return resposta("403", "Nao e possivel remover o ultimo administrador");
        }

        // As sessoes caem junto pelo ON DELETE CASCADE; a invalidacao explicita
        // deixa o efeito visivel mesmo que o esquema mude no futuro.
        AutorizacaoAdmin.revogar(token);   // regra 3.14
        SessaoDAO.invalidarTodasDoUsuario(autor.getId());
        UsuarioDAO.remover(autor.getId());

        return resposta("200", "Usuario removido com sucesso");
    }
}
