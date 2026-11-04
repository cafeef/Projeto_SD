package servidor.handlers;

import com.google.gson.JsonObject;

import protocolo.CampoInvalidoException;
import protocolo.Validador;
import servidor.AutorizacaoAdmin;
import servidor.dao.SessaoDAO;
import servidor.dao.UsuarioDAO;
import servidor.modelo.Usuario;

/**
 * admin_delete_user -- remove qualquer usuario.
 *
 * Diferente do delete_user, aqui nao se pede senha: quem confirma a operacao e
 * o poder de admin, nao a senha do alvo.
 *
 * Regra 3.13: remover a conta apaga o cadastro, encerra a sessao e apaga todas
 * as reservas do usuario, passadas e futuras, de modo que o user e o email
 * fiquem livres para um novo register. As sessoes caem pelo ON DELETE CASCADE;
 * as reservas cairao do mesmo jeito quando a tabela existir, na EP-3.
 */
public class AdminDeleteUserHandler extends HandlerAdmin {

    public AdminDeleteUserHandler() {
        super("admin_delete_user", "Dados em formato invalido");
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

        if (usuario.isAdmin() && UsuarioDAO.contarAdministradores() <= 1) {
            return resposta("403", "Nao e possivel remover o ultimo administrador");
        }

        // O token do removido perde o poder junto com a conta (regra 3.14).
        AutorizacaoAdmin.revogar(SessaoDAO.tokenAtivoDoUsuario(usuario.getId()));
        SessaoDAO.invalidarTodasDoUsuario(usuario.getId());
        UsuarioDAO.remover(usuario.getId());

        return resposta("200", "Usuario removido com sucesso");
    }
}
