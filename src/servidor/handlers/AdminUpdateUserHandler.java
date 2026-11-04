package servidor.handlers;

import com.google.gson.JsonObject;

import protocolo.CampoInvalidoException;
import protocolo.Validador;
import servidor.AutorizacaoAdmin;
import servidor.dao.DadoDuplicadoException;
import servidor.dao.SessaoDAO;
import servidor.dao.UsuarioDAO;
import servidor.modelo.Usuario;

/**
 * admin_update_user -- altera user, senha e/ou perfil de qualquer usuario.
 *
 * Vale tudo do update_user ("" nao altera, a chave 'email' e proibida, pelo
 * menos um campo precisa mudar) e mais a alteracao de 'role'.
 *
 * Duas regras proprias:
 *
 *  - rebaixar o ultimo administrador responde 403, pela mesma razao que o
 *    delete_user protege o ultimo admin: sem nenhum, as operacoes admin_* se
 *    tornariam inalcancaveis para sempre;
 *  - mudar o perfil mexe na whitelist na hora (regra 3.14). Se o alvo estiver
 *    logado, o token que ele ja tem na mao ganha ou perde o poder sem que ele
 *    precise entrar de novo.
 *
 * Sobre 'user', 'password' e 'role' serem obrigatorios: o protocolo marca os
 * tres como required nesta operacao, justamente porque "" ja e a forma de dizer
 * "nao alterar". Entao a chave ausente e 400, pela regra 2.10.
 */
public class AdminUpdateUserHandler extends HandlerAdmin {

    public AdminUpdateUserHandler() {
        super("admin_update_user", "Dados em formato invalido");
    }

    @Override
    protected JsonObject tratarComoAdmin(JsonObject requisicao, Usuario autor, String token)
            throws Exception {
        String alvo;
        String novoUsuario;
        String novaSenha;
        String novoRole;
        try {
            // RNF 5.c: o email e imutavel, nem o admin muda.
            Validador.proibido(requisicao, "email");

            alvo = Validador.obrigatorio(requisicao, "target_user");
            novoUsuario = Validador.opcionalAtualizacao(requisicao, "user");
            novaSenha = Validador.opcionalAtualizacao(requisicao, "password");
            novoRole = Validador.opcionalAtualizacao(requisicao, "role");
        } catch (CampoInvalidoException e) {
            return erro400();
        }

        if (novoUsuario == null || novaSenha == null || novoRole == null) {
            return erro400();
        }

        // Regra 2.13: com os tres vazios nada mudaria, e um 200 seria mentira.
        if (novoUsuario.isEmpty() && novaSenha.isEmpty() && novoRole.isEmpty()) {
            return resposta("400", "Nenhum dado para atualizar");
        }

        Usuario usuario = UsuarioDAO.buscarPorUsuario(alvo);
        if (usuario == null) {
            return resposta("404", "Usuario nao encontrado");
        }

        boolean rebaixando = !novoRole.isEmpty()
                && "user".equals(novoRole) && usuario.isAdmin();
        if (rebaixando && UsuarioDAO.contarAdministradores() <= 1) {
            return resposta("403", "Nao e possivel remover o ultimo administrador");
        }

        try {
            UsuarioDAO.atualizar(usuario.getId(),
                    novoUsuario.isEmpty() ? null : novoUsuario,
                    novaSenha.isEmpty() ? null : novaSenha);
        } catch (DadoDuplicadoException e) {
            return resposta("409", "Usuario ja esta em uso");
        }

        if (!novoRole.isEmpty() && !novoRole.equals(usuario.getRole())) {
            UsuarioDAO.alterarRole(usuario.getId(), novoRole);
            // Regra 3.14: a mudanca vale na hora para quem ja esta logado.
            String tokenDoAlvo = SessaoDAO.tokenAtivoDoUsuario(usuario.getId());
            if (tokenDoAlvo != null) {
                AutorizacaoAdmin.sincronizar(tokenDoAlvo, novoRole);
            }
        }

        return resposta("200", "Dados atualizados com sucesso");
    }
}
