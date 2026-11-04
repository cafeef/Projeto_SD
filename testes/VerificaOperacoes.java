import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

/**
 * Verifica as seis operacoes da EP-1 contra o servidor, cobrindo TODOS os
 * status previstos na aba "Msg Autenticacao e Usuario".
 *
 * Usa sockets crus, sem a classe Framing: e a mesma situacao do teste de
 * interoperabilidade de 08/10, em que o outro lado nao compartilha codigo nenhum
 * com o nosso.
 *
 * Uso: java VerificaOperacoes <porta>
 */
public class VerificaOperacoes {

    static int ok = 0, falhas = 0;
    static int porta;
    static final SecureRandom RND = new SecureRandom();
    static final String TOKEN_FALSO = "a".repeat(64);

    /** Vira false se alguma 'message' recebida tiver caractere fora do ASCII. */
    static boolean todasSemAcento = true;
    static String primeiraComAcento = null;

    public static void main(String[] args) throws Exception {
        porta = Integer.parseInt(args[0]);

        // A operacao "eco" nao pode existir no servidor de verdade.
        confere("dispatcher de producao nao conhece 'eco'",
                !servidor.ServidorConsole.criarDispatcher().conhece("eco"));
        confere("dispatcher de producao conhece as 6 operacoes da EP-1",
                servidor.ServidorConsole.criarDispatcher().conhece("register")
                        && servidor.ServidorConsole.criarDispatcher().conhece("login")
                        && servidor.ServidorConsole.criarDispatcher().conhece("logout")
                        && servidor.ServidorConsole.criarDispatcher().conhece("read_user")
                        && servidor.ServidorConsole.criarDispatcher().conhece("update_user")
                        && servidor.ServidorConsole.criarDispatcher().conhece("delete_user"));
        confere("dispatcher de producao conhece as 4 operacoes de admin da EP-2",
                servidor.ServidorConsole.criarDispatcher().conhece("admin_list_users")
                        && servidor.ServidorConsole.criarDispatcher().conhece("admin_read_user")
                        && servidor.ServidorConsole.criarDispatcher().conhece("admin_update_user")
                        && servidor.ServidorConsole.criarDispatcher().conhece("admin_delete_user"));

        registrar();
        login();
        logout();
        readUser();
        updateUser();
        deleteUser();
        operacoesAdmin();

        // Regra 2.8: 'message' viaja em portugues SEM acento e sem emoji. Se
        // alguem "corrigir" os textos para portugues acentuado, a resposta deixa
        // de bater com a dos outros grupos no teste de interoperabilidade -- e
        // esta verificacao quebra antes que isso aconteca na avaliacao.
        confere("todas as mensagens do protocolo vieram sem acento (regra 2.8)"
                + (todasSemAcento ? "" : " -- achei: " + primeiraComAcento), todasSemAcento);

        System.out.println("\n===== operacoes: " + ok + " passaram, " + falhas + " falharam =====");
        if (falhas > 0) System.exit(1);
    }

    // ------------------------------------------------------------------ register

