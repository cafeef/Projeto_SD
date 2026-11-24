package cliente;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import ui.Estilo;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Dimension;

/**
 * Aba de reservas: consultar disponibilidade, reservar, listar e cancelar.
 *
 * Cobre os itens b, c, d e f da EP-3. O item e (alterar reserva) nao esta
 * aqui: a planilha pede que a alteracao mude tambem a sala e o dia, e a regra
 * 9.10 do protocolo proibe as duas chaves no update_reservation. Enquanto a
 * turma nao decide, um botao de alterar so poderia obedecer a um dos dois.
 *
 * A sala e identificada pelo ID, e nao por um combo de nomes, de proposito: o
 * ID e o que o protocolo manda no 'room_id', e a aba Salas ao lado mostra a
 * tabela inteira com os IDs. Um combo exigiria uma listagem a mais a cada vez
 * que esta aba abrisse, e ficaria desatualizado assim que um administrador
 * cadastrasse uma sala.
 */
final class PainelReservas extends JPanel {

    private static final long serialVersionUID = 1L;

    private final transient Canal canal;

    // --- consulta de disponibilidade (item b) ---
    private final JTextField dispSala = estreito(new JTextField(5));
    private final JTextField dispData = estreito(new JTextField(10));
    private final JTextField dispInicio = estreito(new JTextField(5));
    private final JTextField dispFim = estreito(new JTextField(5));
    private final JButton botaoVerificar = new JButton("Verificar disponibilidade");

    // --- minhas reservas (item c) ---
    private final JTable tabela = Estilo.tabela("ID", "Sala", "Nome da sala", "Data",
            "Início", "Fim", "Assunto", "Participantes", "Quem reservou");
    private final JTextField filtroData = estreito(new JTextField(10));
    private final JTextField filtroSala = estreito(new JTextField(5));
    private final JCheckBox todasAsReservas = new JCheckBox("Ver as de todos (admin)");
    private final JButton botaoListar = Estilo.botaoPrincipal("Listar reservas");
    private final JButton botaoLimparFiltros = new JButton("Limpar filtros");
    private final JButton botaoDetalhes = new JButton("Ver detalhes da reserva");
    private final JButton botaoCancelar = Estilo.botaoDestrutivo("Cancelar reserva");

    // --- nova reserva (item d) ---
    private final JPanel grupoNova = Estilo.grupo("Nova reserva");
    private final JTextField novaSala = estreito(new JTextField(5));
    private final JTextField novaData = estreito(new JTextField(10));
    private final JTextField novaInicio = estreito(new JTextField(5));
    private final JTextField novaFim = estreito(new JTextField(5));
    private final JTextField novaAssunto = estreito(new JTextField(26));
    private final JTextField novaParticipantes = estreito(new JTextField(4));
    private final JButton botaoReservar = Estilo.botaoPrincipal("Reservar sala");
    private final JButton botaoLimparForm = new JButton("Limpar formulário");

    PainelReservas(Canal canal) {
        this.canal = canal;
        montar();
        ligarAcoes();
    }

    // ----------------------------------------------------------------- layout

