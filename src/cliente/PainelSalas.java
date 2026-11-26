package cliente;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import protocolo.Horarios;
import ui.Estilo;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;

/**
 * Aba de salas: consulta para todos, cadastro e alteracao para administradores.
 *
 * Cobre as cinco operacoes de sala da EP-2 -- list_rooms, read_room,
 * create_room, update_room e delete_room.
 *
 * O grupo de administracao e escondido de quem nao e admin, mas isso e
 * conveniencia de tela, nao seguranca: a autorizacao mora na whitelist de
 * tokens do servidor (regra 3.14), e um cliente de outro grupo que mandasse
 * create_room sem ser admin receberia 403 do mesmo jeito.
 */
final class PainelSalas extends JPanel {

    private static final long serialVersionUID = 1L;

    private final transient Canal canal;

    // --- filtros do list_rooms ---
    private final JTextField filtroData = estreito(new JTextField(10));
    private final ComboDeHorario filtroInicio = ComboDeHorario.inicio(ComboDeHorario.QUALQUER);
    private final ComboDeHorario filtroFim = ComboDeHorario.fim(ComboDeHorario.QUALQUER);
    private final JTextField filtroCapacidade = estreito(new JTextField(4));
    private final JButton botaoListar = Estilo.botaoPrincipal("Listar salas");
    private final JButton botaoLimparFiltros = new JButton("Limpar filtros");

    // As colunas seguem a ordem em que o protocolo descreve o objeto Sala.
    private final JTable tabela = Estilo.tabela("ID", "Nome", "Capacidade", "Local",
            "Recursos", "Horários", "Estado", "Disponível");
    private final JButton botaoDetalhes = new JButton("Ver detalhes da sala");
    private final JButton botaoDiaInteiro = new JButton("Ver o dia de todas as salas");

    // --- formulario de administracao ---
    private final JPanel grupoAdmin = Estilo.grupo("Administração de salas");
    private final JTextField salaNome = estreito(new JTextField(20));
    private final JTextField salaCapacidade = estreito(new JTextField(4));
    private final JTextField salaLocal = estreito(new JTextField(22));
    private final JTextField salaRecursos = estreito(new JTextField(26));
    private final SeletorDeSlots salaHorarios = new SeletorDeSlots();

    /**
     * Os codigos de horario de cada sala listada, por room_id.
     *
     * A coluna da tabela passou a mostrar "terça 13:00-15:30", que se le mas
     * nao se traduz de volta sem ambiguidade. Guardar os codigos como vieram
     * e o que permite o formulario marcar exatamente os horarios da sala
     * escolhida.
     */
    private final transient java.util.Map<String, String> codigosDeHorario =
            new java.util.HashMap<>();
    private final JComboBox<String> salaEstado = new JComboBox<>(new String[] {"active", "inactive"});
    private final JButton botaoCadastrar = Estilo.botaoPrincipal("Cadastrar nova sala");
    private final JButton botaoSalvar = new JButton("Salvar na sala selecionada");
    private final JButton botaoExcluir = Estilo.botaoDestrutivo("Excluir sala");
    private final JButton botaoLimparForm = new JButton("Limpar formulário");

    PainelSalas(Canal canal) {
        this.canal = canal;
        montar();
        ligarAcoes();
    }

    // ----------------------------------------------------------------- layout

