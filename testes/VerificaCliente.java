package cliente;

import com.google.gson.JsonObject;

/**
 * Verifica o JSON que o cliente MONTA, sem abrir janela nenhuma.
 *
 * E a parte do cliente que mais merece teste: e exatamente esse JSON que o
 * servidor de outro grupo vai receber no teste de interoperabilidade. Se uma
 * chave obrigatoria faltar ou vier com o nome errado, a falha aparece aqui, e
 * nao na frente da turma.
 *
 * Fica no pacote cliente para enxergar os paineis, que sao de uso interno da
 * interface e nao precisam ser publicos.
 */
public class VerificaCliente {

    static int ok = 0, falhas = 0;
    static final String TOKEN = "c0fc3c713f09a43384ac08f7d91fca430dcbc6466fff9284ce4571bdc2c8f9f9";

    public static void main(String[] args) {
        requisicoesDeSala();
        recursos();
        horarios();
        requisicoesDeAdmin();

        System.out.println("\n===== cliente: " + ok + " passaram, " + falhas + " falharam =====");
        if (falhas > 0) System.exit(1);
    }

    // ------------------------------------------------------------------ salas

    static void requisicoesDeSala() {
        System.out.println("\n-- requisicoes de sala --");

        // Regra 2.1: 'op' e a primeira chave da mensagem.
        JsonObject semFiltro = PainelSalas.requisicaoDeListagem(TOKEN, "", "", "", "");
        confere("list_rooms comeca pela chave 'op' (regra 2.1)",
                semFiltro.keySet().iterator().next().equals("op"));
        confere("list_rooms sem filtro leva so op e token",
                semFiltro.keySet().equals(java.util.Set.of("op", "token")));

        // Filtro em branco nao vira chave: a ausencia e que diz "sem filtro".
        JsonObject comTrio = PainelSalas.requisicaoDeListagem(TOKEN, "2026-12-01", "14:00",
                "16:00", "");
        confere("o trio de horario viaja completo",
                "2026-12-01".equals(texto(comTrio, "date"))
                        && "14:00".equals(texto(comTrio, "start_time"))
                        && "16:00".equals(texto(comTrio, "end_time")));
        confere("min_capacity em branco nao vira chave", !comTrio.has("min_capacity"));

        JsonObject soCapacidade = PainelSalas.requisicaoDeListagem(TOKEN, "", "", "", "8");
        confere("min_capacity viaja sozinho, sem o trio",
                "8".equals(texto(soCapacidade, "min_capacity"))
                        && !soCapacidade.has("date"));

        // Espaco sobrando no campo nao deve virar valor com espaco.
        confere("o espaco digitado em volta do filtro e aparado",
                "2026-12-01".equals(texto(
                        PainelSalas.requisicaoDeListagem(TOKEN, "  2026-12-01  ", "14:00",
                                "16:00", ""), "date")));
        confere("campo so com espaco conta como em branco",
                !PainelSalas.requisicaoDeListagem(TOKEN, "   ", "", "", "").has("date"));

        // --- create_room ---
        JsonObject cadastro = PainelSalas.requisicaoDeCadastro(TOKEN, " Sala B12 ", " 12 ",
                " Bloco B ", "projetor, tv", "2M1, 3T1");
        confere("create_room leva op, token e os cinco campos da sala",
                cadastro.keySet().equals(java.util.Set.of(
                        "op", "token", "name", "capacity", "location", "resources", "slots")));
        confere("create_room apara os espacos dos campos",
                "Sala B12".equals(texto(cadastro, "name"))
                        && "12".equals(texto(cadastro, "capacity"))
                        && "Bloco B".equals(texto(cadastro, "location")));
        // A sala nasce "active" por decisao do servidor: mandar 'room_status'
        // aqui seria sugerir que o cliente escolhe, e ele nao escolhe.
        confere("create_room NAO manda room_status", !cadastro.has("room_status"));
        confere("resources vai como array, nao como texto",
                cadastro.get("resources").isJsonArray()
                        && cadastro.getAsJsonArray("resources").size() == 2);

        // --- update_room ---
        JsonObject alteracao = PainelSalas.requisicaoDeAlteracao(TOKEN, "7", "Sala B12", "16",
                "Bloco B", "projetor", "inactive", "");
        confere("update_room leva op, token, room_id e os seis campos da sala",
                alteracao.keySet().equals(java.util.Set.of(
                        "op", "token", "room_id", "name", "capacity", "location",
                        "resources", "room_status", "slots")));
        confere("update_room identifica a sala por room_id",
                "7".equals(texto(alteracao, "room_id")));
        confere("o estado escolhido no combo viaja como room_status",
                "inactive".equals(texto(alteracao, "room_status")));

        // Todos os valores sao strings, inclusive os numericos (regra 2.7).
        confere("capacity viaja como string, nao como numero (regra 2.7)",
                alteracao.get("capacity").getAsJsonPrimitive().isString());
        confere("room_id viaja como string (regra 2.7)",
                alteracao.get("room_id").getAsJsonPrimitive().isString());

    }

