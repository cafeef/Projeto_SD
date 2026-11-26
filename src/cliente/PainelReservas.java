package cliente;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import ui.Estilo;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Component;

/**
 * A aba Reservas: as minhas reservas, para ler, alterar e cancelar.
 *
 * Criar reserva começa na aba Salas, clicando num bloco livre da grade do
 * dia -- lá a sala, o dia e o horário já estão escolhidos, e sobra dizer o
 * assunto. O botão "Nova reserva" daqui existe para quem já sabe sala e
 * horário de cor e prefere digitar.
 *
 * Os formulários abrem em janela própria. Antes eles ficavam empilhados
 * embaixo da tabela, e com consulta, tabela e formulário na mesma aba nenhum
 * dos três tinha espaço.
 */
final class PainelReservas extends JPanel {

    private static final long serialVersionUID = 1L;

    private final transient Canal canal;

    private final JTextField filtroData = estreito(new JTextField(11));
    private final JTextField filtroSala = estreito(new JTextField(6));
    private final JCheckBox todasAsReservas = new JCheckBox("Ver as de todos (admin)");
    private final JButton botaoListar = new JButton("Atualizar lista");
    private final JButton botaoLimparFiltros = new JButton("Limpar filtros");

    private final JTable tabela = Estilo.tabela("ID", "Sala", "Nome da sala", "Data",
            "Início", "Fim", "Assunto", "Participantes", "Quem reservou");

    private final JButton botaoNova = Estilo.botaoPrincipal("Nova reserva");
    private final JButton botaoAlterar = new JButton("Alterar reserva");
    private final JButton botaoDetalhes = new JButton("Ver detalhes");
    private final JButton botaoCancelar = Estilo.botaoDestrutivo("Cancelar reserva");

    /** Avisado quando uma reserva muda, para a grade do dia se atualizar. */
    private final transient Runnable aoMudarReservas;

    PainelReservas(Canal canal, Runnable aoMudarReservas) {
        this.canal = canal;
        this.aoMudarReservas = aoMudarReservas;
        montar();
        ligarAcoes();
    }

    private void montar() {
        setLayout(new BorderLayout(0, 10));

        JPanel filtros = Estilo.grupo("Minhas reservas");
        JPanel linhaFiltros = linha(new JLabel("Dia:"), filtroData,
                new JLabel("Sala (ID):"), filtroSala,
                todasAsReservas, botaoListar, botaoLimparFiltros);
        filtros.add(linhaFiltros);

        JScrollPane rolagem = new JScrollPane(tabela);

        JPanel acoes = new JPanel();
        acoes.setLayout(new BoxLayout(acoes, BoxLayout.X_AXIS));
        acoes.add(botaoNova);
        acoes.add(Box.createHorizontalStrut(8));
        acoes.add(botaoAlterar);
        acoes.add(Box.createHorizontalStrut(8));
        acoes.add(botaoDetalhes);
        acoes.add(Box.createHorizontalGlue());
        acoes.add(botaoCancelar);

        add(filtros, BorderLayout.NORTH);
        add(rolagem, BorderLayout.CENTER);
        add(acoes, BorderLayout.SOUTH);
    }