    private void montar() {
        setLayout(new BorderLayout(0, 12));

        JPanel disponibilidade = Estilo.grupo("Consultar disponibilidade");
        new Estilo.Grade(disponibilidade)
                .campo("Sala (ID):", linha(dispSala,
                        rotulo("Data (AAAA-MM-DD):"), dispData,
                        rotulo("Início:"), dispInicio,
                        rotulo("Fim:"), dispFim))
                .linhaCheia(linha(botaoVerificar))
                .dica("Início e fim são horários de aula da UTFPR:"
                        + " 13:00–14:40 são as duas primeiras da tarde.")
                .dica("A resposta é informativa — ela não segura a sala para você.");

        JScrollPane rolagem = new JScrollPane(tabela);
        rolagem.setPreferredSize(new Dimension(640, 170));

        JPanel filtros = linha(filtroData, rotulo("Sala (ID):"), filtroSala,
                todasAsReservas, botaoListar, botaoLimparFiltros);

        JPanel acoes = linha(botaoDetalhes, botaoCancelar);

        JPanel lista = Estilo.grupo("Minhas reservas");
        lista.setLayout(new BorderLayout(0, 8));
        JPanel cabecalho = new JPanel(new BorderLayout());
        new Estilo.Grade(cabecalho).campo("Data:", filtros);
        lista.add(cabecalho, BorderLayout.NORTH);
        lista.add(rolagem, BorderLayout.CENTER);
        lista.add(acoes, BorderLayout.SOUTH);

        JPanel botoesNova = new JPanel();
        botoesNova.setLayout(new BoxLayout(botoesNova, BoxLayout.X_AXIS));
        botoesNova.add(botaoReservar);
        botoesNova.add(Box.createHorizontalStrut(8));
        botoesNova.add(botaoLimparForm);
        botoesNova.add(Box.createHorizontalGlue());

        new Estilo.Grade(grupoNova)
                .campo("Sala (ID):", linha(novaSala,
                        rotulo("Data (AAAA-MM-DD):"), novaData,
                        rotulo("Início:"), novaInicio,
                        rotulo("Fim:"), novaFim))
                .campo("Assunto:", linha(novaAssunto,
                        rotulo("Participantes:"), novaParticipantes))
                .linhaCheia(botoesNova)
                .dica("Assunto sem acento, até 60 caracteres.")
                .dica("Participantes não pode passar da capacidade da sala.")
                .dica("Clique numa reserva da tabela para copiá-la para cá.");

        add(disponibilidade, BorderLayout.NORTH);
        add(lista, BorderLayout.CENTER);
        add(grupoNova, BorderLayout.SOUTH);
    }

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

    /** Minimo igual ao preferido: sem isso o GridBagLayout encolhe o campo a 23px. */
    private static JTextField estreito(JTextField campo) {
        campo.setMinimumSize(campo.getPreferredSize());
        return campo;
    }

    /** O "ver as de todos" so existe para admin: o scope "all" responde 403 aos demais. */
    void aplicarPerfil() {
        todasAsReservas.setVisible(canal.ehAdmin());
        if (!canal.ehAdmin()) {
            todasAsReservas.setSelected(false);
        }
        revalidate();
        repaint();
    }

    void habilitar(boolean habilitado) {
        botaoVerificar.setEnabled(habilitado);
        botaoListar.setEnabled(habilitado);
        botaoDetalhes.setEnabled(habilitado);
        botaoCancelar.setEnabled(habilitado);
        botaoReservar.setEnabled(habilitado);
    }

    void limpar() {
        Estilo.modeloDe(tabela).setRowCount(0);
        limparFormulario();
    }

    // ------------------------------------------------------------------ ações