    private void montar() {
        setLayout(new BorderLayout(0, 12));

        // Os quatro filtros numa linha só: juntos eles formam uma pergunta
        // unica ("que salas servem, nesse horario?"), e empilhados ocupariam
        // altura que a tabela faz mais falta ter.
        JPanel linhaFiltros = linha(
                filtroData,
                rotulo("Início:"), filtroInicio,
                rotulo("Fim:"), filtroFim,
                rotulo("Capacidade mín.:"), filtroCapacidade);

        JPanel botoesFiltro = linha(botaoListar, botaoLimparFiltros);

        JPanel filtros = Estilo.grupo("Consultar salas");
        new Estilo.Grade(filtros)
                .campo("Data (AAAA-MM-DD):", linhaFiltros)
                .linhaCheia(botoesFiltro)
                .dica("Data, início e fim vão juntos, ou nenhum dos três.")
                .dica("Só com os três o servidor diz se a sala está livre.")
                .dica("Para ver um dia inteiro sem escolher horário,"
                        + " preencha a data e use \"Ver o dia de todas as salas\".");

        JScrollPane rolagem = new JScrollPane(tabela);
        rolagem.setPreferredSize(new Dimension(640, 180));

        JPanel acoesTabela = linha(botaoDetalhes, botaoDiaInteiro);

        JPanel centro = new JPanel(new BorderLayout(0, 8));
        centro.add(rolagem, BorderLayout.CENTER);
        centro.add(acoesTabela, BorderLayout.SOUTH);

        JPanel botoesAdmin = new JPanel();
        botoesAdmin.setLayout(new BoxLayout(botoesAdmin, BoxLayout.X_AXIS));
        botoesAdmin.add(botaoCadastrar);
        botoesAdmin.add(Box.createHorizontalStrut(8));
        botoesAdmin.add(botaoSalvar);
        botoesAdmin.add(Box.createHorizontalStrut(8));
        botoesAdmin.add(botaoLimparForm);
        botoesAdmin.add(Box.createHorizontalGlue());
        botoesAdmin.add(botaoExcluir);

        new Estilo.Grade(grupoAdmin)
                .campo("Nome:", linha(salaNome, rotulo("Capacidade:"), salaCapacidade))
                .campo("Local:", linha(salaLocal, rotulo("Estado:"), salaEstado))
                .campo("Recursos:", salaRecursos)
                .campo("Funciona em:", new JScrollPane(salaHorarios))
                .linhaCheia(botoesAdmin)
                .dica("Marque os horários em que a sala funciona:"
                        + " ctrl+clique para avulsos, shift+clique para uma faixa.")
                .dica("Ao alterar, nenhum horário marcado mantém os atuais.")
                .dica("Recursos separados por vírgula; em branco apaga todos.")
                .dica("Nome: letras, números, espaço e hífen, até 30 caracteres.")
                .dica("Clique numa sala da tabela para carregá-la aqui.");

        add(filtros, BorderLayout.NORTH);
        add(centro, BorderLayout.CENTER);
        add(grupoAdmin, BorderLayout.SOUTH);
    }

