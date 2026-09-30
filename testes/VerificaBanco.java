import servidor.Senhas;
import servidor.dao.Banco;
import servidor.dao.DadoDuplicadoException;
import servidor.dao.SessaoDAO;
import servidor.dao.UsuarioDAO;
import servidor.modelo.Usuario;

/**
 * Verificacao da camada de banco. Roda num H2 EM MEMORIA, entao nao encosta no
 * arquivo dados/reservasalas nem deixa sujeira para tras.
 */
public class VerificaBanco {

    static int ok = 0, falhas = 0;

    public static void main(String[] args) throws Exception {
        Banco.iniciarComUrl("jdbc:h2:mem:teste_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");

        // --- esquema e administrador inicial ---
        Usuario admin = UsuarioDAO.buscarPorUsuario("admin");
        confere("administrador inicial foi criado", admin != null);
        confere("administrador tem role admin", admin != null && admin.isAdmin());
        confere("existe exatamente 1 administrador", UsuarioDAO.contarAdministradores() == 1);
        confere("created_at sai no formato do protocolo",
                admin != null && admin.getCriadoEmFormatado()
                        .matches("^[0-9]{4}-[0-9]{2}-[0-9]{2} ([01][0-9]|2[0-3]):[0-5][0-9]:[0-5][0-9]$"));

        // --- cadastro (register) ---
        int idJoao = UsuarioDAO.criar("joao", "joao.silva@email.com", "senha123");
        confere("cadastro devolve id", idJoao > 0);
        Usuario joao = UsuarioDAO.buscarPorId(idJoao);
        confere("cadastro nasce com role user (regra 3.6)",
                joao != null && "user".equals(joao.getRole()));
        confere("busca por email encontra", UsuarioDAO.buscarPorEmail("joao.silva@email.com") != null);
        confere("busca por usuario encontra", UsuarioDAO.buscarPorUsuario("joao") != null);
        confere("busca por usuario inexistente devolve null",
                UsuarioDAO.buscarPorUsuario("ninguem") == null);

        // --- duplicidade -> 409 ---
        duplicado("mesmo usuario e recusado", "joao", "outro@email.com", "senha123");
        duplicado("mesmo email e recusado", "outro", "joao.silva@email.com", "senha123");

        // --- autenticacao (login) ---
        confere("autentica com a senha certa",
                UsuarioDAO.autenticar("joao.silva@email.com", "senha123") != null);
        confere("senha errada devolve null",
                UsuarioDAO.autenticar("joao.silva@email.com", "errada") == null);
        confere("email inexistente devolve null (mesma resposta da senha errada)",
                UsuarioDAO.autenticar("naoexiste@email.com", "senha123") == null);
        confere("conferirSenha aceita a senha certa", UsuarioDAO.conferirSenha(idJoao, "senha123"));
        confere("conferirSenha recusa a errada", !UsuarioDAO.conferirSenha(idJoao, "errada"));

        // --- salt: senhas iguais nao geram hashes iguais ---
        String salt1 = Senhas.novoSalt(), salt2 = Senhas.novoSalt();
        confere("salts diferentes a cada cadastro", !salt1.equals(salt2));
        confere("mesma senha com salts diferentes gera hashes diferentes",
                !Senhas.hash("senha123", salt1).equals(Senhas.hash("senha123", salt2)));

        // --- atualizacao (update_user) ---
        UsuarioDAO.atualizar(idJoao, "joaosilva", null);
        confere("troca de usuario funciona", UsuarioDAO.buscarPorUsuario("joaosilva") != null);
        confere("nome antigo deixa de existir", UsuarioDAO.buscarPorUsuario("joao") == null);

        UsuarioDAO.criar("maria", "maria@email.com", "senha456");
        boolean recusou = false;
        try {
            UsuarioDAO.atualizar(idJoao, "maria", null);
        } catch (DadoDuplicadoException e) {
            recusou = true;
        }
        confere("trocar para um usuario em uso e recusado (409)", recusou);

        UsuarioDAO.atualizar(idJoao, null, "novasenha1");
        confere("senha nova passa a valer",
                UsuarioDAO.autenticar("joao.silva@email.com", "novasenha1") != null);
        confere("senha antiga deixa de valer",
                UsuarioDAO.autenticar("joao.silva@email.com", "senha123") == null);
        confere("atualizar sem nada a alterar e sucesso (regra 2.11)",
                UsuarioDAO.atualizar(idJoao, null, null));

        // --- sessoes ---
        String token = SessaoDAO.criar(idJoao);
        confere("token no formato da regra 3.1", token.matches("^[a-f0-9]{64}$"));
        confere("tokens sao distintos", !token.equals(SessaoDAO.criar(UsuarioDAO
                .buscarPorUsuario("maria").getId())));

        Usuario dono = SessaoDAO.validarERenovar(token);
        confere("token valido identifica o dono (regra 3.8)",
                dono != null && dono.getId() == idJoao);
        confere("token inexistente devolve null",
                SessaoDAO.validarERenovar("f".repeat(64)) == null);
        confere("usuario tem sessao ativa (regra 3.5)", SessaoDAO.temSessaoAtiva(idJoao));

        // --- expiracao e renovacao (regra 3.3) ---
        SessaoDAO.envelhecerParaTeste(token, 20);
        confere("sessao de 20 min ainda vale", SessaoDAO.validarERenovar(token) != null);
        confere("uso valido renovou a contagem", SessaoDAO.temSessaoAtiva(idJoao));

        SessaoDAO.envelhecerParaTeste(token, 31);
        confere("sessao de 31 min expirou", SessaoDAO.validarERenovar(token) == null);
        confere("sessao expirada nao bloqueia login novo", !SessaoDAO.temSessaoAtiva(idJoao));

        // --- logout (regra 3.4) ---
        String token2 = SessaoDAO.criar(idJoao);
        confere("logout invalida o token", SessaoDAO.invalidar(token2));
        confere("token invalidado nao volta a valer", SessaoDAO.validarERenovar(token2) == null);
        confere("invalidar duas vezes devolve false", !SessaoDAO.invalidar(token2));

        // --- reinicio do servidor encerra as sessoes anteriores ---
        int idMaria = UsuarioDAO.buscarPorUsuario("maria").getId();
        String tokenA = SessaoDAO.criar(idJoao);
        String tokenB = SessaoDAO.criar(idMaria);
        confere("duas sessoes ativas antes do reinicio",
                SessaoDAO.validarERenovar(tokenA) != null
                        && SessaoDAO.validarERenovar(tokenB) != null);

        // Nao se fixa o numero exato: testes anteriores deixaram outras sessoes
        // ativas, e o que importa e que o reinicio encerra TODAS elas.
        int encerradas = SessaoDAO.invalidarTodas();
        confere("o reinicio encerra as sessoes ativas (encerrou " + encerradas + ")",
                encerradas >= 2);
        confere("token de antes do reinicio nao vale mais",
                SessaoDAO.validarERenovar(tokenA) == null);
        confere("o outro token tambem nao vale",
                SessaoDAO.validarERenovar(tokenB) == null);
        // Este e o ponto da mudanca: sem isso, o login seguinte levaria 409.
        confere("usuario pode entrar de novo depois do reinicio, sem 409",
                !SessaoDAO.temSessaoAtiva(idJoao) && !SessaoDAO.temSessaoAtiva(idMaria));
        confere("encerrar de novo nao encontra nada", SessaoDAO.invalidarTodas() == 0);

        // --- remocao (delete_user) ---
        String token3 = SessaoDAO.criar(idJoao);
        confere("remocao do cadastro funciona", UsuarioDAO.remover(idJoao));
        confere("cadastro removido some", UsuarioDAO.buscarPorId(idJoao) == null);
        confere("sessao cai junto pelo ON DELETE CASCADE",
                SessaoDAO.validarERenovar(token3) == null);

        // --- regra do ultimo administrador ---
        confere("continua com 1 administrador", UsuarioDAO.contarAdministradores() == 1);
        UsuarioDAO.alterarRole(UsuarioDAO.buscarPorUsuario("maria").getId(), "admin");
        confere("promover a admin passa para 2", UsuarioDAO.contarAdministradores() == 2);

        Banco.encerrar();
        System.out.println("\n===== banco: " + ok + " passaram, " + falhas + " falharam =====");
        if (falhas > 0) System.exit(1);
    }

    static void duplicado(String nome, String usuario, String email, String senha) throws Exception {
        try {
            UsuarioDAO.criar(usuario, email, senha);
            confere(nome, false);
        } catch (DadoDuplicadoException e) {
            confere(nome, true);
        }
    }

    static void confere(String nome, boolean passou) {
        if (passou) { ok++; System.out.println("  OK    " + nome); }
        else { falhas++; System.out.println("  FALHA " + nome); }
    }
}