    static void horarios() {
        System.out.println("\n-- campo de horarios (grade da UTFPR) --");

        confere("um horario so",
                PainelSalas.slotsDeTexto("2M1").toString().equals("[\"2M1\"]"));
        confere("varios, com o espaco aparado",
                PainelSalas.slotsDeTexto(" 2M1 , 3T1 ,4N2").toString()
                        .equals("[\"2M1\",\"3T1\",\"4N2\"]"));
        // O codigo da grade e maiusculo; punir quem digita "2m1" seria so
        // transformar um deslize de teclado num 400 do servidor.
        confere("minuscula vira maiuscula",
                PainelSalas.slotsDeTexto("2m1, 3t1").toString()
                        .equals("[\"2M1\",\"3T1\"]"));
        confere("campo vazio vira lista vazia",
                PainelSalas.slotsDeTexto("").size() == 0);
        confere("virgula dobrada nao cria horario vazio",
                PainelSalas.slotsDeTexto("2M1,,3T1").size() == 2);

        // Regra 8.2 no create_room: obrigatorio.
        JsonObject semHorario = PainelSalas.requisicaoDeCadastro(TOKEN, "Sala X", "10",
                "Bloco A", "", "");
        confere("create_room sem horario manda slots vazio, e o servidor recusa",
                semHorario.getAsJsonArray("slots").size() == 0);

        // Regra 8.7 no update_room: vazio MANTEM, nao apaga.
        JsonObject semMudarHorario = PainelSalas.requisicaoDeAlteracao(TOKEN, "7", "", "", "",
                "", "", "");
        confere("update_room com horario em branco manda [] (= nao alterar)",
                semMudarHorario.has("slots")
                        && semMudarHorario.getAsJsonArray("slots").size() == 0);
        JsonObject comHorario = PainelSalas.requisicaoDeAlteracao(TOKEN, "7", "", "", "",
                "", "", "5N1, 5N2");
        confere("update_room com horarios manda a lista nova",
                comHorario.getAsJsonArray("slots").toString().equals("[\"5N1\",\"5N2\"]"));

        // A tabela e o formulario conversam pelo mesmo texto.
        JsonObject resposta = com("slots", PainelSalas.slotsDeTexto("2M1, 3T1"));
        confere("o array da resposta volta para '2M1, 3T1'",
                "2M1, 3T1".equals(PainelSalas.listaParaTexto(resposta, "slots")));
        confere("resposta sem 'slots' nao quebra a tabela",
                "".equals(PainelSalas.listaParaTexto(new JsonObject(), "slots")));
    }

    static void recursos() {
        System.out.println("\n-- campo de recursos --");

        confere("texto vazio vira array vazio, nao array com item vazio",
                PainelSalas.recursosDeTexto("").size() == 0);
        confere("texto so com espaco vira array vazio",
                PainelSalas.recursosDeTexto("   ").size() == 0);
        confere("um recurso so",
                PainelSalas.recursosDeTexto("projetor").toString().equals("[\"projetor\"]"));
        confere("dois recursos, com o espaco aparado",
                PainelSalas.recursosDeTexto(" projetor ,  tv ").toString()
                        .equals("[\"projetor\",\"tv\"]"));
        // "tv,,projetor" digitado as pressas nao deve criar um recurso sem nome.
        confere("virgula dobrada nao cria recurso de nome vazio",
                PainelSalas.recursosDeTexto("tv,,projetor").toString()
                        .equals("[\"tv\",\"projetor\"]"));
        confere("a ordem digitada e preservada",
                PainelSalas.recursosDeTexto("tv, projetor").toString()
                        .equals("[\"tv\",\"projetor\"]"));
        confere("virgula no fim nao cria item extra",
                PainelSalas.recursosDeTexto("tv,").size() == 1);

        // Ida e volta: o array da resposta volta ao formato do campo de texto.
        JsonObject resposta = com("resources", PainelSalas.recursosDeTexto("projetor, tv"));
        confere("o array da resposta volta para 'projetor, tv'",
                "projetor, tv".equals(PainelSalas.recursosParaTexto(resposta)));
        confere("resposta sem 'resources' nao quebra a tabela",
                "".equals(PainelSalas.recursosParaTexto(new JsonObject())));

        // O 'available' so vem com o trio de horario; sem ele, a tabela nao
        // pode insinuar que a sala esta indisponivel.
        confere("available ausente aparece como travessao",
                "—".equals(PainelSalas.disponibilidade(null)));
        confere("available \"true\" aparece como livre",
                "livre".equals(PainelSalas.disponibilidade("true")));
        // "Indisponivel" e nao "ocupada": o false do protocolo cobre sala
        // inactive e horario fora do funcionamento, e nao so reserva.
        confere("available \"false\" aparece como indisponivel",
                "indisponível".equals(PainelSalas.disponibilidade("false")));
        confere("a tabela nao chama de ocupada o que nao tem reserva",
                !"ocupada".equals(PainelSalas.disponibilidade("false")));
    }

