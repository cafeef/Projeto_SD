package cliente;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import protocolo.Horarios;
import ui.Estilo;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Component;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * A aba Salas: o dia escolhido, sala por sala, horário por horário.
 *
 * Responde a pergunta que se faz de verdade -- "quero reservar no dia 30; o
 * que tem livre?" -- e não "me dê todas as salas com um rótulo ao lado".
 * Usa o `read_room` SEM `room_id` (regra 9.16), que traz o dia inteiro de
 * todas as salas numa requisição só. Foi para isso que a regra existe.
 *
 * Por que não o `list_rooms`: ele exige o trio data+início+fim (regra 9.11),
 * ou seja, exige saber o horário ANTES de perguntar -- e o horário é
 * justamente o que se quer descobrir. Ele continua aqui, no botão "Ver todas
 * as salas cadastradas", que é outra pergunta: o catálogo, sem dia nenhum.
 *
 * O administrador cadastra e altera salas na aba "Administrar salas". Separar
 * foi deliberado: antes consulta, tabela e formulário dividiam a mesma aba, e
 * nenhum dos três tinha espaço.
 */
final class PainelSalas extends JPanel {

    private static final long serialVersionUID = 1L;

    private final transient Canal canal;

    private final JTextField campoDia = estreito(new JTextField(11));
    private final JCheckBox apenasLivres = new JCheckBox("Só salas com horário livre", true);
    private final JButton botaoVerDia = Estilo.botaoPrincipal("Ver o dia");
    private final JButton botaoHoje = new JButton("Hoje");
    private final JButton botaoCatalogo = new JButton("Ver todas as salas cadastradas");

    private final transient GradeDoDia grade = new GradeDoDia(g -> atualizarEscolha());
    private final JLabel resumo = new JLabel();
    private final JButton botaoReservar = Estilo.botaoPrincipal("Reservar este horário");
    private final JButton botaoDetalhes = new JButton("Ver detalhes da sala");
    private final JButton botaoConferir = new JButton("Conferir disponibilidade");

    /** O último dia consultado, para as ações saberem de que dia falam. */
    private transient String diaMostrado = "";

    PainelSalas(Canal canal) {
        this.canal = canal;
        campoDia.setText(LocalDate.now().toString());
        montar();
        ligarAcoes();
        atualizarEscolha();
    }

    private void montar() {
        setLayout(new BorderLayout(0, 10));

        JPanel consulta = Estilo.grupo("Que salas estão livres?");
        consulta.setLayout(new BoxLayout(consulta, BoxLayout.Y_AXIS));
        JPanel linhaDoDia = linha(new JLabel("Dia (AAAA-MM-DD):"), campoDia,
                botaoVerDia, botaoHoje, apenasLivres, Box.createHorizontalGlue());
        linhaDoDia.setAlignmentX(Component.LEFT_ALIGNMENT);
        consulta.add(linhaDoDia);
        consulta.add(Box.createVerticalStrut(6));
        JPanel linhaDoCatalogo = linha(botaoCatalogo);
        linhaDoCatalogo.setAlignmentX(Component.LEFT_ALIGNMENT);
        consulta.add(linhaDoCatalogo);

        JScrollPane rolagem = new JScrollPane(grade);
        rolagem.setBorder(javax.swing.BorderFactory.createEmptyBorder());
        rolagem.getVerticalScrollBar().setUnitIncrement(18);
        rolagem.getHorizontalScrollBar().setUnitIncrement(18);

        resumo.setForeground(Estilo.CINZA);
        JPanel acoes = linha(resumo, Box.createHorizontalGlue(),
                botaoDetalhes, botaoConferir, botaoReservar);

        add(consulta, BorderLayout.NORTH);
        add(rolagem, BorderLayout.CENTER);
        add(acoes, BorderLayout.SOUTH);
    }