    private void ligarAcoes() {
        botaoVerificar.addActionListener(e -> verificar());
        botaoListar.addActionListener(e -> listar(true));
        botaoLimparFiltros.addActionListener(e -> {
            filtroData.setText("");
            filtroSala.setText("");
            listar(false);
        });
        botaoDetalhes.addActionListener(e -> verDetalhes());
        botaoCancelar.addActionListener(e -> cancelar());
        botaoReservar.addActionListener(e -> reservar());
        botaoLimparForm.addActionListener(e -> {
            tabela.clearSelection();
            limparFormulario();
        });
        tabela.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                carregarSelecionada();
            }
        });
    }

    /** Item b: pergunta se a sala está livre naquele recorte. */
    private void verificar() {
        canal.trocar(requisicaoDeDisponibilidade(canal.token(), dispSala.getText(),
                dispData.getText(), dispInicio.getText(), dispFim.getText()), resposta -> {
            if (!"200".equals(campo(resposta, "status"))) {
                canal.informar(resposta);
                return;
            }
            JOptionPane.showMessageDialog(this, textoDaDisponibilidade(resposta),
                    "Disponibilidade da sala", JOptionPane.INFORMATION_MESSAGE);
        });
    }

    /** Item c: as reservas do usuário (ou de todos, para o admin). */
    private void listar(boolean avisarSeVazio) {
        canal.trocar(requisicaoDeListagem(canal.token(), todasAsReservas.isSelected(),
                filtroData.getText(), filtroSala.getText()), resposta -> {
            if (!"200".equals(campo(resposta, "status"))) {
                canal.informar(resposta);
                return;
            }
            preencherTabela(resposta, avisarSeVazio);
        });
    }

    /** Item d: cria a reserva com assunto e número de participantes. */
    private void reservar() {
        canal.trocar(requisicaoDeReserva(canal.token(), novaSala.getText(), novaData.getText(),
                novaInicio.getText(), novaFim.getText(), novaAssunto.getText(),
                novaParticipantes.getText()), resposta -> {
            canal.informar(resposta);
            if ("201".equals(campo(resposta, "status"))) {
                limparFormulario();
                listar(false);
            }
        });
    }

    private void verDetalhes() {
        String id = idSelecionado();
        if (id == null) {
            return;
        }
        canal.trocar(requisicaoDeLeitura(canal.token(), id), resposta -> {
            if (!"200".equals(campo(resposta, "status"))) {
                canal.informar(resposta);
                return;
            }
            JOptionPane.showMessageDialog(this, textoDaReserva(resposta),
                    "Detalhes da reserva", JOptionPane.INFORMATION_MESSAGE);
        });
    }

    /** Item f: cancela a reserva selecionada, sempre com confirmação. */
    private void cancelar() {
        String id = idSelecionado();
        if (id == null) {
            return;
        }
        int escolha = JOptionPane.showConfirmDialog(this,
                "Cancelar a reserva " + id + "? A sala volta a ficar livre nesse horário.",
                "Cancelar reserva", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (escolha != JOptionPane.YES_OPTION) {
            return;
        }
        canal.trocar(requisicaoDeCancelamento(canal.token(), id), resposta -> {
            canal.informar(resposta);
            if ("200".equals(campo(resposta, "status"))) {
                listar(false);
            }
        });
    }

    /**
     * O id da linha selecionada, ou null -- avisando quem esqueceu de escolher.
     *
     * Sem o aviso, o botao pareceria quebrado: o usuario clicaria e nada
     * aconteceria, sem explicacao nenhuma.
     */
    private String idSelecionado() {
        int linha = tabela.getSelectedRow();
        if (linha < 0) {
            JOptionPane.showMessageDialog(this,
                    "Selecione uma reserva na tabela primeiro.",
                    "Nenhuma reserva selecionada", JOptionPane.WARNING_MESSAGE);
            return null;
        }
        return texto(Estilo.modeloDe(tabela).getValueAt(linha, 0));
    }

    /** Copia a reserva selecionada para o formulário, como ponto de partida. */
    private void carregarSelecionada() {
        int linha = tabela.getSelectedRow();
        if (linha < 0) {
            return;
        }
        var modelo = Estilo.modeloDe(tabela);
        novaSala.setText(texto(modelo.getValueAt(linha, 1)));
        novaData.setText(texto(modelo.getValueAt(linha, 3)));
        novaInicio.setText(texto(modelo.getValueAt(linha, 4)));
        novaFim.setText(texto(modelo.getValueAt(linha, 5)));
        novaAssunto.setText(texto(modelo.getValueAt(linha, 6)));
        novaParticipantes.setText(texto(modelo.getValueAt(linha, 7)));
    }

    private void limparFormulario() {
        novaSala.setText("");
        novaData.setText("");
        novaInicio.setText("");
        novaFim.setText("");
        novaAssunto.setText("");
        novaParticipantes.setText("");
    }

    private void preencherTabela(JsonObject resposta, boolean avisarSeVazio) {
        var modelo = Estilo.modeloDe(tabela);
        modelo.setRowCount(0);
        if (resposta.has("reservations") && resposta.get("reservations").isJsonArray()) {
            for (JsonElement e : resposta.getAsJsonArray("reservations")) {
                JsonObject r = e.getAsJsonObject();
                modelo.addRow(new Object[] {
                        campo(r, "reservation_id"), campo(r, "room_id"), campo(r, "name"),
                        campo(r, "date"), campo(r, "start_time"), campo(r, "end_time"),
                        campo(r, "topic"), campo(r, "participants"), campo(r, "user")});
            }
        }
        if (modelo.getRowCount() == 0 && avisarSeVazio) {
            JOptionPane.showMessageDialog(this,
                    "Nenhuma reserva encontrada com esses filtros.",
                    "Consulta de reservas", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    // ------------------------------------------------- montagem das requisições

    static JsonObject requisicaoDeDisponibilidade(String token, String sala, String data,
                                                  String inicio, String fim) {
        JsonObject req = Canal.requisicao("check_availability");
        req.addProperty("token", token);
        req.addProperty("room_id", sala.trim());
        req.addProperty("date", data.trim());
        req.addProperty("start_time", inicio.trim());
        req.addProperty("end_time", fim.trim());
        return req;
    }

    static JsonObject requisicaoDeReserva(String token, String sala, String data, String inicio,
                                          String fim, String assunto, String participantes) {
        JsonObject req = Canal.requisicao("create_reservation");
        req.addProperty("token", token);
        req.addProperty("room_id", sala.trim());
        req.addProperty("date", data.trim());
        req.addProperty("start_time", inicio.trim());
        req.addProperty("end_time", fim.trim());
        req.addProperty("topic", assunto.trim());
        req.addProperty("participants", participantes.trim());
        return req;
    }

    /**
     * Monta o list_reservations.
     *
     * Regra 2.14: o filtro que o usuario nao preencheu e OMITIDO, e nao
     * enviado vazio -- "" responderia 400. O 'scope' so viaja quando e "all",
     * porque "mine" ja e o padrao do protocolo.
     */
    static JsonObject requisicaoDeListagem(String token, boolean todas, String data,
                                           String sala) {
        JsonObject req = Canal.requisicao("list_reservations");
        req.addProperty("token", token);
        if (todas) {
            req.addProperty("scope", "all");
        }
        acrescentarSeInformado(req, "date", data);
        acrescentarSeInformado(req, "room_id", sala);
        return req;
    }

    static JsonObject requisicaoDeLeitura(String token, String reservationId) {
        JsonObject req = Canal.requisicao("read_reservation");
        req.addProperty("token", token);
        req.addProperty("reservation_id", reservationId.trim());
        return req;
    }

    static JsonObject requisicaoDeCancelamento(String token, String reservationId) {
        JsonObject req = Canal.requisicao("delete_reservation");
        req.addProperty("token", token);
        req.addProperty("reservation_id", reservationId.trim());
        return req;
    }

    // ------------------------------------------------------ texto das respostas

    /** A resposta do check_availability em uma frase, com o dia por extenso. */
    static String textoDaDisponibilidade(JsonObject resposta) {
        String quando = campo(resposta, "date") + ", das " + campo(resposta, "start_time")
                + " às " + campo(resposta, "end_time");
        return "true".equals(campo(resposta, "available"))
                ? "A sala " + campo(resposta, "room_id") + " está LIVRE em " + quando
                        + ".\n\nA consulta não reserva a sala — outra pessoa pode"
                        + " reservá-la antes de você."
                : "A sala " + campo(resposta, "room_id") + " NÃO está disponível em "
                        + quando + ".\n\nPode ser reserva de outra pessoa, horário fora do"
                        + " funcionamento da sala, ou sala inativa.";
    }

    /** O read_reservation em texto, para a caixa de detalhes. */
    static String textoDaReserva(JsonObject resposta) {
        StringBuilder sb = new StringBuilder()
                .append("Reserva ").append(campo(resposta, "reservation_id")).append('\n')
                .append("Sala: ").append(campo(resposta, "name"))
                .append(" (ID ").append(campo(resposta, "room_id")).append(")\n")
                .append("Dia: ").append(campo(resposta, "date")).append('\n')
                .append("Horário: ").append(campo(resposta, "start_time"))
                .append(" às ").append(campo(resposta, "end_time")).append('\n')
                .append("Assunto: ").append(campo(resposta, "topic")).append('\n')
                .append("Participantes: ").append(campo(resposta, "participants")).append('\n')
                .append("Reservada por: ").append(campo(resposta, "user"));
        String criadaEm = campo(resposta, "created_at");
        if (criadaEm != null) {
            sb.append("\nCriada em: ").append(criadaEm);
        }
        return sb.toString();
    }

    private static void acrescentarSeInformado(JsonObject req, String chave, String valor) {
        String limpo = valor.trim();
        if (!limpo.isEmpty()) {
            req.addProperty(chave, limpo);
        }
    }

    private static String campo(JsonObject o, String chave) {
        return o != null && o.has(chave) && !o.get(chave).isJsonNull()
                ? o.get(chave).getAsString()
                : null;
    }

    private static String texto(Object valor) {
        return valor == null ? "" : valor.toString();
    }
}