    static void registrar() throws Exception {
        System.out.println("\n-- register --");
        try (Conn c = new Conn()) {
            String u = nome(), e = u + "@email.com";

            checa("201 cadastro realizado", c.envia(req("register", "email", e, "user", u,
                    "password", "senha123")), "register_response", "201",
                    "Usuario cadastrado com sucesso");

            checa("409 usuario ja cadastrado", c.envia(req("register", "email", nome() + "@email.com",
                    "user", u, "password", "senha123")), "register_response", "409",
                    "Usuario ou email ja cadastrado");

            checa("409 email ja cadastrado", c.envia(req("register", "email", e,
                    "user", nome(), "password", "senha123")), "register_response", "409",
                    "Usuario ou email ja cadastrado");

            // A regra de 'user' combinada com a turma: so letras, sem numero.
            checa("400 user com numero", c.envia(req("register", "email", nome() + "@email.com",
                    "user", "joao123", "password", "senha123")), "register_response", "400",
                    "Dados de cadastro em formato invalido");

            checa("400 user com maiuscula", c.envia(req("register", "email", nome() + "@email.com",
                    "user", "Joao", "password", "senha123")), "register_response", "400",
                    "Dados de cadastro em formato invalido");

            checa("400 email com maiuscula", c.envia(req("register", "email", "JOAO@email.com",
                    "user", nome(), "password", "senha123")), "register_response", "400",
                    "Dados de cadastro em formato invalido");

            checa("400 password com espaco", c.envia(req("register", "email", nome() + "@email.com",
                    "user", nome(), "password", "senha 123")), "register_response", "400",
                    "Dados de cadastro em formato invalido");

            checa("400 campo obrigatorio ausente",
                    c.envia("{\"op\":\"register\",\"user\":\"" + nome() + "\"}"),
                    "register_response", "400", "Dados de cadastro em formato invalido");

            checa("400 campo null (regra 2.10)",
                    c.envia("{\"op\":\"register\",\"email\":null,\"user\":\"" + nome()
                            + "\",\"password\":\"senha123\"}"),
                    "register_response", "400", "Dados de cadastro em formato invalido");

            checa("400 campo numerico, nao string (regra 2.3)",
                    c.envia("{\"op\":\"register\",\"email\":\"" + nome()
                            + "@email.com\",\"user\":\"" + nome() + "\",\"password\":12345}"),
                    "register_response", "400", "Dados de cadastro em formato invalido");

            JsonObject r = json(c.envia(req("register", "email", nome() + "@email.com",
                    "user", nome(), "password", "senha123")));
            confere("register nao devolve token (nao cria sessao)", !r.has("token"));
        }
    }

    // --------------------------------------------------------------------- login

    static void login() throws Exception {
        System.out.println("\n-- login --");
        try (Conn c = new Conn()) {
            String u = nome(), e = u + "@email.com";
            c.envia(req("register", "email", e, "user", u, "password", "senha123"));

            checa("400 email em formato invalido", c.envia(req("login", "email", "naoehemail",
                    "password", "senha123")), "login_response", "400",
                    "Email ou senha em formato invalido");

            checa("400 password ausente", c.envia("{\"op\":\"login\",\"email\":\"" + e + "\"}"),
                    "login_response", "400", "Email ou senha em formato invalido");

            String senhaErrada = c.envia(req("login", "email", e, "password", "errada"));
            checa("401 senha incorreta", senhaErrada, "login_response", "401",
                    "Email ou senha incorretos");

            String emailInexistente = c.envia(req("login", "email", nome() + "@email.com",
                    "password", "senha123"));
            checa("401 email inexistente", emailInexistente, "login_response", "401",
                    "Email ou senha incorretos");

            confere("401 de senha errada e de email inexistente sao IGUAIS (nao revela cadastros)",
                    json(senhaErrada).get("message").getAsString()
                            .equals(json(emailInexistente).get("message").getAsString()));

            JsonObject r = json(c.envia(req("login", "email", e, "password", "senha123")));
            confere("200 login realizado", "200".equals(campo(r, "status")));
            confere("200 devolve token no formato da regra 3.1",
                    campo(r, "token") != null && campo(r, "token").matches("^[a-f0-9]{64}$"));
            confere("200 devolve role", "user".equals(campo(r, "role")));
            confere("200 nao devolve password", !r.has("password"));

            checa("409 sessao ja ativa (regra 3.5)", c.envia(req("login", "email", e,
                    "password", "senha123")), "login_response", "409",
                    "Usuario ja possui sessao ativa");

            // O 409 nao pode vazar existencia: senha errada continua 401.
            checa("401 tem prioridade sobre 409 quando a senha esta errada",
                    c.envia(req("login", "email", e, "password", "errada")),
                    "login_response", "401", "Email ou senha incorretos");
        }
    }

