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
        reservas();
        seletores();
        horarioQuePassou();
        diaInteiro();

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

        // Regra 9.7: no update_room, [] passou a significar "nao alterar".
        // Quem quer apagar os recursos manda ["0"], e e isso que o campo em
        // branco vira -- o formulario chega preenchido com os recursos atuais,
        // entao apaga-lo e um pedido, nao um descuido.
        confere("update_room com recursos em branco manda [\"0\"] (apagar)",
                PainelSalas.requisicaoDeAlteracao(TOKEN, "7", "", "", "", "", "", "")
                        .getAsJsonArray("resources").toString().equals("[\"0\"]"));
        confere("update_room com recursos preenchidos manda a lista",
                PainelSalas.requisicaoDeAlteracao(TOKEN, "7", "", "", "", "tv, projetor", "", "")
                        .getAsJsonArray("resources").toString()
                        .equals("[\"tv\",\"projetor\"]"));
        // No create_room o vazio continua sendo "sala sem recurso nenhum":
        // nao ha recurso anterior para manter nem para apagar.
        confere("create_room com recursos em branco manda [] (sala sem recursos)",
                PainelSalas.requisicaoDeCadastro(TOKEN, "Sala X", "4", "Bloco A", "", "2M1")
                        .getAsJsonArray("resources").size() == 0);

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

    // -------------------------------------------------------------- reservas

    static void reservas() {
        System.out.println("\n-- reservas (EP-3) --");

        // Item b: check_availability leva sala, dia e o recorte de horario.
        JsonObject disp = PainelReservas.requisicaoDeDisponibilidade(TOKEN, " 7 ",
                "2026-12-01", "13:00", "14:40");
        confere("check_availability leva op, token, sala, data e horario",
                disp.keySet().equals(java.util.Set.of(
                        "op", "token", "room_id", "date", "start_time", "end_time")));
        confere("os espacos digitados sao aparados",
                "7".equals(texto(disp, "room_id")));

        // Item d: create_reservation leva tambem assunto e participantes.
        JsonObject nova = PainelReservas.requisicaoDeReserva(TOKEN, "7", "2026-12-01",
                "13:00", "14:40", "Reuniao de projeto", "6");
        confere("create_reservation leva as sete chaves do protocolo",
                nova.keySet().equals(java.util.Set.of(
                        "op", "token", "room_id", "date", "start_time", "end_time",
                        "topic", "participants")));
        confere("o assunto e o numero de participantes viajam como topic e participants",
                "Reuniao de projeto".equals(texto(nova, "topic"))
                        && "6".equals(texto(nova, "participants")));

        // Item c: sem scope vale "mine", entao o campo so viaja quando e "all".
        JsonObject minhas = PainelReservas.requisicaoDeListagem(TOKEN, false, "", "");
        confere("list_reservations sem filtro leva so op e token",
                minhas.keySet().equals(java.util.Set.of("op", "token")));
        confere("sem a caixa marcada, o scope nao viaja", !minhas.has("scope"));

        JsonObject todas = PainelReservas.requisicaoDeListagem(TOKEN, true, "", "");
        confere("com a caixa marcada, o scope vai como \"all\"",
                "all".equals(texto(todas, "scope")));

        // Regra 2.14: filtro nao preenchido e OMITIDO, e nao mandado vazio --
        // "" responderia 400, e a tela ficaria sem listagem nenhuma.
        JsonObject filtrada = PainelReservas.requisicaoDeListagem(TOKEN, false,
                "2026-12-01", " 7 ");
        confere("os filtros preenchidos viajam, ja aparados",
                "2026-12-01".equals(texto(filtrada, "date"))
                        && "7".equals(texto(filtrada, "room_id")));
        confere("filtro em branco nao vira chave vazia (regra 2.14)",
                !PainelReservas.requisicaoDeListagem(TOKEN, false, "", "9").has("date"));

        // Item e: a alteracao leva as seis chaves alteraveis, inclusive as
        // duas que so existem desde a v2.10 (regra 9.10).
        JsonObject alteracao = PainelReservas.requisicaoDeAlteracao(TOKEN, " 27 ", "9",
                "2026-12-08", "", "13:50", "", "4");
        confere("update_reservation leva as oito chaves do protocolo",
                alteracao.keySet().equals(java.util.Set.of(
                        "op", "token", "reservation_id", "room_id", "date",
                        "start_time", "end_time", "topic", "participants")));
        confere("a troca de sala e de dia viaja (regra 9.10, v2.10)",
                "9".equals(texto(alteracao, "room_id"))
                        && "2026-12-08".equals(texto(alteracao, "date")));
        // Campo apagado vira "", que o protocolo le como "nao alterar" -- e
        // nao some da requisicao, para o formato ser sempre o mesmo.
        confere("campo apagado viaja como \"\" (nao alterar)",
                "".equals(texto(alteracao, "start_time"))
                        && "".equals(texto(alteracao, "topic")));
        confere("o reservation_id e aparado",
                "27".equals(texto(alteracao, "reservation_id")));

        confere("read_reservation leva o reservation_id",
                "27".equals(texto(PainelReservas.requisicaoDeLeitura(TOKEN, " 27 "),
                        "reservation_id")));
        confere("delete_reservation leva o reservation_id",
                "delete_reservation".equals(texto(
                        PainelReservas.requisicaoDeCancelamento(TOKEN, "27"), "op")));

        // A frase do check_availability precisa dizer que a consulta NAO
        // reserva: sem isso o usuario leria "livre" como garantia.
        JsonObject respostaLivre = new JsonObject();
        respostaLivre.addProperty("available", "true");
        respostaLivre.addProperty("room_id", "7");
        respostaLivre.addProperty("date", "2026-12-01");
        respostaLivre.addProperty("start_time", "13:00");
        respostaLivre.addProperty("end_time", "14:40");
        String frase = PainelReservas.textoDaDisponibilidade(respostaLivre);
        confere("a sala livre e anunciada como LIVRE", frase.contains("LIVRE"));
        confere("a tela avisa que a consulta nao segura a sala",
                frase.contains("não reserva"));

        respostaLivre.addProperty("available", "false");
        confere("a sala ocupada nao e anunciada como livre",
                PainelReservas.textoDaDisponibilidade(respostaLivre).contains("NÃO está"));

        // Consultar o passado e valido (regra 9.4) e o servidor responde 200
        // com "available": "true" -- mas aquilo nao da para reservar (regra
        // 9.3). Sem o aviso, o "LIVRE" convida a tentar e levar um 400.
        JsonObject ontem = new JsonObject();
        ontem.addProperty("available", "true");
        ontem.addProperty("room_id", "2");
        ontem.addProperty("date", java.time.LocalDate.now().minusDays(1).toString());
        ontem.addProperty("start_time", "13:00");
        ontem.addProperty("end_time", "13:50");
        String textoDeOntem = PainelReservas.textoDaDisponibilidade(ontem);
        confere("consulta sobre ontem avisa que nao da para reservar",
                textoDeOntem.contains("não é possível reservar"));
        confere("e fala no passado: \"estava livre\", nao \"esta livre\"",
                textoDeOntem.contains("estava LIVRE") && !textoDeOntem.contains("está LIVRE"));
        confere("sobre ontem nao vem a dica de que outra pessoa pode reservar antes",
                !textoDeOntem.contains("antes de você"));

        ontem.addProperty("available", "false");
        confere("ocupada no passado tambem fala no passado",
                PainelReservas.textoDaDisponibilidade(ontem).contains("NÃO estava"));
    }

    // ------------------------------------------- listas de escolha de horário

    /**
     * As listas que substituiram os campos de texto de horario.
     *
     * Sao componentes Swing, mas nenhum deles abre janela: dao para construir
     * e interrogar sem tela, como qualquer objeto.
     */
    static void seletores() {
        System.out.println("\n-- listas de escolha de horario --");

        ComboDeHorario inicio = ComboDeHorario.inicio(ComboDeHorario.NAO_INFORMADO);
        confere("a lista de inicio tem a opcao vazia mais as 17 aulas",
                inicio.getItemCount() == 18);
        confere("a opcao vazia vem primeiro e vale \"\"",
                ComboDeHorario.NAO_INFORMADO.equals(inicio.getItemAt(0))
                        && "".equals(inicio.hora()));
        confere("o rotulo mostra a hora e o codigo da aula",
                "13:00 (T1)".equals(inicio.getItemAt(7)));

        inicio.selecionarHora("13:00");
        confere("escolher por hora devolve a hora do protocolo",
                "13:00".equals(inicio.hora()));
        inicio.selecionarHora("");
        confere("limpar volta para a opcao vazia", "".equals(inicio.hora()));
        inicio.selecionarHora("14:15");
        confere("hora fora da grade nao seleciona nada", "".equals(inicio.hora()));

        // A lista de fim acompanha o inicio: so o que vem DEPOIS.
        ComboDeHorario fim = ComboDeHorario.fim(ComboDeHorario.NAO_INFORMADO);
        fim.seguir(inicio);
        inicio.selecionarHora("22:10");
        confere("com inicio 22:10 so sobra o fim 23:00",
                fim.getItemCount() == 2 && "23:00 (N5)".equals(fim.getItemAt(1)));
        // Nao escolhe sozinho: numa ALTERACAO, o vazio quer dizer "mantem o
        // fim que ja esta la", e preencher por conta propria transformaria um
        // "nao mexa nisso" num horario novo sem a pessoa pedir.
        confere("a lista nao escolhe o fim por conta propria",
                "".equals(fim.hora()));

        inicio.selecionarHora("13:00");
        confere("voltando o inicio, a lista de fim cresce de novo",
                fim.getItemCount() > 2);

        // Mas um fim JA ESCOLHIDO que deixa de ser possivel precisa sair: ele
        // nao esta mais na lista, e deixa-lo mandaria um horario invertido.
        fim.selecionarHora("13:50");
        confere("o fim escolhido vale", "13:50".equals(fim.hora()));
        inicio.selecionarHora("22:10");
        confere("fim que ficou impossivel cai para o primeiro valido",
                "23:00".equals(fim.hora()));
        inicio.selecionarHora("13:00");
        fim.selecionarHora("14:40");
        inicio.selecionarHora("13:50");
        confere("fim que continua valido nao e trocado",
                "14:40".equals(fim.hora()));
        inicio.selecionarHora("13:00");
        // O 400 mais facil de cometer deixou de ser possivel pela tela.
        confere("nenhum fim oferecido e anterior ao inicio escolhido",
                todosOsFinsDepoisDe(fim, "13:00"));

        ComboDeHorario filtro = ComboDeHorario.inicio(ComboDeHorario.QUALQUER);
        confere("o filtro de sala usa \"(qualquer)\" como opcao vazia",
                ComboDeHorario.QUALQUER.equals(filtro.getItemAt(0))
                        && "".equals(filtro.hora()));

        // --- os horários de funcionamento da sala ---
        SeletorDeSlots slots = new SeletorDeSlots();
        confere("a lista traz os 102 horarios da grade",
                slots.getModel().getSize() == 102);
        confere("nada marcado devolve texto vazio", "".equals(slots.texto()));

        slots.selecionar("3T1, 3T2");
        confere("marcar por codigo devolve os mesmos codigos",
                "3T1, 3T2".equals(slots.texto()));
        slots.selecionar("3t1");
        confere("codigo minusculo tambem marca", "3T1".equals(slots.texto()));
        slots.selecionar("2M1, NAOEXISTE, 3T1");
        confere("codigo de outra grade e ignorado sem quebrar a tela",
                "2M1, 3T1".equals(slots.texto()));
        slots.limpar();
        confere("limpar desmarca tudo", "".equals(slots.texto()));

        // --- a coluna de horários da tabela de salas ---
        JsonObject sala = new JsonObject();
        com.google.gson.JsonArray lista = new com.google.gson.JsonArray();
        lista.add("3T1");
        lista.add("3T2");
        sala.add("slots", lista);
        confere("a tabela mostra o horario por extenso, nao o codigo",
                "terça 13:00-14:40".equals(PainelSalas.horariosResumidos(sala)));
        confere("sala sem 'slots' nao quebra a tabela",
                "".equals(PainelSalas.horariosResumidos(new JsonObject())));
    }

    static boolean todosOsFinsDepoisDe(ComboDeHorario fim, String inicio) {
        for (int i = 0; i < fim.getItemCount(); i++) {
            String rotulo = fim.getItemAt(i);
            if (ComboDeHorario.NAO_INFORMADO.equals(rotulo)) {
                continue;
            }
            if (rotulo.substring(0, 5).compareTo(inicio) <= 0) {
                return false;
            }
        }
        return true;
    }

    // --------------------------------- o dia inteiro de todas as salas (9.16)

    /**
     * O read_room SEM room_id, que e a operacao do "ver o dia inteiro".
     *
     * Nem o check_availability nem o list_rooms respondem essa pergunta: o
     * primeiro exige sala e horario, o segundo exige o trio completo (regra
     * 9.11). Confundir os tres foi o que fez a tela parecer quebrada.
     */
    static void diaInteiro() {
        System.out.println("\n-- dia inteiro de todas as salas (regra 9.16) --");

        JsonObject resposta = new JsonObject();
        resposta.addProperty("count", "2");
        com.google.gson.JsonArray salas = new com.google.gson.JsonArray();
        salas.add(salaComAgenda("3", "Lab IV", "20", "active", "3T1", "livre", null));
        salas.add(salaComAgenda("2", "Lab V", "35", "inactive", "3T2", "indisponivel",
                "joao"));
        resposta.add("rooms", salas);

        String texto = PainelSalas.diaInteiroPorExtenso(resposta, "2027-01-05");
        confere("o texto diz quantas salas e de que dia",
                texto.startsWith("2 salas em 2027-01-05:"));
        confere("cada sala aparece com nome, id e capacidade",
                texto.contains("Lab IV (ID 3, 20 lugares)")
                        && texto.contains("Lab V (ID 2, 35 lugares, inativa)"));
        confere("a agenda de cada sala vem junto",
                texto.contains("3T1") && texto.contains("3T2"));
        confere("quem reservou aparece na linha do horario",
                texto.contains("reservada por joao"));

        JsonObject vazia = new JsonObject();
        vazia.add("rooms", new com.google.gson.JsonArray());
        confere("banco sem sala nenhuma nao quebra a tela",
                "Nenhuma sala cadastrada.".equals(
                        PainelSalas.diaInteiroPorExtenso(vazia, "2027-01-05")));
        confere("resposta sem 'rooms' tambem nao quebra",
                PainelSalas.diaInteiroPorExtenso(new JsonObject(), "2027-01-05") != null);

        // As duas dicas que apontam para o botao certo quando o servidor
        // recusa a pergunta errada.
        confere("o 400 do list_rooms aponta o \"dia de todas as salas\"",
                Mensagens.dica("400", "list_rooms_response")
                        .contains("dia de todas as salas"));
        confere("o 400 do check_availability aponta o mesmo caminho",
                Mensagens.dica("400", "check_availability_response")
                        .contains("dia de todas as salas"));
    }

    static JsonObject salaComAgenda(String id, String nome, String capacidade, String estado,
                                    String slot, String status, String dono) {
        JsonObject sala = new JsonObject();
        sala.addProperty("room_id", id);
        sala.addProperty("name", nome);
        sala.addProperty("capacity", capacidade);
        sala.addProperty("room_status", estado);
        sala.addProperty("date", "2027-01-05");
        com.google.gson.JsonArray agenda = new com.google.gson.JsonArray();
        JsonObject horario = new JsonObject();
        horario.addProperty("slot", slot);
        horario.addProperty("start_time", "13:00");
        horario.addProperty("end_time", "13:50");
        horario.addProperty("slot_status", status);
        if (dono != null) {
            horario.addProperty("user", dono);
        }
        agenda.add(horario);
        sala.add("agenda", agenda);
        return sala;
    }

    // ------------------------------------------- aviso de horário que passou

    /**
     * O protocolo responde 400 "Dados da reserva em formato invalido" a
     * qualquer erro de formato, inclusive ao horario que ja passou. A tela diz
     * antes, e com todas as letras -- era o aviso que mais confundia.
     */
    static void horarioQuePassou() {
        System.out.println("\n-- aviso de horario que passou --");
        java.time.LocalDate hoje = java.time.LocalDate.now();

        String ontem = PainelReservas.avisoDePassado(hoje.minusDays(1).toString(), "13:00");
        confere("dia passado e avisado antes de mandar", ontem != null);
        confere("e o aviso diz que o DIA passou, nao \"dados invalidos\"",
                ontem.contains("já passou"));

        confere("dia futuro nao e barrado",
                PainelReservas.avisoDePassado(hoje.plusDays(7).toString(), "13:00") == null);

        // Hoje depende da hora: as 07:30 ja passaram se agora e mais tarde.
        String cedo = PainelReservas.avisoDePassado(hoje.toString(), "07:30");
        boolean passouDas730 = java.time.LocalTime.now()
                .isAfter(java.time.LocalTime.of(7, 30));
        confere("hoje de manha cedo e avisado so depois de a aula comecar",
                passouDas730 ? cedo != null && cedo.contains("já começou") : cedo == null);

        // Data invalida nao e problema DESTE aviso: quem responde e o servidor,
        // com a regra dele. Inventar uma mensagem aqui esconderia o erro real.
        confere("data invalida passa adiante, para o servidor responder",
                PainelReservas.avisoDePassado("2026-02-30", "13:00") == null);
        confere("campo vazio nao vira aviso de passado",
                PainelReservas.avisoDePassado("", "13:00") == null
                        && PainelReservas.avisoDePassado("2027-01-01", "") == null);

        // A coluna "Disponível" da tabela de salas: no passado, o verbo muda.
        confere("sem filtro de passado, a coluna diz \"livre\"",
                "livre".equals(PainelSalas.disponibilidade("true", false)));
        confere("com filtro de passado, a coluna diz \"estava livre\"",
                "estava livre".equals(PainelSalas.disponibilidade("true", true)));
        confere("e \"estava indisponível\" quando ocupada",
                "estava indisponível".equals(PainelSalas.disponibilidade("false", true)));
        confere("sem 'available' continua travessao, passado ou nao",
                "—".equals(PainelSalas.disponibilidade(null, true)));
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