    /** Uma fila horizontal de componentes, encostada a esquerda. */
    private static JPanel linha(java.awt.Component... componentes) {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.X_AXIS));
        for (int i = 0; i < componentes.length; i++) {
            if (i > 0) {
                p.add(Box.createHorizontalStrut(8));
            }
            p.add(componentes[i]);
        }
        p.add(Box.createHorizontalGlue());
        return p;
    }

    private static javax.swing.JLabel rotulo(String texto) {
        return new javax.swing.JLabel(texto);
    }

    /**
     * Fixa a largura minima do campo na largura que ele pede.
     *
     * Sem isso, quando a janela fica menor do que o painel precisa, o
     * GridBagLayout recua para os tamanhos MINIMOS, e o minimo de um
     * JTextField e um quadradinho de 23 pixels -- bonito de ver no retrato,
     * impossivel de digitar. Com o minimo igual ao preferido, o campo para de
     * encolher e quem cede espaco e a rolagem da aba.
     */
    private static JTextField estreito(JTextField campo) {
        campo.setMinimumSize(campo.getPreferredSize());
        return campo;
    }

    /** Mostra ou esconde o grupo de administracao conforme o perfil da sessao. */
    void aplicarPerfil() {
        grupoAdmin.setVisible(canal.ehAdmin());
        revalidate();
        repaint();
    }

    void habilitar(boolean habilitado) {
        botaoListar.setEnabled(habilitado);
        botaoDetalhes.setEnabled(habilitado);
        botaoDiaInteiro.setEnabled(habilitado);
        botaoCadastrar.setEnabled(habilitado);
        botaoSalvar.setEnabled(habilitado);
        botaoExcluir.setEnabled(habilitado);
    }

    void limpar() {
        Estilo.modeloDe(tabela).setRowCount(0);
        limparFormulario();
    }

    // ------------------------------------------------------------------ ações

    private void ligarAcoes() {
        botaoListar.addActionListener(e -> listar());
        botaoLimparFiltros.addActionListener(e -> {
            filtroData.setText("");
            filtroInicio.selecionarHora("");
            filtroFim.selecionarHora("");
            filtroCapacidade.setText("");
            listar();
        });
        botaoDetalhes.addActionListener(e -> verDetalhes());
        botaoDiaInteiro.addActionListener(e -> verDiaInteiro());
        botaoCadastrar.addActionListener(e -> cadastrar());
        botaoSalvar.addActionListener(e -> salvar());
        botaoExcluir.addActionListener(e -> excluir());
        botaoLimparForm.addActionListener(e -> {
            tabela.clearSelection();
            limparFormulario();
        });
        // Clicar numa linha carrega a sala no formulario: e o que permite
        // alterar sem redigitar tudo -- e, no caso de 'resources', e o que
        // evita apagar a lista por descuido, ja que ela viaja completa.
        tabela.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                carregarSelecionada();
            }
        });
    }

    /** Listagem pedida pelo usuario: avisa quando nao veio sala nenhuma. */
    private void listar() {
        listar(true);
    }

    /**
     * @param avisarSeVazio false nas recargas automaticas depois de cadastrar,
     *        alterar ou remover. Sem isso, remover a ultima sala abre dois
     *        avisos em sequencia -- "Sala removida com sucesso" e, logo atras,
     *        "Nenhuma sala atende aos filtros", que soa como se algo tivesse
     *        dado errado quando na verdade deu certo.
     */
    private void listar(boolean avisarSeVazio) {
        String data = filtroData.getText().trim();
        String inicio = filtroInicio.hora();
        String fim = filtroFim.hora();
        int pedacos = (data.isEmpty() ? 0 : 1) + (inicio.isEmpty() ? 0 : 1)
                + (fim.isEmpty() ? 0 : 1);

        // O trio data+início+fim é tudo ou nada (regra 9.11): sem hora, "a
        // sala está livre no dia 30?" não tem resposta única, porque ela pode
        // estar livre de manhã e ocupada à tarde. Mandar o trio pela metade é
        // 400 garantido, e a tela sabe disso antes de mandar.
        if (pedacos != 0 && pedacos != 3) {
            if (avisarSeVazio) {
                // Clique de quem está consultando: explica e aponta o caminho.
                JOptionPane.showMessageDialog(this,
                        "Data, início e fim vão juntos — ou nenhum dos três.\n\n"
                                + "Para ver um dia inteiro sem escolher horário,"
                                + " use \"Ver o dia de todas as salas\".",
                        "Filtros incompletos", JOptionPane.WARNING_MESSAGE);
                return;
            }
            // Recarga automática (depois de salvar ou excluir): lista todas as
            // salas, omitindo o trio incompleto. Travar aqui deixaria a tabela
            // com dados velhos por causa de um filtro que o usuário nem
            // terminou de preencher.
            data = "";
            inicio = "";
            fim = "";
        }

        JsonObject req = requisicaoDeListagem(canal.token(), data, inicio, fim,
                filtroCapacidade.getText());

        canal.trocar(req, resposta -> {
            if (!"200".equals(campo(resposta, "status"))) {
                canal.informar(resposta);
                return;
            }
            preencherTabela(resposta, avisarSeVazio);
        });
    }

    private void preencherTabela(JsonObject resposta, boolean avisarSeVazio) {
        var modelo = Estilo.modeloDe(tabela);
        modelo.setRowCount(0);
        codigosDeHorario.clear();
        JsonArray salas = resposta.has("rooms") && resposta.get("rooms").isJsonArray()
                ? resposta.getAsJsonArray("rooms")
                : new JsonArray();
        for (JsonElement elemento : salas) {
            JsonObject sala = elemento.getAsJsonObject();
            codigosDeHorario.put(campo(sala, "room_id"), listaParaTexto(sala, "slots"));
            modelo.addRow(new Object[] {
                    campo(sala, "room_id"),
                    campo(sala, "name"),
                    campo(sala, "capacity"),
                    campo(sala, "location"),
                    listaParaTexto(sala, "resources"),
                    // A tabela mostra os horarios por extenso; os codigos
                    // ficam guardados a parte, para o formulario poder marcar
                    // a sala selecionada sem ter de traduzir de volta.
                    horariosResumidos(sala),
                    "active".equals(campo(sala, "room_status")) ? "ativa" : "inativa",
                    // Só há "passado" a marcar quando a pergunta tinha hora:
                    // sem o trio o servidor nem manda 'available'.
                    disponibilidade(campo(sala, "available"),
                            Passado.jaPassou(filtroData.getText(), filtroInicio.hora()))});
        }
        if (modelo.getRowCount() == 0 && avisarSeVazio) {
            JOptionPane.showMessageDialog(this,
                    "Nenhuma sala atende aos filtros informados.",
                    "Consulta de salas", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    /**
     * O dia inteiro de TODAS as salas, numa requisição só (regra 9.16, v2.11).
     *
     * Usa o `read_room` SEM `room_id`, que é a operação feita para isso. Nem o
     * `check_availability` nem o `list_rooms` servem aqui, e por motivo:
     *
     *   - `check_availability` exige `room_id`, `start_time` e `end_time` —
     *     ele responde "esta sala está livre NESTE recorte?", que é uma
     *     pergunta sobre uma sala só;
     *   - `list_rooms` aceita a data, mas só junto com início e fim (regra
     *     9.11): sem hora, "a sala está livre no dia 30?" não tem resposta
     *     única, porque ela pode estar livre de manhã e ocupada à tarde.
     *
     * O `read_room` sem `room_id` responde outra pergunta, e é a que interessa
     * aqui: "como está o dia inteiro, sala por sala, horário por horário?".
     */
    private void verDiaInteiro() {
        String data = filtroData.getText().trim();
        if (data.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Preencha a data nos filtros para ver o dia inteiro.",
                    "Agenda do dia", JOptionPane.WARNING_MESSAGE);
            return;
        }

        JsonObject req = Canal.requisicao("read_room");
        req.addProperty("token", canal.token());
        // Sem 'room_id' de propósito: é a chave ausente que pede todas as
        // salas. Mandá-la vazia seria 400 (regra 9.16).
        req.addProperty("date", data);

        canal.trocar(req, resposta -> {
            if (!"200".equals(campo(resposta, "status"))) {
                canal.informar(resposta);
                return;
            }
            mostrarTexto("Agenda de " + data, diaInteiroPorExtenso(resposta, data));
        });
    }

    /**
     * A resposta do read_room sem room_id, em texto.
     *
     * Uma sala por bloco, com a agenda de cada uma. Sala que não funciona
     * naquele dia aparece dizendo isso, em vez de sumir: saber que a sala
     * existe e não abre naquele dia é informação, e a ausência dela faria
     * parecer que a sala foi removida.
     */
    static String diaInteiroPorExtenso(JsonObject resposta, String data) {
        if (!resposta.has("rooms") || !resposta.get("rooms").isJsonArray()) {
            return "O servidor não devolveu salas.";
        }
        JsonArray salas = resposta.getAsJsonArray("rooms");
        if (salas.size() == 0) {
            return "Nenhuma sala cadastrada.";
        }
        StringBuilder texto = new StringBuilder(salas.size() == 1
                ? "1 sala em " + data + ":"
                : salas.size() + " salas em " + data + ":");
        for (JsonElement e : salas) {
            JsonObject sala = e.getAsJsonObject();
            texto.append("\n\n").append(campo(sala, "name"))
                    .append(" (ID ").append(campo(sala, "room_id")).append(", ")
                    .append(campo(sala, "capacity")).append(" lugares")
                    .append("active".equals(campo(sala, "room_status"))
                            ? ")" : ", inativa)");
            String agenda = agendaPorExtenso(sala);
            // agendaPorExtenso abre com duas quebras para o diálogo de uma
            // sala só; aqui as linhas já vêm separadas pelo bloco acima.
            texto.append(agenda.replaceFirst("^\n\n", "\n"));
        }
        return texto.toString();
    }

    /**
     * Um diálogo com rolagem, para texto que pode ficar longo.
     *
     * O showMessageDialog com uma String cresce com o conteúdo e, com muitas
     * salas, passaria da altura da tela -- os botões ficariam fora do
     * alcance. Com a área de rolagem o diálogo tem tamanho fixo.
     */
    private void mostrarTexto(String titulo, String texto) {
        javax.swing.JTextArea area = new javax.swing.JTextArea(texto, 20, 52);
        area.setEditable(false);
        area.setCaretPosition(0);
        area.setFont(new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN,
                area.getFont().getSize()));
        JOptionPane.showMessageDialog(this, new JScrollPane(area), titulo,
                JOptionPane.INFORMATION_MESSAGE);
    }

    private void verDetalhes() {
        String id = idSelecionado();
        if (id == null) {
            return;
        }
        JsonObject req = Canal.requisicao("read_room");
        req.addProperty("token", canal.token());
        req.addProperty("room_id", id);
        // Com data, o servidor devolve a AGENDA do dia (regra 8.5): quem
        // reservou cada horário e o que está livre. É o que o item (e) da
        // Entrega 2 pede. Sem data, vêm só os dados da sala.
        String data = filtroData.getText().trim();
        if (!data.isEmpty()) {
            req.addProperty("date", data);
        }

        canal.trocar(req, resposta -> {
            if (!"200".equals(campo(resposta, "status"))) {
                canal.informar(resposta);
                return;
            }
            StringBuilder texto = new StringBuilder("Sala ")
                    .append(campo(resposta, "room_id")).append("\n\n")
                    .append("Nome: ").append(campo(resposta, "name")).append('\n')
                    .append("Capacidade: ").append(campo(resposta, "capacity"))
                    .append(" lugares\n")
                    .append("Local: ").append(campo(resposta, "location")).append('\n')
                    .append("Recursos: ").append(recursosParaTexto(resposta)).append('\n')
                    .append("Estado: ")
                    .append("active".equals(campo(resposta, "room_status"))
                            ? "ativa" : "inativa (não aceita novas reservas)")
                    .append('\n')
                    .append("Funciona em: ").append(horariosPorExtenso(resposta));

            texto.append(agendaPorExtenso(resposta));
            JOptionPane.showMessageDialog(this, texto.toString(), "Detalhes da sala",
                    JOptionPane.INFORMATION_MESSAGE);
        });
    }

    private void cadastrar() {
        JsonObject req = requisicaoDeCadastro(canal.token(), salaNome.getText(),
                salaCapacidade.getText(), salaLocal.getText(), salaRecursos.getText(),
                salaHorarios.texto());

        canal.trocar(req, resposta -> {
            canal.informar(resposta);
            if ("201".equals(campo(resposta, "status"))) {
                limparFormulario();
                listar(false);
            }
        });
    }

    private void salvar() {
        String id = idSelecionado();
        if (id == null) {
            return;
        }

        JsonObject req = requisicaoDeAlteracao(canal.token(), id, salaNome.getText(),
                salaCapacidade.getText(), salaLocal.getText(), salaRecursos.getText(),
                (String) salaEstado.getSelectedItem(), salaHorarios.texto());

        canal.trocar(req, resposta -> {
            canal.informar(resposta);
            if ("200".equals(campo(resposta, "status"))) {
                listar(false);
            }
        });
    }

    private void excluir() {
        String id = idSelecionado();
        if (id == null) {
            return;
        }
        String nome = (String) Estilo.modeloDe(tabela)
                .getValueAt(tabela.getSelectedRow(), 1);

        int escolha = JOptionPane.showConfirmDialog(this,
                "Remover a sala \"" + nome + "\" (ID " + id + ")?\n\n"
                        + "Salas com reservas futuras não podem ser removidas:\n"
                        + "para essas, altere o estado para \"inactive\".",
                "Excluir sala", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
        if (escolha != JOptionPane.OK_OPTION) {
            return;
        }

        JsonObject req = Canal.requisicao("delete_room");
        req.addProperty("token", canal.token());
        req.addProperty("room_id", id);

        canal.trocar(req, resposta -> {
            canal.informar(resposta);
            if ("200".equals(campo(resposta, "status"))) {
                limparFormulario();
                listar(false);
            }
        });
    }

    // ------------------------------------------------------------------ apoio

    /**
     * Os horários de funcionamento numa linha só: "terça 13:00-15:30".
     *
     * É o que cabe numa célula de tabela. A lista crua de códigos cabia menos
     * e dizia menos: "3T1, 3T2, 3T3" exige decorar a grade para virar uma
     * hora, e era isso que a coluna mostrava antes.
     */
    static String horariosResumidos(JsonObject sala) {
        if (!sala.has("slots") || !sala.get("slots").isJsonArray()) {
            return "";
        }
        List<String> slots = new ArrayList<>();
        for (JsonElement e : sala.getAsJsonArray("slots")) {
            // Horário de uma grade que não é a nossa não quebra a tabela: o
            // resumo o ignora, e os outros continuam aparecendo.
            if (e.isJsonPrimitive() && Horarios.ehSlot(e.getAsString())) {
                slots.add(e.getAsString());
            }
        }
        return Horarios.resumoPorExtenso(slots);
    }

    /** Os horários de funcionamento, legíveis: "2M1 (segunda, 07:30-08:20)". */
    private static String horariosPorExtenso(JsonObject resposta) {
        if (!resposta.has("slots") || !resposta.get("slots").isJsonArray()) {
            return "(não informado)";
        }
        List<String> linhas = new ArrayList<>();
        for (JsonElement e : resposta.getAsJsonArray("slots")) {
            String slot = e.getAsString();
            linhas.add(slot + " (" + Horarios.porExtenso(slot) + ")");
        }
        return linhas.isEmpty() ? "(nenhum)" : "\n  " + String.join("\n  ", linhas);
    }

    /**
     * A agenda do dia, quando a resposta trouxe uma.
     *
     * É aqui que aparece QUEM reservou e EM QUE horário -- o item (e) da
     * Entrega 2. Agenda vazia não é falha: quer dizer que a sala não funciona
     * naquele dia da semana.
     */
    static String agendaPorExtenso(JsonObject resposta) {
        if (!resposta.has("agenda") || !resposta.get("agenda").isJsonArray()) {
            return "\n\nInforme uma data nos filtros para ver a agenda do dia.";
        }
        var agenda = resposta.getAsJsonArray("agenda");
        if (agenda.size() == 0) {
            return "\n\nAgenda de " + campo(resposta, "date")
                    + ": a sala não funciona nesse dia.";
        }
        String data = campo(resposta, "date");
        StringBuilder sb = new StringBuilder("\n\nAgenda de ")
                .append(data).append(":");
        for (JsonElement e : agenda) {
            JsonObject h = e.getAsJsonObject();
            boolean passou = Passado.jaPassou(data, campo(h, "start_time"));
            sb.append("\n  ").append(campo(h, "slot")).append("  ")
                    .append(campo(h, "start_time")).append('-').append(campo(h, "end_time"))
                    .append("  ")
                    .append("livre".equals(campo(h, "slot_status")) ? "livre" : "indisponível");
            // Regra 9.15: a agenda do servidor não olha a hora, de propósito --
            // horário que passou e não foi reservado continua "livre". Quem
            // avisa é o cliente, senão a agenda oferece o que não dá reservar.
            if (passou) {
                sb.append(" (já passou)");
            }
            String dono = campo(h, "user");
            if (dono != null) {
                sb.append(" — reservada por ").append(dono);
                String reserva = campo(h, "reservation_id");
                if (reserva != null) {
                    sb.append(" (reserva ").append(reserva).append(')');
                }
            }
        }
        return sb.toString();
    }

    /** Carrega a sala selecionada no formulario de administracao. */
    private void carregarSelecionada() {
        int linha = tabela.getSelectedRow();
        if (linha < 0) {
            return;
        }
        var modelo = Estilo.modeloDe(tabela);
        salaNome.setText(texto(modelo.getValueAt(linha, 1)));
        salaCapacidade.setText(texto(modelo.getValueAt(linha, 2)));
        salaLocal.setText(texto(modelo.getValueAt(linha, 3)));
        salaRecursos.setText(texto(modelo.getValueAt(linha, 4)));
        salaHorarios.selecionar(codigosDeHorario.get(texto(modelo.getValueAt(linha, 0))));
        salaEstado.setSelectedItem("ativa".equals(texto(modelo.getValueAt(linha, 6)))
                ? "active" : "inactive");
    }

    private void limparFormulario() {
        salaNome.setText("");
        salaCapacidade.setText("");
        salaLocal.setText("");
        salaRecursos.setText("");
        salaHorarios.limpar();
        salaEstado.setSelectedItem("active");
    }

    /**
     * O ID da sala selecionada, ou null -- avisando o usuario -- se nao ha
     * nenhuma. Checagem local: nao vale gastar uma ida ao servidor para
     * descobrir que a tela nao tem sala escolhida.
     */
    private String idSelecionado() {
        int linha = tabela.getSelectedRow();
        if (linha < 0) {
            canal.erro("Selecione uma sala na tabela primeiro.");
            return null;
        }
        return texto(Estilo.modeloDe(tabela).getValueAt(linha, 0));
    }

    // ------------------------------------- montagem das requisicoes de sala
    //
    // Estaticas e sem Swing de proposito: e o JSON que sai daqui que o servidor
    // de outro grupo vai receber no teste de interoperabilidade, entao e a parte
    // que mais merece teste -- e testa-la nao deve exigir abrir uma janela.

    /**
     * Monta o list_rooms. Filtro em branco simplesmente nao vai: a chave
     * ausente e a forma de dizer "sem filtro". Mandar "" tambem funcionaria,
     * mas a requisicao fica mais limpa de ler no terminal na apresentacao.
     */
    static JsonObject requisicaoDeListagem(String token, String data, String inicio,
                                           String fim, String minCapacidade) {
        JsonObject req = Canal.requisicao("list_rooms");
        req.addProperty("token", token);
        acrescentarSeInformado(req, "date", data);
        acrescentarSeInformado(req, "start_time", inicio);
        acrescentarSeInformado(req, "end_time", fim);
        acrescentarSeInformado(req, "min_capacity", minCapacidade);
        return req;
    }

    /**
     * Monta o create_room.
     *
     * O 'room_status' nao vai: a sala nasce "active" por decisao do servidor, e
     * o campo nem e lido no create_room.
     */
    static JsonObject requisicaoDeCadastro(String token, String nome, String capacidade,
                                           String local, String recursos, String horarios) {
        JsonObject req = Canal.requisicao("create_room");
        req.addProperty("token", token);
        req.addProperty("name", nome.trim());
        req.addProperty("capacity", capacidade.trim());
        req.addProperty("location", local.trim());
        req.add("resources", recursosDeTexto(recursos));
        // Obrigatorio (regra 8.2). Vai em MAIUSCULA porque o codigo da grade e
        // maiusculo e digitar "2m1" e esperar que funcione e razoavel.
        req.add("slots", slotsDeTexto(horarios));
        return req;
    }

    /**
     * Monta o update_room, com as seis chaves que o protocolo exige.
     *
     * O formulario foi preenchido com a sala selecionada, entao ele carrega um
     * retrato completo dela: reenviar os valores atuais tem o mesmo efeito que
     * mandar "" ("nao alterar") e deixa claro no terminal o que a sala passa a
     * ser. As chaves sao obrigatorias de todo jeito.
     */
    static JsonObject requisicaoDeAlteracao(String token, String roomId, String nome,
                                            String capacidade, String local, String recursos,
                                            String estado, String horarios) {
        JsonObject req = Canal.requisicao("update_room");
        req.addProperty("token", token);
        req.addProperty("room_id", roomId);
        req.addProperty("name", nome.trim());
        req.addProperty("capacity", capacidade.trim());
        req.addProperty("location", local.trim());
        // Regra 9.7 (v2.9): [] passou a significar "nao alterar", entao o campo
        // em branco nao apaga mais os recursos -- quem apaga e ["0"]. O campo
        // vem preenchido com os recursos atuais da sala, entao apaga-lo e um
        // pedido deliberado, e e esse pedido que mandamos.
        JsonArray lista = recursosDeTexto(recursos);
        if (lista.size() == 0) {
            lista.add(protocolo.Validador.APAGAR_RECURSOS);
        }
        req.add("resources", lista);
        req.addProperty("room_status", estado);
        // Regra 8.7: lista vazia mantem os horarios atuais, entao o campo em
        // branco nao apaga nada -- ao contrario do 'resources'.
        req.add("slots", slotsDeTexto(horarios));
        return req;
    }

    /**
     * O campo de horarios, de "2m1, 3T1" para ["2M1","3T1"].
     *
     * Maiusculiza porque o codigo da grade e maiusculo e ninguem deveria ser
     * punido por digitar "2m1" -- o servidor recusaria com 400, e o erro seria
     * do teclado, nao da intencao.
     */
    static JsonArray slotsDeTexto(String texto) {
        JsonArray lista = new JsonArray();
        for (String parte : texto.split(",")) {
            String limpo = parte.trim().toUpperCase();
            if (!limpo.isEmpty()) {
                lista.add(limpo);
            }
        }
        return lista;
    }

    /** O campo de recursos, de "projetor, tv" para ["projetor","tv"]. */
    static JsonArray recursosDeTexto(String texto) {
        JsonArray lista = new JsonArray();
        for (String parte : texto.split(",")) {
            String limpo = parte.trim();
            // Entrada vazia, ou "tv,,projetor" digitado as pressas, nao vira
            // um recurso de nome vazio.
            if (!limpo.isEmpty()) {
                lista.add(limpo);
            }
        }
        return lista;
    }

    /** Um array de strings da resposta, de volta para "a, b, c". */
    static String listaParaTexto(JsonObject objeto, String chave) {
        if (!objeto.has(chave) || !objeto.get(chave).isJsonArray()) {
            return "";
        }
        List<String> itens = new ArrayList<>();
        for (JsonElement elemento : objeto.getAsJsonArray(chave)) {
            itens.add(elemento.getAsString());
        }
        return String.join(", ", itens);
    }

    /** O array 'resources' de uma resposta, de volta para "projetor, tv". */
    static String recursosParaTexto(JsonObject objeto) {
        return listaParaTexto(objeto, "resources");
    }

    /**
     * O 'available' da resposta, em palavra.
     *
     * O travessao e para a ausencia do campo: sem o trio de horario o servidor
     * omite 'available', e "—" diz "nao perguntamos" em vez de insinuar que a
     * sala esta indisponivel.
     *
     * "Indisponivel", e nao "ocupada": o protocolo manda um booleano, sem
     * dizer o motivo, e os motivos sao tres -- sala inactive, horario fora do
     * funcionamento dela (regra 8.4) e reserva de alguem. So o terceiro e
     * "ocupada". A agenda do read_room e quem separa os casos, mostrando quem
     * reservou quando existe reserva.
     */
    static String disponibilidade(String available) {
        return disponibilidade(available, false);
    }

    /**
     * @param diaPassado true quando o filtro perguntou sobre um horário que
     *                   já passou: aí a resposta é sobre o que FOI, e não
     *                   sobre o que dá para reservar agora (regras 9.3 e 9.4)
     */
    static String disponibilidade(String available, boolean diaPassado) {
        if (available == null) {
            return "—";
        }
        boolean livre = "true".equals(available);
        if (diaPassado) {
            return livre ? "estava livre" : "estava indisponível";
        }
        return livre ? "livre" : "indisponível";
    }

    private static void acrescentarSeInformado(JsonObject req, String chave, String valor) {
        String limpo = valor.trim();
        if (!limpo.isEmpty()) {
            req.addProperty(chave, limpo);
        }
    }

    private static String campo(JsonObject o, String chave) {
        return o.has(chave) && !o.get(chave).isJsonNull() ? o.get(chave).getAsString() : null;
    }

    private static String texto(Object valor) {
        return valor == null ? "" : valor.toString();
    }
}
