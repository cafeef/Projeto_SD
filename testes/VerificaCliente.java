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
        salasComHorarioLivre();
        blocosLegiveis();

        System.out.println("\n===== cliente: " + ok + " passaram, " + falhas + " falharam =====");
        if (falhas > 0) System.exit(1);
    }

    // ------------------------------------------------------------------ salas

    static void requisicoesDeSala() {
        System.out.println("\n-- requisicoes de sala --");

        // A aba Salas faz duas perguntas, e cada uma tem a sua operacao.

        // 1) "O que tem no dia 30?" -> read_room SEM room_id (regra 9.16).
        // A chave fica de fora de proposito: e a ausencia dela que pede todas
        // as salas, e manda-la vazia seria 400.
        JsonObject dia = PainelSalas.requisicaoDoDia(TOKEN, " 2026-11-30 ");
        confere("a grade do dia usa read_room (regra 9.16)",
                "read_room".equals(texto(dia, "op")));
        confere("read_room comeca pela chave 'op' (regra 2.1)",
                dia.keySet().iterator().next().equals("op"));
        confere("a grade do dia manda so op, token e date",
                dia.keySet().equals(java.util.Set.of("op", "token", "date")));
        confere("o room_id fica de fora: e ele que pede TODAS as salas",
                !dia.has("room_id"));
        confere("o espaco digitado em volta da data e aparado",
                "2026-11-30".equals(texto(dia, "date")));

        // 2) "Quais salas existem?" -> list_rooms sem filtro nenhum. E o
        // catalogo, e nao uma pergunta sobre um dia: o trio data/inicio/fim
        // do list_rooms e tudo ou nada (regra 9.11), e sem hora a pergunta
        // "esta livre no dia 30?" nao teria resposta unica.
        JsonObject catalogo = PainelSalas.requisicaoDoCatalogo(TOKEN);
        confere("o catalogo usa list_rooms",
                "list_rooms".equals(texto(catalogo, "op")));
        confere("o catalogo nao manda filtro nenhum",
                catalogo.keySet().equals(java.util.Set.of("op", "token")));

        // --- create_room ---
        JsonObject cadastro = PainelAdminSalas.requisicaoDeCadastro(TOKEN, " Sala B12 ", " 12 ",
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
        JsonObject alteracao = PainelAdminSalas.requisicaoDeAlteracao(TOKEN, "7", "Sala B12", "16",
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
                PainelAdminSalas.requisicaoDeAlteracao(TOKEN, "7", "", "", "", "", "", "")
                        .getAsJsonArray("resources").toString().equals("[\"0\"]"));
        confere("update_room com recursos preenchidos manda a lista",
                PainelAdminSalas.requisicaoDeAlteracao(TOKEN, "7", "", "", "", "tv, projetor", "", "")
                        .getAsJsonArray("resources").toString()
                        .equals("[\"tv\",\"projetor\"]"));
        // No create_room o vazio continua sendo "sala sem recurso nenhum":
        // nao ha recurso anterior para manter nem para apagar.
        confere("create_room com recursos em branco manda [] (sala sem recursos)",
                PainelAdminSalas.requisicaoDeCadastro(TOKEN, "Sala X", "4", "Bloco A", "", "2M1")
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
                PainelAdminSalas.slotsDeTexto("2M1").toString().equals("[\"2M1\"]"));
        confere("varios, com o espaco aparado",
                PainelAdminSalas.slotsDeTexto(" 2M1 , 3T1 ,4N2").toString()
                        .equals("[\"2M1\",\"3T1\",\"4N2\"]"));
        // O codigo da grade e maiusculo; punir quem digita "2m1" seria so
        // transformar um deslize de teclado num 400 do servidor.
        confere("minuscula vira maiuscula",
                PainelAdminSalas.slotsDeTexto("2m1, 3t1").toString()
                        .equals("[\"2M1\",\"3T1\"]"));
        confere("campo vazio vira lista vazia",
                PainelAdminSalas.slotsDeTexto("").size() == 0);
        confere("virgula dobrada nao cria horario vazio",
                PainelAdminSalas.slotsDeTexto("2M1,,3T1").size() == 2);

        // Regra 8.2 no create_room: obrigatorio.
        JsonObject semHorario = PainelAdminSalas.requisicaoDeCadastro(TOKEN, "Sala X", "10",
                "Bloco A", "", "");
        confere("create_room sem horario manda slots vazio, e o servidor recusa",
                semHorario.getAsJsonArray("slots").size() == 0);

        // Regra 8.7 no update_room: vazio MANTEM, nao apaga.
        JsonObject semMudarHorario = PainelAdminSalas.requisicaoDeAlteracao(TOKEN, "7", "", "", "",
                "", "", "");
        confere("update_room com horario em branco manda [] (= nao alterar)",
                semMudarHorario.has("slots")
                        && semMudarHorario.getAsJsonArray("slots").size() == 0);
        JsonObject comHorario = PainelAdminSalas.requisicaoDeAlteracao(TOKEN, "7", "", "", "",
                "", "", "5N1, 5N2");
        confere("update_room com horarios manda a lista nova",
                comHorario.getAsJsonArray("slots").toString().equals("[\"5N1\",\"5N2\"]"));

        // A tabela e o formulario conversam pelo mesmo texto.
        JsonObject resposta = com("slots", PainelAdminSalas.slotsDeTexto("2M1, 3T1"));
        confere("o array da resposta volta para '2M1, 3T1'",
                "2M1, 3T1".equals(PainelSalas.listaParaTexto(resposta, "slots")));
        confere("resposta sem 'slots' nao quebra a tabela",
                "".equals(PainelSalas.listaParaTexto(new JsonObject(), "slots")));
    }

    static void recursos() {
        System.out.println("\n-- campo de recursos --");

        confere("texto vazio vira array vazio, nao array com item vazio",
                PainelAdminSalas.recursosDeTexto("").size() == 0);
        confere("texto so com espaco vira array vazio",
                PainelAdminSalas.recursosDeTexto("   ").size() == 0);
        confere("um recurso so",
                PainelAdminSalas.recursosDeTexto("projetor").toString().equals("[\"projetor\"]"));
        confere("dois recursos, com o espaco aparado",
                PainelAdminSalas.recursosDeTexto(" projetor ,  tv ").toString()
                        .equals("[\"projetor\",\"tv\"]"));
        // "tv,,projetor" digitado as pressas nao deve criar um recurso sem nome.
        confere("virgula dobrada nao cria recurso de nome vazio",
                PainelAdminSalas.recursosDeTexto("tv,,projetor").toString()
                        .equals("[\"tv\",\"projetor\"]"));
        confere("a ordem digitada e preservada",
                PainelAdminSalas.recursosDeTexto("tv, projetor").toString()
                        .equals("[\"tv\",\"projetor\"]"));
        confere("virgula no fim nao cria item extra",
                PainelAdminSalas.recursosDeTexto("tv,").size() == 1);

        // Ida e volta: o array da resposta volta ao formato do campo de texto.
        JsonObject resposta = com("resources", PainelAdminSalas.recursosDeTexto("projetor, tv"));
        confere("o array da resposta volta para 'projetor, tv'",
                "projetor, tv".equals(PainelSalas.recursosParaTexto(resposta)));
        confere("sala sem recurso nenhum nao vira texto vazio na tela",
                "(nenhum)".equals(PainelSalas.recursosParaTexto(new JsonObject())));
    }

    // ------------------------------------- "so salas com horario livre" (9.16)

    /**
     * O filtro que a aba Salas aplica: so entra na grade a sala que tem pelo
     * menos um bloco RESERVAVEL naquele dia.
     *
     * "Reservavel" exclui o que ja passou. Um bloco de ontem continua "livre"
     * na resposta do servidor (regra 9.15), mas o create_reservation o recusa
     * (regra 9.3) -- conta-lo faria a sala aparecer na lista de disponiveis
     * sem ter nada para oferecer.
     */
    static void salasComHorarioLivre() {
        System.out.println("\n-- filtro de salas com horario livre --");
        String amanha = java.time.LocalDate.now().plusDays(1).toString();
        String ontem = java.time.LocalDate.now().minusDays(1).toString();

        JsonObject livre = salaComAgenda("3", "Lab IV", "20", "active", "3T1", "livre", null);
        confere("sala com bloco livre amanha entra na grade",
                GradeDoDia.temHorarioLivre(livre, amanha));
        confere("a MESMA sala, com o dia ja passado, fica de fora",
                !GradeDoDia.temHorarioLivre(livre, ontem));

        JsonObject ocupada = salaComAgenda("2", "Lab V", "35", "active", "3T1",
                "indisponivel", "joao");
        confere("sala com o unico bloco ocupado fica de fora",
                !GradeDoDia.temHorarioLivre(ocupada, amanha));

        JsonObject semAgenda = new JsonObject();
        semAgenda.addProperty("room_id", "9");
        confere("sala que nao funciona nesse dia fica de fora",
                !GradeDoDia.temHorarioLivre(semAgenda, amanha));
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
        GradeDeSlots slots = new GradeDeSlots();
        confere("a grade tem um bloco para cada horario da semana",
                slots.selecionados().isEmpty());
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

        // O catalogo (list_rooms) continua em texto: ele e uma leitura, e nao
        // uma agenda. O dia vira GRADE, e quem o testa e salasComHorarioLivre.
        JsonObject catalogo = new JsonObject();
        com.google.gson.JsonArray lista = new com.google.gson.JsonArray();
        JsonObject sala = salaComAgenda("3", "Lab IV", "20", "active", "3T1", "livre", null);
        com.google.gson.JsonArray slotsDaSala = new com.google.gson.JsonArray();
        slotsDaSala.add("3T1");
        slotsDaSala.add("3T2");
        sala.add("slots", slotsDaSala);
        sala.addProperty("location", "Bloco C");
        lista.add(sala);
        catalogo.add("rooms", lista);

        String texto = PainelSalas.catalogoPorExtenso(catalogo);
        confere("o catalogo diz quantas salas existem",
                texto.startsWith("1 sala(s) cadastrada(s):"));
        confere("cada sala aparece com nome, id, capacidade e local",
                texto.contains("Lab IV  (ID 3)")
                        && texto.contains("20 lugares")
                        && texto.contains("Bloco C"));
        confere("os horarios de funcionamento vem por extenso, nao em codigo",
                texto.contains("terça 13:00-14:40"));

        JsonObject vazia = new JsonObject();
        vazia.add("rooms", new com.google.gson.JsonArray());
        confere("banco sem sala nenhuma nao quebra a tela",
                "Nenhuma sala cadastrada.".equals(
                        PainelSalas.catalogoPorExtenso(vazia)));
        confere("resposta sem 'rooms' tambem nao quebra",
                PainelSalas.catalogoPorExtenso(new JsonObject()) != null);

        // As duas dicas que apontam para o botao certo quando o servidor
        // recusa a pergunta errada.
        confere("o 400 do list_rooms aponta a grade do dia",
                Mensagens.dica("400", "list_rooms_response").contains("aba Salas"));
        confere("o 400 do check_availability aponta o mesmo caminho",
                Mensagens.dica("400", "check_availability_response")
                        .contains("grade da aba Salas"));
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

    // ------------------------------------------------ legibilidade dos blocos

    /**
     * Todo bloco da grade precisa ser LEGIVEL, em qualquer tema do sistema.
     *
     * O bug que isto tranca: o GTK pinta o botao com o tema e ignora o
     * setBackground, entao o bloco escolhido ficava com texto branco sobre
     * fundo branco -- invisivel. A correcao e desligar o preenchimento do
     * Look and Feel (setContentAreaFilled(false)) e deixar o proprio
     * componente pintar (setOpaque(true)).
     *
     * Testar cor renderizada seria fragil; o que se testa aqui e a condicao
     * que fazia a cor sumir, mais o contraste entre texto e fundo.
     */
    static void blocosLegiveis() {
        System.out.println("\n-- legibilidade dos blocos da grade --");
        String amanha = java.time.LocalDate.now().plusDays(1).toString();

        GradeDoDia grade = new GradeDoDia(g -> { });
        JsonObject resposta = new JsonObject();
        com.google.gson.JsonArray salas = new com.google.gson.JsonArray();
        salas.add(salaComAgenda("3", "Lab IV", "20", "active", "3T1", "livre", null));
        salas.add(salaComAgenda("2", "Lab V", "35", "active", "3T1", "indisponivel", "joao"));
        resposta.add("rooms", salas);
        grade.mostrar(resposta, amanha, false);

        java.util.List<javax.swing.JButton> botoes = botoesDe(grade);
        confere("a grade desenhou blocos", !botoes.isEmpty());
        confere("nenhum bloco deixa o tema pintar por cima da cor",
                botoes.stream().allMatch(b -> b.isOpaque() && !b.isContentAreaFilled()));
        confere("todo bloco tem texto legivel sobre o proprio fundo",
                botoes.stream().allMatch(b -> legivel(b.getForeground(), b.getBackground())));

        // E o estado que quebrou: o bloco escolhido.
        javax.swing.JButton livre = botoes.stream()
                .filter(b -> "livre".equals(b.getText()))
                .findFirst().orElse(null);
        confere("ha um bloco livre para escolher", livre != null);
        if (livre != null) {
            livre.doClick();
            confere("o bloco escolhido muda de texto", "escolhido".equals(livre.getText()));
            confere("e continua legivel depois de escolhido",
                    legivel(livre.getForeground(), livre.getBackground()));
            confere("o escolhido nao e branco no branco",
                    !livre.getForeground().equals(livre.getBackground()));
        }

        // A grade de horarios da sala tem o mesmo risco.
        GradeDeSlots slots = new GradeDeSlots();
        slots.selecionar("3T1");
        java.util.List<javax.swing.AbstractButton> blocos = marcaveisDe(slots);
        confere("a grade de horarios desenhou blocos", !blocos.isEmpty());
        confere("os blocos de horario tambem pintam o proprio fundo",
                blocos.stream().allMatch(b -> b.isOpaque() && !b.isContentAreaFilled()));
        confere("o horario marcado aparece com \"✓\"",
                blocos.stream().anyMatch(b -> "✓".equals(b.getText())));
        confere("o horario marcado fica legivel",
                blocos.stream()
                        .filter(b -> "✓".equals(b.getText()))
                        .allMatch(b -> legivel(b.getForeground(), b.getBackground())));
    }

    /**
     * Contraste suficiente entre texto e fundo (razao de luminancia >= 3).
     *
     * E o limite que a WCAG pede para texto grande. Branco no branco da 1.0,
     * que era exatamente o caso do bloco escolhido antes da correcao.
     */
    static boolean legivel(java.awt.Color texto, java.awt.Color fundo) {
        if (texto == null || fundo == null) {
            return false;
        }
        double a = luminancia(texto);
        double b = luminancia(fundo);
        double clara = Math.max(a, b);
        double escura = Math.min(a, b);
        return (clara + 0.05) / (escura + 0.05) >= 3.0;
    }

    static double luminancia(java.awt.Color c) {
        double[] canais = {c.getRed() / 255.0, c.getGreen() / 255.0, c.getBlue() / 255.0};
        for (int i = 0; i < 3; i++) {
            canais[i] = canais[i] <= 0.03928
                    ? canais[i] / 12.92
                    : Math.pow((canais[i] + 0.055) / 1.055, 2.4);
        }
        return 0.2126 * canais[0] + 0.7152 * canais[1] + 0.0722 * canais[2];
    }

    /** Os blocos marcaveis (JToggleButton) de uma grade. */
    static java.util.List<javax.swing.AbstractButton> marcaveisDe(java.awt.Container c) {
        java.util.List<javax.swing.AbstractButton> achados = new java.util.ArrayList<>();
        for (java.awt.Component x : c.getComponents()) {
            if (x instanceof javax.swing.JToggleButton b) {
                achados.add(b);
            }
            if (x instanceof java.awt.Container k) {
                achados.addAll(marcaveisDe(k));
            }
        }
        return achados;
    }

    static java.util.List<javax.swing.JButton> botoesDe(java.awt.Container c) {
        java.util.List<javax.swing.JButton> achados = new java.util.ArrayList<>();
        for (java.awt.Component x : c.getComponents()) {
            if (x instanceof javax.swing.JButton b && b.getBorder() != null) {
                achados.add(b);
            }
            if (x instanceof java.awt.Container k) {
                achados.addAll(botoesDe(k));
            }
        }
        return achados;
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

        // A agenda marca o que já passou: a do servidor não olha a hora, de
        // propósito (regra 9.15), e sem a marca ela ofereceria um horário que
        // o create_reservation recusa.
        JsonObject comAgenda = new JsonObject();
        comAgenda.addProperty("date", hoje.minusDays(1).toString());
        com.google.gson.JsonArray agenda = new com.google.gson.JsonArray();
        JsonObject bloco = new JsonObject();
        bloco.addProperty("slot", "3T1");
        bloco.addProperty("start_time", "13:00");
        bloco.addProperty("end_time", "13:50");
        bloco.addProperty("slot_status", "livre");
        agenda.add(bloco);
        comAgenda.add("agenda", agenda);
        confere("a agenda de ontem marca o horario como ja passado",
                PainelSalas.agendaPorExtenso(comAgenda).contains("(já passou)"));

        comAgenda.addProperty("date", hoje.plusDays(7).toString());
        confere("a agenda de um dia futuro nao marca nada",
                !PainelSalas.agendaPorExtenso(comAgenda).contains("já passou"));
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
