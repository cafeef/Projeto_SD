import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

import servidor.AutorizacaoAdmin;
import servidor.Senhas;
import servidor.dao.Banco;
import servidor.dao.DadoDuplicadoException;
import servidor.dao.SalaDAO;
import servidor.dao.SessaoDAO;
import servidor.dao.UsuarioDAO;
import servidor.modelo.Sala;
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
        // Email combinado entre todos os grupos (regra 3.15): e com esta conta que
        // os outros servidores -- e o testador oficial -- entram no nosso.
        confere("administrador inicial usa o email combinado admin@admin.com",
                admin != null && "admin@admin.com".equals(admin.getEmail()));
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

        // --- regra 3.11: dois logins simultaneos nao criam duas sessoes ---
        // Enquanto checar e criar eram duas chamadas separadas, as duas threads
        // passavam pelo temSessaoAtiva antes de qualquer uma gravar o token, e as
        // duas sessoes nasciam. Agora uma leva o token e a outra leva null (409).
        final String[] obtidos = new String[2];
        CountDownLatch largada = new CountDownLatch(1);
        Thread[] corredores = new Thread[2];
        for (int i = 0; i < corredores.length; i++) {
            final int pos = i;
            corredores[i] = new Thread(() -> {
                try {
                    largada.await();
                    obtidos[pos] = SessaoDAO.criarSeSemSessaoAtiva(idJoao);
                } catch (Exception e) {
                    obtidos[pos] = null;
                }
            });
            corredores[i].start();
        }
        largada.countDown();
        for (Thread corredor : corredores) {
            corredor.join();
        }
        confere("dois logins simultaneos: exatamente um recebe token (regra 3.11)",
                (obtidos[0] == null) != (obtidos[1] == null));
        SessaoDAO.invalidarTodasDoUsuario(idJoao);
        confere("depois do logout da sessao vencedora o login volta a passar",
                SessaoDAO.criarSeSemSessaoAtiva(idJoao) != null);
        SessaoDAO.invalidarTodasDoUsuario(idJoao);

        // --- whitelist de tokens de admin (regra 3.14) ---
        String tokenAdm = SessaoDAO.criarSeSemSessaoAtiva(idJoao);
        confere("tokenAtivoDoUsuario acha a sessao aberta",
                tokenAdm != null && tokenAdm.equals(SessaoDAO.tokenAtivoDoUsuario(idJoao)));
        AutorizacaoAdmin.limpar();
        confere("token sem poder por padrao", !AutorizacaoAdmin.temPoder(tokenAdm));
        AutorizacaoAdmin.conceder(tokenAdm);
        confere("conceder da poder ao token", AutorizacaoAdmin.temPoder(tokenAdm));
        AutorizacaoAdmin.sincronizar(tokenAdm, "user");
        confere("rebaixar tira o poder do mesmo token",
                !AutorizacaoAdmin.temPoder(tokenAdm));
        AutorizacaoAdmin.sincronizar(tokenAdm, "admin");
        confere("promover devolve o poder sem token novo",
                AutorizacaoAdmin.temPoder(tokenAdm));
        // Nenhum token sobrevive ao reinicio, logo nenhum poder tambem.
        AutorizacaoAdmin.limpar();
        confere("limpar esvazia a lista",
                AutorizacaoAdmin.tamanho() == 0 && !AutorizacaoAdmin.temPoder(tokenAdm));
        SessaoDAO.invalidarTodasDoUsuario(idJoao);
        confere("sem sessao ativa nao ha token para promover",
                SessaoDAO.tokenAtivoDoUsuario(idJoao) == null);

        // ================= Salas (EP-2) =================

        confere("banco novo comeca sem sala", SalaDAO.contar() == 0);

        int idB12 = SalaDAO.criar("Sala de Estudo B12", 12, "Bloco B - 1 andar",
                Arrays.asList("projetor", "tv"));
        confere("cadastro de sala devolve id", idB12 > 0);

        Sala b12 = SalaDAO.buscarPorId(idB12);
        confere("sala gravada volta com os dados certos",
                b12 != null && "Sala de Estudo B12".equals(b12.getNome())
                        && b12.getCapacidade() == 12
                        && "Bloco B - 1 andar".equals(b12.getLocalizacao()));
        confere("sala nasce active", b12 != null && b12.estaAtiva());
        // A ordem dos recursos e a que o cliente enviou, e nao a alfabetica:
        // e por isso que sala_recursos tem a coluna 'ordem'.
        confere("recursos voltam na ordem enviada",
                b12 != null && b12.getRecursos().equals(Arrays.asList("projetor", "tv")));
        confere("sala inexistente devolve null", SalaDAO.buscarPorId(999999) == null);

        // O UNIQUE no nome e o que entrega o 409 "Sala ja cadastrada".
        salaDuplicada("sala com nome repetido e recusada", "Sala de Estudo B12", 8, "Bloco C");

        int idVazia = SalaDAO.criar("Sala Sem Recurso", 4, "Bloco A", List.of());
        Sala vazia = SalaDAO.buscarPorId(idVazia);
        confere("sala sem recurso volta com lista vazia, nao nula",
                vazia != null && vazia.getRecursos().isEmpty());

        // --- listagem e filtro de capacidade ---
        confere("listagem traz as duas salas", SalaDAO.listar(null).size() == 2);
        confere("listagem vem ordenada por id",
                SalaDAO.listar(null).get(0).getId() == idB12);
        List<Sala> grandes = SalaDAO.listar(10);
        confere("min_capacity filtra as menores",
                grandes.size() == 1 && grandes.get(0).getId() == idB12);
        confere("min_capacity acima de todas devolve lista vazia",
                SalaDAO.listar(1000).isEmpty());
        confere("listagem carrega os recursos de cada sala",
                SalaDAO.listar(null).get(0).getRecursos().size() == 2);

        // --- atualizacao: null mantem, valor troca ---
        SalaDAO.atualizar(idB12, null, 16, null, null, null);
        Sala depoisDaCapacidade = SalaDAO.buscarPorId(idB12);
        confere("capacidade trocada", depoisDaCapacidade.getCapacidade() == 16);
        confere("nome preservado quando vem null",
                "Sala de Estudo B12".equals(depoisDaCapacidade.getNome()));
        confere("recursos preservados quando a lista vem null",
                depoisDaCapacidade.getRecursos().size() == 2);

        // A lista de recursos chega COMPLETA: a vazia significa "nenhum
        // recurso", e nao "nao alterar" -- so o null preserva.
        SalaDAO.atualizar(idB12, null, null, null, List.of(), null);
        confere("lista vazia apaga os recursos",
                SalaDAO.buscarPorId(idB12).getRecursos().isEmpty());

        SalaDAO.atualizar(idB12, null, null, null, Arrays.asList("quadro", "tv", "projetor"), null);
        confere("lista nova substitui a antiga inteira",
                SalaDAO.buscarPorId(idB12).getRecursos()
                        .equals(Arrays.asList("quadro", "tv", "projetor")));

        SalaDAO.atualizar(idB12, null, null, null, null, "inactive");
        confere("estado inactive e gravado", !SalaDAO.buscarPorId(idB12).estaAtiva());
        SalaDAO.atualizar(idB12, null, null, null, null, "active");

        salaRenomeadaDuplicada("renomear para um nome ja usado e recusado", idB12, "Sala Sem Recurso");
        confere("a recusa nao deixou o nome pela metade",
                "Sala de Estudo B12".equals(SalaDAO.buscarPorId(idB12).getNome()));
        confere("atualizar sala inexistente devolve false",
                !SalaDAO.atualizar(999999, "Outro Nome", null, null, null, null));

        confere("existeNome encontra a sala", SalaDAO.existeNome("Sala de Estudo B12"));
        confere("existeNome nao inventa sala", !SalaDAO.existeNome("Sala Que Nao Existe"));

        // --- reservas: sustentam o 'available' e o 409 do delete_room ---
        confere("sala nova nao tem reserva futura", !SalaDAO.temReservaFutura(idB12));
        Set<Integer> livres = SalaDAO.ocupadasNoPeriodo(
                LocalDate.now().plusDays(1), LocalTime.of(14, 0), LocalTime.of(16, 0));
        confere("sem reserva nenhuma sala aparece ocupada", livres.isEmpty());

        LocalDate amanha = LocalDate.now().plusDays(1);
        inserirReserva(idB12, idJoao, amanha, "14:00", "16:00");
        confere("reserva futura bloqueia a remocao da sala",
                SalaDAO.temReservaFutura(idB12));
        confere("a outra sala continua livre para remover",
                !SalaDAO.temReservaFutura(idVazia));

        // Colisao: os limites sao estritos, entao encostar nao e colidir.
        confere("periodo identico colide",
                SalaDAO.ocupadasNoPeriodo(amanha, LocalTime.of(14, 0), LocalTime.of(16, 0))
                        .contains(idB12));
        confere("periodo sobreposto em parte colide",
                SalaDAO.ocupadasNoPeriodo(amanha, LocalTime.of(15, 0), LocalTime.of(17, 0))
                        .contains(idB12));
        confere("periodo que engloba a reserva colide",
                SalaDAO.ocupadasNoPeriodo(amanha, LocalTime.of(13, 0), LocalTime.of(18, 0))
                        .contains(idB12));
        confere("periodo que termina quando a reserva comeca NAO colide",
                !SalaDAO.ocupadasNoPeriodo(amanha, LocalTime.of(12, 0), LocalTime.of(14, 0))
                        .contains(idB12));
        confere("periodo que comeca quando a reserva termina NAO colide",
                !SalaDAO.ocupadasNoPeriodo(amanha, LocalTime.of(16, 0), LocalTime.of(18, 0))
                        .contains(idB12));
        confere("outro dia nao colide",
                SalaDAO.ocupadasNoPeriodo(amanha.plusDays(1), LocalTime.of(14, 0),
                        LocalTime.of(16, 0)).isEmpty());

        // --- remocao de sala ---
        confere("remocao de sala funciona", SalaDAO.remover(idVazia));
        confere("sala removida some", SalaDAO.buscarPorId(idVazia) == null);
        confere("o nome volta a ficar livre", !SalaDAO.existeNome("Sala Sem Recurso"));
        confere("remover sala inexistente devolve false", !SalaDAO.remover(999999));

        // A sala com agenda e removivel no banco; quem recusa e o handler, com
        // o 409. Aqui confirmamos que a reserva vai junto, sem deixar orfa.
        confere("remover a sala leva a reserva com ela (ON DELETE CASCADE)",
                SalaDAO.remover(idB12) && contarReservas() == 0);

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

    static void salaDuplicada(String nome, String sala, int capacidade, String local)
            throws Exception {
        try {
            SalaDAO.criar(sala, capacidade, local, List.of());
            confere(nome, false);
        } catch (DadoDuplicadoException e) {
            confere(nome, true);
        }
    }

    static void salaRenomeadaDuplicada(String nome, int id, String novoNome) throws Exception {
        try {
            SalaDAO.atualizar(id, novoNome, null, null, null, null);
            confere(nome, false);
        } catch (DadoDuplicadoException e) {
            confere(nome, true);
        }
    }

    /**
     * Grava uma reserva direto no banco.
     *
     * A EP-3 vai ter um ReservaDAO; enquanto ele nao existe, o INSERT na mao e o
     * que permite exercitar de verdade as consultas que a EP-2 ja depende dele
     * -- o 'available' do list_rooms e o 409 do delete_room. A alternativa seria
     * testar as duas com a tabela sempre vazia, que nao prova nada.
     */
    static void inserirReserva(int salaId, int usuarioId, LocalDate dia, String inicio, String fim)
            throws Exception {
        try (Connection c = Banco.conexao();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO reservas (sala_id, usuario_id, dia, inicio, fim,"
                             + " assunto, participantes) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            ps.setInt(1, salaId);
            ps.setInt(2, usuarioId);
            ps.setDate(3, java.sql.Date.valueOf(dia));
            ps.setTime(4, java.sql.Time.valueOf(inicio + ":00"));
            ps.setTime(5, java.sql.Time.valueOf(fim + ":00"));
            ps.setString(6, "Reuniao de teste");
            ps.setInt(7, 4);
            ps.executeUpdate();
        }
    }

    static int contarReservas() throws Exception {
        try (Connection c = Banco.conexao();
             PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM reservas");
             var rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
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