    // -------------------------------------------------------------------- logout

    static void logout() throws Exception {
        System.out.println("\n-- logout --");
        try (Conn c = new Conn()) {
            String u = nome(), e = u + "@email.com";
            c.envia(req("register", "email", e, "user", u, "password", "senha123"));
            String token = campo(json(c.envia(req("login", "email", e, "password", "senha123"))),
                    "token");

            checa("400 token em formato invalido",
                    c.envia(req("logout", "token", "abc")), "logout_response", "400",
                    "Token em formato invalido");

            checa("401 token inexistente",
                    c.envia(req("logout", "token", TOKEN_FALSO)), "logout_response", "401",
                    "Token invalido ou expirado");

            checa("200 logout realizado", c.envia(req("logout", "token", token)),
                    "logout_response", "200", "Logout realizado com sucesso");

            checa("401 no segundo logout com o mesmo token (regra 3.4)",
                    c.envia(req("logout", "token", token)), "logout_response", "401",
                    "Token invalido ou expirado");

            checa("token invalidado nao serve mais para outras operacoes",
                    c.envia(req("read_user", "token", token)), "read_user_response", "401",
                    "Token invalido ou expirado");

            checa("depois do logout da para logar de novo (sem 409)",
                    c.envia(req("login", "email", e, "password", "senha123")),
                    "login_response", "200", "Login realizado com sucesso");
        }
    }

    // ----------------------------------------------------------------- read_user

    static void readUser() throws Exception {
        System.out.println("\n-- read_user --");
        try (Conn c = new Conn()) {
            String u = nome(), e = u + "@email.com";
            c.envia(req("register", "email", e, "user", u, "password", "senha123"));
            String token = campo(json(c.envia(req("login", "email", e, "password", "senha123"))),
                    "token");

            checa("400 token em formato invalido", c.envia(req("read_user", "token", "xyz")),
                    "read_user_response", "400", "Token em formato invalido");

            checa("401 token inexistente", c.envia(req("read_user", "token", TOKEN_FALSO)),
                    "read_user_response", "401", "Token invalido ou expirado");

            JsonObject r = json(c.envia(req("read_user", "token", token)));
            confere("200 consulta realizada", "200".equals(campo(r, "status")));
            confere("200 devolve o proprio user", u.equals(campo(r, "user")));
            confere("200 devolve o email", e.equals(campo(r, "email")));
            confere("200 devolve role user (regra 3.6)", "user".equals(campo(r, "role")));
            confere("200 devolve created_at no formato do protocolo",
                    campo(r, "created_at") != null && campo(r, "created_at").matches(
                            "^[0-9]{4}-[0-9]{2}-[0-9]{2} ([01][0-9]|2[0-3]):[0-5][0-9]:[0-5][0-9]$"));
            confere("200 NUNCA devolve a senha", !r.has("password"));

            c.envia(req("logout", "token", token));
        }
    }

    // --------------------------------------------------------------- update_user