    // ----------------------------------------------------------------- admin

    static void requisicoesDeAdmin() {
        System.out.println("\n-- requisicoes de administrador --");

        JsonObject alteracao = PainelUsuarios.requisicaoDeAlteracao(TOKEN, " maria ", " joana ",
                "senha123", "admin");
        confere("admin_update_user leva op, token, target_user e os tres campos",
                alteracao.keySet().equals(java.util.Set.of(
                        "op", "token", "target_user", "user", "password", "role")));
        // Regra 3.8: o token diz quem FAZ; 'target_user' diz sobre quem.
        confere("o alvo vai em target_user, nunca no token (regra 3.8)",
                "maria".equals(texto(alteracao, "target_user"))
                        && TOKEN.equals(texto(alteracao, "token")));
        confere("o novo nome vai em 'user', aparado",
                "joana".equals(texto(alteracao, "user")));

        // Regra 2.11: "" significa "nao alterar", e por isso as chaves vao
        // mesmo quando o campo ficou em branco.
        JsonObject soSenha = PainelUsuarios.requisicaoDeAlteracao(TOKEN, "maria", "", "nova1", "");
        confere("campo em branco viaja como \"\", e nao ausente (regra 2.11)",
                soSenha.has("user") && "".equals(texto(soSenha, "user"))
                        && soSenha.has("role") && "".equals(texto(soSenha, "role")));

        // Espaco em senha e caractere como qualquer outro: aparar mudaria em
        // silencio o que a pessoa digitou.
        confere("a senha NAO e aparada",
                " com espaco ".equals(texto(PainelUsuarios.requisicaoDeAlteracao(
                        TOKEN, "maria", "", " com espaco ", ""), "password")));

        confere("o combo \"(manter)\" se traduz em \"\" no role",
                "".equals(PainelUsuarios.perfilEscolhido("(manter)")));
        confere("o combo \"admin\" se traduz em \"admin\"",
                "admin".equals(PainelUsuarios.perfilEscolhido("admin")));
        confere("o combo \"user\" se traduz em \"user\"",
                "user".equals(PainelUsuarios.perfilEscolhido("user")));

        // O admin_delete_user nao pede senha: o dono da conta nem esta presente.
        JsonObject remocao = PainelUsuarios.requisicaoDeRemocao(TOKEN, " maria ");
        confere("admin_delete_user leva so op, token e target_user",
                remocao.keySet().equals(java.util.Set.of("op", "token", "target_user")));
        confere("admin_delete_user NAO pede senha", !remocao.has("password"));
        confere("admin_delete_user apara o alvo",
                "maria".equals(texto(remocao, "target_user")));
    }

    // Nao ha teste de ofereceAdmin() aqui porque ele e privado da ClienteGUI;
    // a decisao esta documentada no proprio metodo. Se um dia virar regra de
    // protocolo, vale extrair como os montadores acima.

    // ----------------------------------------------------------------- apoio

    static JsonObject com(String chave, com.google.gson.JsonElement valor) {
        JsonObject o = new JsonObject();
        o.add(chave, valor);
        return o;
    }

    static String texto(JsonObject o, String chave) {
        return o.has(chave) && !o.get(chave).isJsonNull() ? o.get(chave).getAsString() : null;
    }

    static void confere(String nome, boolean passou) {
        if (passou) { ok++; System.out.println("  OK    " + nome); }
        else { falhas++; System.out.println("  FALHA " + nome); }
    }
}