    /**
     * Trava a largura do campo na que ele pede.
     *
     * Numa linha de BoxLayout, um JTextField cresce até o fim da janela se
     * deixarem -- e um campo de data com 600 pixels de largura não ajuda
     * ninguém a digitar dez caracteres.
     */
    private static JTextField estreito(JTextField campo) {
        campo.setMaximumSize(campo.getPreferredSize());
        campo.setMinimumSize(campo.getPreferredSize());
        return campo;
    }

    private static JPanel linha(Component... componentes) {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.X_AXIS));
        for (int i = 0; i < componentes.length; i++) {
            if (i > 0) {
                p.add(Box.createHorizontalStrut(8));
            }
            p.add(componentes[i]);
        }
        return p;
    }

    private void ligarAcoes() {
        botaoVerDia.addActionListener(e -> verDia());
        botaoHoje.addActionListener(e -> {
            campoDia.setText(LocalDate.now().toString());
            verDia();
        });
        botaoCatalogo.addActionListener(e -> verCatalogo());
        botaoReservar.addActionListener(e -> reservarEscolhido());
        botaoDetalhes.addActionListener(e -> verDetalhes());
        botaoConferir.addActionListener(e -> conferir());
        apenasLivres.addActionListener(e -> {
            if (!diaMostrado.isEmpty()) {
                verDia();
            }
        });
    }

    void aplicarPerfil() {
        // Nada muda com o perfil: a consulta do dia é de todo mundo. A
        // administração mora noutra aba, que o perfil decide se existe.
    }

    void habilitar(boolean habilitado) {
        botaoVerDia.setEnabled(habilitado);
        botaoHoje.setEnabled(habilitado);
        botaoCatalogo.setEnabled(habilitado);
        botaoDetalhes.setEnabled(habilitado);
        atualizarEscolha();
    }

    void limpar() {
        grade.mostrar(new JsonObject(), "", false);
        diaMostrado = "";
        atualizarEscolha();
    }

    /** Recarrega o dia que está na tela, depois de reservar ou cancelar. */
    void recarregar() {
        if (!diaMostrado.isEmpty()) {
            verDia();
        }
    }

    // ------------------------------------------------------------------ ações

    private void verDia() {
        String dia = campoDia.getText().trim();
        if (dia.isEmpty()) {
            canal.erro("Informe o dia que você quer ver, no formato AAAA-MM-DD.");
            return;
        }
        canal.trocar(requisicaoDoDia(canal.token(), dia), resposta -> {
            if (!"200".equals(campo(resposta, "status"))) {
                canal.informar(resposta);
                return;
            }
            diaMostrado = dia;
            grade.mostrar(resposta, dia, apenasLivres.isSelected());
        });
    }

    /** O catálogo: todas as salas cadastradas, sem dia nenhum (list_rooms). */
    private void verCatalogo() {
        canal.trocar(requisicaoDoCatalogo(canal.token()), resposta -> {
            if (!"200".equals(campo(resposta, "status"))) {
                canal.informar(resposta);
                return;
            }
            mostrarTexto("Salas cadastradas", catalogoPorExtenso(resposta));
        });
    }

    private void reservarEscolhido() {
        if (!grade.temEscolha()) {
            canal.erro("Clique num bloco livre da grade para escolher o horário.");
            return;
        }
        FormularioDeReserva form = new FormularioDeReserva(canal);
        form.preencher(grade.salaEscolhida(), diaMostrado,
                grade.inicioEscolhido(), grade.fimEscolhido(), "", "");
        if (!form.mostrar(this, "Reservar " + grade.nomeDaSalaEscolhida(), false)) {
            return;
        }
        String impedimento = PainelReservas.impedimento(form.data(), form.inicio(),
                form.fim(), true);
        if (impedimento != null) {
            canal.erro(impedimento);
            return;
        }
        canal.trocar(PainelReservas.requisicaoDeReserva(canal.token(), form.sala(), form.data(),
                form.inicio(), form.fim(), form.assunto(), form.participantes()), resposta -> {
            canal.informar(resposta);
            if ("201".equals(campo(resposta, "status"))) {
                grade.desmarcar();
                verDia();
            }
        });
    }

    private void verDetalhes() {
        String sala = grade.salaEscolhida();
        if (sala == null) {
            canal.erro("Clique num bloco da grade para escolher a sala.");
            return;
        }
        JsonObject req = Canal.requisicao("read_room");
        req.addProperty("token", canal.token());
        req.addProperty("room_id", sala);
        if (!diaMostrado.isEmpty()) {
            req.addProperty("date", diaMostrado);
        }
        canal.trocar(req, resposta -> {
            if (!"200".equals(campo(resposta, "status"))) {
                canal.informar(resposta);
                return;
            }
            mostrarTexto("Detalhes da sala", detalhesPorExtenso(resposta));
        });
    }

    /**
     * Item b/j da EP-3: pergunta ao servidor se aquele recorte está livre.
     *
     * A grade já mostra isso, lida do read_room -- mas ela é um retrato do
     * momento em que foi carregada. O check_availability pergunta AGORA, e é
     * a operação que o protocolo define para essa pergunta. Ainda assim é
     * informativo (regra 5.2): um "true" não segura a sala, e quem garante é
     * o create_reservation, dentro da trava.
     */
    private void conferir() {
        if (!grade.temEscolha()) {
            canal.erro("Clique num bloco da grade para escolher sala e horário.");
            return;
        }
        canal.trocar(PainelReservas.requisicaoDeDisponibilidade(canal.token(),
                grade.salaEscolhida(), diaMostrado,
                grade.inicioEscolhido(), grade.fimEscolhido()), resposta -> {
            if (!"200".equals(campo(resposta, "status"))) {
                canal.informar(resposta);
                return;
            }
            JOptionPane.showMessageDialog(this,
                    PainelReservas.textoDaDisponibilidade(resposta),
                    "Disponibilidade da sala", JOptionPane.INFORMATION_MESSAGE);
        });
    }

    private void atualizarEscolha() {
        resumo.setText(grade.resumoDaEscolha());
        botaoReservar.setEnabled(grade.temEscolha());
        botaoConferir.setEnabled(grade.temEscolha());
    }

    // ------------------------------------------------- montagem das requisições

    /**
     * O dia inteiro de todas as salas: read_room SEM 'room_id' (regra 9.16).
     *
     * A chave fica de fora de propósito -- é a ausência dela que pede todas as
     * salas. Mandá-la vazia seria 400.
     */
    static JsonObject requisicaoDoDia(String token, String dia) {
        JsonObject req = Canal.requisicao("read_room");
        req.addProperty("token", token);
        req.addProperty("date", dia.trim());
        return req;
    }

    /** O catálogo de salas, sem filtro nenhum (list_rooms). */
    static JsonObject requisicaoDoCatalogo(String token) {
        JsonObject req = Canal.requisicao("list_rooms");
        req.addProperty("token", token);
        return req;
    }

    // ------------------------------------------------------ texto das respostas

    /** As salas cadastradas em texto, uma por bloco. */
    static String catalogoPorExtenso(JsonObject resposta) {
        if (!resposta.has("rooms") || !resposta.get("rooms").isJsonArray()) {
            return "O servidor não devolveu salas.";
        }
        JsonArray salas = resposta.getAsJsonArray("rooms");
        if (salas.size() == 0) {
            return "Nenhuma sala cadastrada.";
        }
        StringBuilder texto = new StringBuilder(salas.size() + " sala(s) cadastrada(s):");
        for (JsonElement e : salas) {
            JsonObject sala = e.getAsJsonObject();
            texto.append("\n\n").append(campo(sala, "name"))
                    .append("  (ID ").append(campo(sala, "room_id")).append(')')
                    .append("\n  Capacidade: ").append(campo(sala, "capacity"))
                    .append(" lugares   Local: ").append(campo(sala, "location"))
                    .append("\n  Recursos: ").append(recursosParaTexto(sala))
                    .append("\n  Funciona em: ").append(horariosResumidos(sala))
                    .append("\n  Estado: ")
                    .append("active".equals(campo(sala, "room_status"))
                            ? "ativa" : "inativa (não aceita novas reservas)");
        }
        return texto.toString();
    }

    /** Uma sala em texto, com a agenda do dia quando a resposta trouxe uma. */
    static String detalhesPorExtenso(JsonObject resposta) {
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
                .append("Funciona em: ").append(horariosResumidos(resposta));
        return texto.append(agendaPorExtenso(resposta)).toString();
    }

    /**
     * A agenda do dia, quando a resposta trouxe uma.
     *
     * É aqui que aparece QUEM reservou e EM QUE horário -- o item (e) da
     * Entrega 2. O "(já passou)" é do cliente: a agenda do servidor não olha
     * a hora, de propósito (regra 9.15), e sem a marca ela ofereceria um
     * horário que o create_reservation recusa.
     */
    static String agendaPorExtenso(JsonObject resposta) {
        if (!resposta.has("agenda") || !resposta.get("agenda").isJsonArray()) {
            return "";
        }
        JsonArray agenda = resposta.getAsJsonArray("agenda");
        String data = campo(resposta, "date");
        if (agenda.size() == 0) {
            return "\n\nAgenda de " + data + ": a sala não funciona nesse dia.";
        }
        StringBuilder sb = new StringBuilder("\n\nAgenda de ").append(data).append(':');
        for (JsonElement e : agenda) {
            JsonObject h = e.getAsJsonObject();
            sb.append("\n  ").append(campo(h, "slot")).append("  ")
                    .append(campo(h, "start_time")).append('-').append(campo(h, "end_time"))
                    .append("  ")
                    .append("livre".equals(campo(h, "slot_status")) ? "livre" : "indisponível");
            if (Passado.jaPassou(data, campo(h, "start_time"))) {
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

    /** Os horários de funcionamento numa linha só: "terça 13:00-15:30". */
    static String horariosResumidos(JsonObject sala) {
        if (!sala.has("slots") || !sala.get("slots").isJsonArray()) {
            return "";
        }
        List<String> slots = new ArrayList<>();
        for (JsonElement e : sala.getAsJsonArray("slots")) {
            // Horário de uma grade que não é a nossa não quebra a tela: o
            // resumo o ignora, e os outros continuam aparecendo.
            if (e.isJsonPrimitive() && Horarios.ehSlot(e.getAsString())) {
                slots.add(e.getAsString());
            }
        }
        return Horarios.resumoPorExtenso(slots);
    }

    /** Os itens de uma lista do protocolo separados por vírgula. */
    static String listaParaTexto(JsonObject objeto, String chave) {
        if (objeto == null || !objeto.has(chave) || !objeto.get(chave).isJsonArray()) {
            return "";
        }
        List<String> itens = new ArrayList<>();
        for (JsonElement e : objeto.getAsJsonArray(chave)) {
            itens.add(e.getAsString());
        }
        return String.join(", ", itens);
    }

    static String recursosParaTexto(JsonObject objeto) {
        String texto = listaParaTexto(objeto, "resources");
        return texto.isEmpty() ? "(nenhum)" : texto;
    }

    /**
     * Um diálogo com rolagem, para texto que cresce com os dados.
     *
     * O showMessageDialog com uma String cresce junto e, com muitas salas,
     * passaria da altura da tela -- os botões ficariam fora do alcance.
     */
    private void mostrarTexto(String titulo, String texto) {
        javax.swing.JTextArea area = new javax.swing.JTextArea(texto, 20, 54);
        area.setEditable(false);
        area.setCaretPosition(0);
        area.setFont(new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN,
                area.getFont().getSize()));
        JOptionPane.showMessageDialog(this, new JScrollPane(area), titulo,
                JOptionPane.INFORMATION_MESSAGE);
    }

    private static String campo(JsonObject o, String chave) {
        return o != null && o.has(chave) && !o.get(chave).isJsonNull()
                ? o.get(chave).getAsString()
                : null;
    }
}