    static void updateUser() throws Exception {
        System.out.println("\n-- update_user --");
        try (Conn c = new Conn()) {
            String u = nome(), e = u + "@email.com";
            c.envia(req("register", "email", e, "user", u, "password", "senha123"));
            String token = campo(json(c.envia(req("login", "email", e, "password", "senha123"))),
                    "token");

            checa("400 token em formato invalido", c.envia(req("update_user", "token", "zzz")),
                    "update_user_response", "400", "Dados em formato invalido");

            checa("400 se a chave 'email' vier (RNF 5.c: email e imutavel)",
                    c.envia(req("update_user", "token", token, "email", "novo@email.com")),
                    "update_user_response", "400", "Dados em formato invalido");

            checa("400 user com numero", c.envia(req("update_user", "token", token,
                    "user", "joao123")), "update_user_response", "400", "Dados em formato invalido");

            checa("400 campo null (regra 2.10)",
                    c.envia("{\"op\":\"update_user\",\"token\":\"" + token + "\",\"user\":null}"),
                    "update_user_response", "400", "Dados em formato invalido");

            checa("400 user e password vazios ao mesmo tempo (regra 2.13)",
                    c.envia(req("update_user", "token", token, "user", "", "password", "")),
                    "update_user_response", "400", "Nenhum dado para atualizar");

            checa("400 sem nenhum campo de alteracao (nada mudaria)",
                    c.envia(req("update_user", "token", token)),
                    "update_user_response", "400", "Nenhum dado para atualizar");

            // O "" continua querendo dizer "nao alterar" (regra 2.11): junto de um
            // campo que muda de verdade, a requisicao passa e o vazio e ignorado.
            checa("200 user vazio e ignorado quando a senha vem junto (regra 2.11)",
                    c.envia(req("update_user", "token", token, "user", "",
                            "password", "senha123")),
                    "update_user_response", "200", "Dados atualizados com sucesso");

            JsonObject apos = json(c.envia(req("read_user", "token", token)));
            confere("campo vazio realmente nao alterou o user", u.equals(campo(apos, "user")));

            String novo = nome();
            checa("200 troca de user", c.envia(req("update_user", "token", token, "user", novo)),
                    "update_user_response", "200", "Dados atualizados com sucesso");
            confere("o token continua valido depois de trocar o user",
                    novo.equals(campo(json(c.envia(req("read_user", "token", token))), "user")));

            String outro = nome();
            c.envia(req("register", "email", outro + "@email.com", "user", outro,
                    "password", "senha123"));
            checa("409 user ja em uso", c.envia(req("update_user", "token", token, "user", outro)),
                    "update_user_response", "409", "Usuario ja esta em uso");

            checa("200 troca de senha", c.envia(req("update_user", "token", token,
                    "password", "novasenha1")), "update_user_response", "200",
                    "Dados atualizados com sucesso");
            c.envia(req("logout", "token", token));
            checa("a senha nova passa a valer no login",
                    c.envia(req("login", "email", e, "password", "novasenha1")),
                    "login_response", "200", "Login realizado com sucesso");
            checa("a senha antiga deixa de valer",
                    c.envia(req("login", "email", e, "password", "senha123")),
                    "login_response", "401", "Email ou senha incorretos");
        }
    }

    // --------------------------------------------------------------- delete_user

    static void deleteUser() throws Exception {
        System.out.println("\n-- delete_user --");
        try (Conn c = new Conn()) {
            String u = nome(), e = u + "@email.com";
            c.envia(req("register", "email", e, "user", u, "password", "senha123"));
            String token = campo(json(c.envia(req("login", "email", e, "password", "senha123"))),
                    "token");

            checa("400 password ausente", c.envia(req("delete_user", "token", token)),
                    "delete_user_response", "400", "Senha em formato invalido");

            checa("400 password com espaco", c.envia(req("delete_user", "token", token,
                    "password", "senha 123")), "delete_user_response", "400",
                    "Senha em formato invalido");

            checa("403 senha incorreta (regra 3.9)", c.envia(req("delete_user", "token", token,
                    "password", "outrasenha")), "delete_user_response", "403",
                    "Senha incorreta");

            // E o motivo de ser 403 e nao 401: a sessao nao pode cair por uma senha
            // errada, senao o cliente se desloga e o login seguinte bate em 409.
            checa("a sessao sobrevive a senha errada",
                    c.envia(req("read_user", "token", token)), "read_user_response", "200",
                    "Consulta realizada com sucesso");

            checa("200 usuario removido", c.envia(req("delete_user", "token", token,
                    "password", "senha123")), "delete_user_response", "200",
                    "Usuario removido com sucesso");

            checa("o token cai junto com o cadastro",
                    c.envia(req("read_user", "token", token)), "read_user_response", "401",
                    "Token invalido ou expirado");

            checa("o cadastro removido nao loga mais",
                    c.envia(req("login", "email", e, "password", "senha123")),
                    "login_response", "401", "Email ou senha incorretos");

            // Regra do ultimo administrador, com o admin semeado no boot.
            String tokenAdmin = campo(json(c.envia(req("login", "email", "admin@admin.com",
                    "password", "admin"))), "token");
            if (tokenAdmin == null) {
                confere("login do administrador semeado", false);
            } else {
                checa("403 nao remove o ultimo administrador",
                        c.envia(req("delete_user", "token", tokenAdmin, "password", "admin")),
                        "delete_user_response", "403",
                        "Nao e possivel remover o ultimo administrador");
                c.envia(req("logout", "token", tokenAdmin));
            }
        }
    }