    /** Trava a largura do campo na que ele pede -- ver a nota em PainelSalas. */
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
        p.add(Box.createHorizontalGlue());
        return p;
    }

    private void ligarAcoes() {
        botaoListar.addActionListener(e -> listar(true));
        botaoLimparFiltros.addActionListener(e -> {
            filtroData.setText("");
            filtroSala.setText("");
            listar(false);
        });
        botaoNova.addActionListener(e -> nova());
        botaoAlterar.addActionListener(e -> alterar());
        botaoDetalhes.addActionListener(e -> verDetalhes());
        botaoCancelar.addActionListener(e -> cancelar());
    }

    /** O scope "all" é de admin: pedi-lo sem perfil responde 403 (regra 9.9). */
    void aplicarPerfil() {
        todasAsReservas.setVisible(canal.ehAdmin());
        if (!canal.ehAdmin()) {
            todasAsReservas.setSelected(false);
        }
        revalidate();
        repaint();
    }

    void habilitar(boolean habilitado) {
        botaoListar.setEnabled(habilitado);
        botaoNova.setEnabled(habilitado);
        botaoAlterar.setEnabled(habilitado);
        botaoDetalhes.setEnabled(habilitado);
        botaoCancelar.setEnabled(habilitado);
    }

    void limpar() {
        Estilo.modeloDe(tabela).setRowCount(0);
    }

    // ------------------------------------------------------------------ ações

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

    /** Item d: reserva digitada, para quem já sabe sala e horário. */
    private void nova() {
        FormularioDeReserva form = new FormularioDeReserva(canal);
        if (!form.mostrar(this, "Nova reserva", false)) {
            return;
        }
        String impedimento = impedimento(form.data(), form.inicio(), form.fim(), true);
        if (impedimento != null) {
            canal.erro(impedimento);
            return;
        }
        canal.trocar(requisicaoDeReserva(canal.token(), form.sala(), form.data(),
                form.inicio(), form.fim(), form.assunto(), form.participantes()), resposta -> {
            canal.informar(resposta);
            if ("201".equals(campo(resposta, "status"))) {
                listar(false);
                avisarMudanca();
            }
        });
    }

    /**
     * Item e: alterar a reserva selecionada.
     *
     * Campo em branco mantém o valor (regra 2.11). Trocar de sala ou de dia
     * numa requisição só passou a ser possível na v2.10 do protocolo; antes
     * era cancelar e criar outra, o que perdia o id e podia deixar a pessoa
     * sem reserva nenhuma se a sala nova já tivesse dono no horário.
     */
    private void alterar() {
        int linha = tabela.getSelectedRow();
        if (linha < 0) {
            canal.erro("Selecione uma reserva na tabela para alterar.");
            return;
        }
        var modelo = Estilo.modeloDe(tabela);
        String id = texto(modelo.getValueAt(linha, 0));

        FormularioDeReserva form = new FormularioDeReserva(canal);
        form.preencher(texto(modelo.getValueAt(linha, 1)), texto(modelo.getValueAt(linha, 3)),
                texto(modelo.getValueAt(linha, 4)), texto(modelo.getValueAt(linha, 5)),
                texto(modelo.getValueAt(linha, 6)), texto(modelo.getValueAt(linha, 7)));

        if (!form.mostrar(this, "Alterar reserva " + id, true)) {
            return;
        }
        String impedimento = impedimento(form.data(), form.inicio(), form.fim());
        if (impedimento != null) {
            canal.erro(impedimento);
            return;
        }
        canal.trocar(requisicaoDeAlteracao(canal.token(), id, form.sala(), form.data(),
                form.inicio(), form.fim(), form.assunto(), form.participantes()), resposta -> {
            canal.informar(resposta);
            if ("200".equals(campo(resposta, "status"))) {
                listar(false);
                avisarMudanca();
            }
        });
    }

    private void verDetalhes() {
        int linha = tabela.getSelectedRow();
        if (linha < 0) {
            canal.erro("Selecione uma reserva na tabela.");
            return;
        }
        String id = texto(Estilo.modeloDe(tabela).getValueAt(linha, 0));
        canal.trocar(requisicaoDeLeitura(canal.token(), id), resposta -> {
            if (!"200".equals(campo(resposta, "status"))) {
                canal.informar(resposta);
                return;
            }
            JOptionPane.showMessageDialog(this, textoDaReserva(resposta),
                    "Detalhes da reserva", JOptionPane.INFORMATION_MESSAGE);
        });
    }

    /** Item f: cancelar, sempre com confirmação. */
    private void cancelar() {
        int linha = tabela.getSelectedRow();
        if (linha < 0) {
            canal.erro("Selecione uma reserva na tabela para cancelar.");
            return;
        }
        var modelo = Estilo.modeloDe(tabela);
        String id = texto(modelo.getValueAt(linha, 0));
        int escolha = JOptionPane.showConfirmDialog(this,
                "Cancelar a reserva " + id + " ("
                        + texto(modelo.getValueAt(linha, 2)) + ", "
                        + texto(modelo.getValueAt(linha, 3)) + " "
                        + texto(modelo.getValueAt(linha, 4)) + "-"
                        + texto(modelo.getValueAt(linha, 5)) + ")?\n\n"
                        + "A sala volta a ficar livre nesse horário.",
                "Cancelar reserva", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (escolha != JOptionPane.YES_OPTION) {
            return;
        }
        canal.trocar(requisicaoDeCancelamento(canal.token(), id), resposta -> {
            canal.informar(resposta);
            if ("200".equals(campo(resposta, "status"))) {
                listar(false);
                avisarMudanca();
            }
        });
    }

    private void avisarMudanca() {
        if (aoMudarReservas != null) {
            aoMudarReservas.run();
        }
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

    // ----------------------------------------------- o que a tela barra antes

    /**
     * O que impede o pedido de sair, ou null quando não há nada.
     *
     * O protocolo tem UMA mensagem de formato por operação, então o servidor
     * responde "Dados da reserva em formato invalido" tanto para um campo mal
     * preenchido quanto para o horário que já passou (regra 9.3) -- e trocar
     * esse texto quebraria a interoperabilidade. Quem pode explicar melhor é
     * o cliente, que sabe o que a pessoa acabou de escolher.
     *
     * Horário em branco só é problema ao CRIAR: ao alterar, o vazio é o "" do
     * protocolo, que mantém o que já está lá -- e aí o passado também não se
     * aplica, porque o horário nem está mudando.
     */
    static String impedimento(String data, String inicio, String fim) {
        return impedimento(data, inicio, fim, false);
    }

    /**
     * @param ehCriacao ao CRIAR, horário em branco não tem o sentido de
     *                  "manter" que tem ao alterar: não há horário anterior.
     *                  Dizer isso aqui é melhor do que mandar "" e receber de
     *                  volta "Dados da reserva em formato invalido".
     */
    static String impedimento(String data, String inicio, String fim, boolean ehCriacao) {
        if (inicio.isEmpty() ^ fim.isEmpty()) {
            return "Escolha o início e o fim juntos, ou deixe os dois como estão.";
        }
        if (inicio.isEmpty()) {
            if (!ehCriacao) {
                return null;
            }
            // Dia passado não tem bloco escolhível: todos já passaram. Dizer
            // "escolha o horário" ali seria mandar fazer o impossível.
            return Passado.diaJaPassou(data)
                    ? Passado.aviso(data, "00:00")
                    : "Escolha o horário clicando nos blocos livres.";
        }
        return Passado.aviso(data, inicio);
    }

    /** Mantido para quem só quer o aviso do passado. */
    static String avisoDePassado(String data, String inicio) {
        return Passado.aviso(data, inicio);
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
     * Monta o update_reservation com as seis chaves alteráveis.
     *
     * 'room_id' e 'date' são opcionais desde a v2.10; mandá-los vazios diz o
     * mesmo que omiti-los e deixa a requisição com um formato só, mais fácil
     * de conferir no terminal durante a avaliação.
     */
    static JsonObject requisicaoDeAlteracao(String token, String reservationId, String sala,
                                            String data, String inicio, String fim,
                                            String assunto, String participantes) {
        JsonObject req = Canal.requisicao("update_reservation");
        req.addProperty("token", token);
        req.addProperty("reservation_id", reservationId.trim());
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
     * Regra 2.14: o filtro que o usuário não preencheu é OMITIDO, e não
     * enviado vazio -- "" responderia 400. O 'scope' só viaja quando é "all",
     * porque "mine" já é o padrão do protocolo.
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

    /**
     * A resposta do check_availability em uma frase.
     *
     * Consultar o passado é válido (regra 9.4) e o servidor responde de
     * verdade -- mas no passado o verbo é outro: a sala ESTAVA livre. Dizer
     * "está" sobre ontem convidaria a reservar o que não dá (regra 9.3).
     */
    static String textoDaDisponibilidade(JsonObject resposta) {
        String data = campo(resposta, "date");
        String inicio = campo(resposta, "start_time");
        String quando = data + ", das " + inicio + " às " + campo(resposta, "end_time");
        boolean livre = "true".equals(campo(resposta, "available"));
        String aviso = Passado.naoDaParaReservar(data, inicio);

        StringBuilder texto = new StringBuilder("A sala ")
                .append(campo(resposta, "room_id"));
        if (livre) {
            texto.append(aviso == null ? " está LIVRE em " : " estava LIVRE em ");
        } else {
            texto.append(aviso == null
                    ? " NÃO está disponível em "
                    : " NÃO estava disponível em ");
        }
        texto.append(quando).append(".\n\n");

        if (aviso != null) {
            texto.append(aviso);
        } else if (livre) {
            texto.append("A consulta não reserva a sala — outra pessoa pode"
                    + " reservá-la antes de você.");
        } else {
            texto.append("Pode ser reserva de outra pessoa, horário fora do"
                    + " funcionamento da sala, ou sala inativa.");
        }
        return texto.toString();
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