    /**
     * As quatro operacoes admin_* e a whitelist de tokens (regra 3.14).
     *
     * O fio condutor e sempre o mesmo: quem decide se a requisicao tem poder de
     * admin e a whitelist, nunca o cliente e nunca o role gravado no banco. Por
     * isso os testes de promocao e rebaixamento usam o token que o alvo JA tem
     * na mao, sem login novo.
     */
    static void operacoesAdmin() throws Exception {
        System.out.println("\n-- operacoes de administrador --");
        try (Conn adm = new Conn(); Conn c = new Conn()) {
            String tokenAdmin = campo(json(adm.envia(req("login",
                    "email", "admin@admin.com", "password", "admin"))), "token");
            if (tokenAdmin == null) {
                confere("login do administrador inicial", false);
                return;
            }

            // Um usuario comum, para servir de alvo.
            String u = nome(), e = u + "@email.com";
            c.envia(req("register", "email", e, "user", u, "password", "senha123"));
            String tokenComum = campo(json(c.envia(req("login", "email", e,
                    "password", "senha123"))), "token");

            // --- quem pode entrar ---
            checa("400 token em formato invalido",
                    adm.envia(req("admin_list_users", "token", "xyz")),
                    "admin_list_users_response", "400", "Token em formato invalido");

            checa("401 token bem formado que nao existe",
                    adm.envia(req("admin_list_users", "token", TOKEN_FALSO)),
                    "admin_list_users_response", "401", "Token invalido ou expirado");

            checa("403 usuario comum nao lista usuarios",
                    c.envia(req("admin_list_users", "token", tokenComum)),
                    "admin_list_users_response", "403", "Permissao insuficiente");

            // Regra 3.14: o perfil nunca vem do cliente. Mandar "role":"admin"
            // na requisicao nao da poder nenhum.
            checa("403 mesmo mandando role=admin na requisicao (regra 3.14)",
                    c.envia(req("admin_list_users", "token", tokenComum, "role", "admin")),
                    "admin_list_users_response", "403", "Permissao insuficiente");

            // --- admin_list_users ---
            JsonObject lista = json(adm.envia(req("admin_list_users", "token", tokenAdmin)));
            checa("200 admin lista os usuarios",
                    lista.toString(), "admin_list_users_response", "200",
                    "Consulta realizada com sucesso");
            confere("count e string e bate com o tamanho de users (regra 2.7)",
                    campo(lista, "count") != null && lista.has("users")
                            && campo(lista, "count").equals(
                                    String.valueOf(lista.getAsJsonArray("users").size())));
            confere("o alvo aparece na lista",
                    lista.toString().contains("\"" + u + "\""));
            confere("a lista nao devolve senha nem hash",
                    !lista.toString().contains("senha123")
                            && !lista.toString().contains("senha_hash"));

            // --- admin_read_user ---
            checa("400 target_user ausente",
                    adm.envia(req("admin_read_user", "token", tokenAdmin)),
                    "admin_read_user_response", "400", "Dados em formato invalido");

            checa("404 usuario inexistente",
                    adm.envia(req("admin_read_user", "token", tokenAdmin,
                            "target_user", "naoexistemesmo")),
                    "admin_read_user_response", "404", "Usuario nao encontrado");

            JsonObject lido = json(adm.envia(req("admin_read_user", "token", tokenAdmin,
                    "target_user", u)));
            checa("200 admin le o cadastro de outro", lido.toString(),
                    "admin_read_user_response", "200", "Consulta realizada com sucesso");
            confere("a leitura traz user, email, role e created_at",
                    u.equals(campo(lido, "user")) && e.equals(campo(lido, "email"))
                            && "user".equals(campo(lido, "role"))
                            && campo(lido, "created_at") != null);

            // --- admin_update_user ---
            checa("400 nenhum dado para atualizar (regra 2.13)",
                    adm.envia(req("admin_update_user", "token", tokenAdmin, "target_user", u,
                            "user", "", "password", "", "role", "")),
                    "admin_update_user_response", "400", "Nenhum dado para atualizar");

            checa("400 a chave email e proibida (RNF 5.c)",
                    adm.envia(req("admin_update_user", "token", tokenAdmin, "target_user", u,
                            "user", "", "password", "", "role", "", "email", "x@y.com")),
                    "admin_update_user_response", "400", "Dados em formato invalido");

            checa("404 alterar usuario inexistente",
                    adm.envia(req("admin_update_user", "token", tokenAdmin,
                            "target_user", "naoexistemesmo",
                            "user", "", "password", "novasenha1", "role", "")),
                    "admin_update_user_response", "404", "Usuario nao encontrado");

            checa("409 novo user ja em uso",
                    adm.envia(req("admin_update_user", "token", tokenAdmin, "target_user", u,
                            "user", "admin", "password", "", "role", "")),
                    "admin_update_user_response", "409", "Usuario ja esta em uso");

            // --- promocao vale na hora, sem login novo (regra 3.14) ---
            checa("200 promove o usuario a admin",
                    adm.envia(req("admin_update_user", "token", tokenAdmin, "target_user", u,
                            "user", "", "password", "", "role", "admin")),
                    "admin_update_user_response", "200", "Dados atualizados com sucesso");

            checa("o token que o alvo ja tinha ganha poder na hora (regra 3.14)",
                    c.envia(req("admin_list_users", "token", tokenComum)),
                    "admin_list_users_response", "200", "Consulta realizada com sucesso");

            checa("200 rebaixa o usuario de volta",
                    adm.envia(req("admin_update_user", "token", tokenAdmin, "target_user", u,
                            "user", "", "password", "", "role", "user")),
                    "admin_update_user_response", "200", "Dados atualizados com sucesso");

            checa("e perde o poder na hora, com o mesmo token (regra 3.14)",
                    c.envia(req("admin_list_users", "token", tokenComum)),
                    "admin_list_users_response", "403", "Permissao insuficiente");

            // --- o ultimo administrador nao cai ---
            checa("403 nao rebaixa o ultimo administrador",
                    adm.envia(req("admin_update_user", "token", tokenAdmin,
                            "target_user", "admin",
                            "user", "", "password", "", "role", "user")),
                    "admin_update_user_response", "403",
                    "Nao e possivel remover o ultimo administrador");

            checa("403 nao remove o ultimo administrador",
                    adm.envia(req("admin_delete_user", "token", tokenAdmin,
                            "target_user", "admin")),
                    "admin_delete_user_response", "403",
                    "Nao e possivel remover o ultimo administrador");

            // --- admin_delete_user ---
            checa("404 remover usuario inexistente",
                    adm.envia(req("admin_delete_user", "token", tokenAdmin,
                            "target_user", "naoexistemesmo")),
                    "admin_delete_user_response", "404", "Usuario nao encontrado");

            checa("200 admin remove outro usuario",
                    adm.envia(req("admin_delete_user", "token", tokenAdmin, "target_user", u)),
                    "admin_delete_user_response", "200", "Usuario removido com sucesso");

            checa("a sessao do removido cai junto",
                    c.envia(req("read_user", "token", tokenComum)),
                    "read_user_response", "401", "Token invalido ou expirado");

            // Regra 3.13: user e email ficam livres para um cadastro novo.
            checa("201 o user e o email removidos ficam livres (regra 3.13)",
                    c.envia(req("register", "email", e, "user", u, "password", "senha123")),
                    "register_response", "201", "Usuario cadastrado com sucesso");

            // --- o logout tira o poder do token ---
            adm.envia(req("logout", "token", tokenAdmin));
            checa("depois do logout o token do admin nao vale mais",
                    adm.envia(req("admin_list_users", "token", tokenAdmin)),
                    "admin_list_users_response", "401", "Token invalido ou expirado");
        }
    }

    // ------------------------------------------------------------------ apoio

    /** Conexao crua com o servidor, uma mensagem por linha terminada em LF. */
    static class Conn implements AutoCloseable {
        private final Socket s;
        private final OutputStream out;
        private final InputStream in;

        Conn() throws IOException {
            s = new Socket("127.0.0.1", porta);
            s.setSoTimeout(5000);
            out = s.getOutputStream();
            in = s.getInputStream();
        }

        String envia(String json) throws IOException {
            out.write((json + "\n").getBytes(StandardCharsets.UTF_8));
            out.flush();
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            int b;
            while ((b = in.read()) != -1 && b != '\n') buf.write(b);
            return new String(buf.toByteArray(), StandardCharsets.UTF_8);
        }

        @Override
        public void close() throws IOException {
            s.close();
        }
    }

    /** Monta uma requisicao com 'op' primeiro (regra 2.1) e pares chave/valor. */
    static String req(String op, String... pares) {
        StringBuilder sb = new StringBuilder("{\"op\":\"").append(op).append('"');
        for (int i = 0; i < pares.length; i += 2) {
            sb.append(",\"").append(pares[i]).append("\":\"").append(pares[i + 1]).append('"');
        }
        return sb.append('}').toString();
    }

    /** Nome de usuario aleatorio: so letras minusculas, como manda a regex. */
    static String nome() {
        StringBuilder sb = new StringBuilder("t");
        for (int i = 0; i < 9; i++) sb.append((char) ('a' + RND.nextInt(26)));
        return sb.toString();
    }

    static JsonObject json(String s) {
        return JsonParser.parseString(s).getAsJsonObject();
    }

    static String campo(JsonObject o, String chave) {
        return o.has(chave) && !o.get(chave).isJsonNull() ? o.get(chave).getAsString() : null;
    }

    /** Confere op, status e message -- os tres campos que a regra 2.6 obriga. */
    static void checa(String nome, String resposta, String op, String status, String message) {
        try {
            JsonObject r = json(resposta);
            registrarAcento(campo(r, "message"));
            boolean passou = op.equals(campo(r, "op"))
                    && status.equals(campo(r, "status"))
                    && message.equals(campo(r, "message"));
            if (passou) { ok++; System.out.println("  OK    " + nome); }
            else {
                falhas++;
                System.out.println("  FALHA " + nome + "\n         esperado: " + op + " / "
                        + status + " / " + message + "\n         recebido: " + resposta);
            }
        } catch (RuntimeException e) {
            falhas++;
            System.out.println("  FALHA " + nome + " (resposta ilegivel): " + resposta);
        }
    }

    /** Anota se a mensagem trouxe algum caractere fora do ASCII imprimivel. */
    static void registrarAcento(String message) {
        if (message == null) {
            return;
        }
        for (char c : message.toCharArray()) {
            if (c < 0x20 || c > 0x7E) {
                todasSemAcento = false;
                if (primeiraComAcento == null) {
                    primeiraComAcento = message;
                }
                return;
            }
        }
    }

    static void confere(String nome, boolean passou) {
        if (passou) { ok++; System.out.println("  OK    " + nome); }
        else { falhas++; System.out.println("  FALHA " + nome); }
    }
}
