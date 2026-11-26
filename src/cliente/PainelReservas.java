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
 * Cobre os itens b, c, d, e e f da EP-3.
 *
 * O mesmo formulario cria e altera. Clicar numa reserva da tabela copia os
 * valores dela para os campos; dai "Reservar sala" cria uma nova e "Salvar
 * alteracoes" muda a selecionada. Dois formularios iguais lado a lado seriam
 * mais codigo para dizer a mesma coisa, e o campo que o usuario acabou de
 * preencher num deles estaria vazio no outro.
 *
 * Campo apagado quer dizer "nao alterar" (regra 2.11), e nao "apagar": o
 * formulario chega preenchido com a reserva, entao limpar um campo e o jeito
 * de dizer "esse aqui, deixa como esta".
 *
 * Trocar de sala ou de dia so virou possivel na v2.10 do protocolo (regra
 * 9.10). Ate a v2.9.2 as duas chaves respondiam 400, e a troca era cancelar e
 * criar outra reserva -- o que perdia o id e podia deixar a pessoa sem reserva
 * nenhuma se a sala nova ja tivesse dono naquele horario.
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
    private final ComboDeHorario dispInicio = ComboDeHorario.inicio(ComboDeHorario.NAO_INFORMADO);
    private final ComboDeHorario dispFim = ComboDeHorario.fim(ComboDeHorario.NAO_INFORMADO);
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
    private final JPanel grupoNova = Estilo.grupo("Nova reserva ou alteração");
    private final JTextField novaSala = estreito(new JTextField(5));
    private final JTextField novaData = estreito(new JTextField(10));
    private final ComboDeHorario novaInicio = ComboDeHorario.inicio(ComboDeHorario.NAO_INFORMADO);
    private final ComboDeHorario novaFim = ComboDeHorario.fim(ComboDeHorario.NAO_INFORMADO);
    private final JTextField novaAssunto = estreito(new JTextField(26));
    private final JTextField novaParticipantes = estreito(new JTextField(4));
    private final JButton botaoReservar = Estilo.botaoPrincipal("Reservar sala");
    private final JButton botaoSalvar = new JButton("Salvar alterações na reserva");
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
                .dica("As listas trazem só começos e fins de aula da UTFPR;"
                        + " o código da aula vem entre parênteses.")
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
        botoesNova.add(botaoSalvar);
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
                .dica("Clique numa reserva da tabela para copiá-la para cá e alterá-la.")
                .dica("Ao alterar, campo vazio ou \"(não informado)\" mantém o que está lá.")
                .dica("Adiantar o fim libera o resto do horário sem cancelar a reserva.");

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
        botaoSalvar.setEnabled(habilitado);
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
        botaoSalvar.addActionListener(e -> salvar());
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
        // O check_availability exige sala, dia E horário (os seis campos são
        // required no protocolo): ele responde "esta sala está livre NESTE
        // recorte?". Sem isso o servidor responde "Dados em formato
        // invalido", que não diz o que falta nem onde está o que a pessoa
        // queria -- o dia inteiro de todas as salas fica na aba Salas, em
        // "Ver o dia de todas as salas" (regra 9.16).
        if (dispSala.getText().isBlank() || dispData.getText().isBlank()
                || dispInicio.hora().isEmpty() || dispFim.hora().isEmpty()) {
            canal.erro("Para consultar a disponibilidade, informe a sala, a data,"
                    + " o início e o fim.\n\nPara ver um dia inteiro sem escolher sala"
                    + " nem horário, use a aba Salas:\n\"Ver o dia de todas as salas\".");
            return;
        }
        canal.trocar(requisicaoDeDisponibilidade(canal.token(), dispSala.getText(),
                dispData.getText(), dispInicio.hora(), dispFim.hora()), resposta -> {
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
        // O protocolo responde 400 "Dados da reserva em formato invalido" a
        // qualquer problema de formato, sem dizer qual. Para o horario que ja
        // passou -- de longe o erro mais comum -- a tela diz antes, com todas
        // as letras, em vez de deixar a pessoa adivinhar o que esta errado.
        // Ao CRIAR, o vazio nao tem o sentido de "manter" que tem ao alterar:
        // nao ha horario anterior. Dizer isso aqui e melhor do que mandar ""
        // e receber de volta "Dados da reserva em formato invalido".
        if (novaInicio.hora().isEmpty() || novaFim.hora().isEmpty()) {
            canal.erro("Escolha o horário de início e o de fim da reserva.");
            return;
        }
        String passado = avisoDePassado(novaData.getText(), novaInicio.hora());
        if (passado != null) {
            canal.erro(passado);
            return;
        }
        canal.trocar(requisicaoDeReserva(canal.token(), novaSala.getText(), novaData.getText(),
                novaInicio.hora(), novaFim.hora(), novaAssunto.getText(),
                novaParticipantes.getText()), resposta -> {
            canal.informar(resposta);
            if ("201".equals(campo(resposta, "status"))) {
                limparFormulario();
                listar(false);
            }
        });
    }

    /**
     * Item e: altera a reserva selecionada com o que esta no formulario.
     *
     * Trocar de sala, de dia, de horario, de assunto ou de numero de
     * participantes -- tudo numa requisicao so (regra 9.10). Adiantar o fim e
     * o "UC libera a sala" da planilha: a sala volta a ficar livre no resto do
     * horario sem a reserva ser cancelada.
     */
    private void salvar() {
        String id = idSelecionado();
        if (id == null) {
            return;
        }
        String passado = avisoDePassado(novaData.getText(), novaInicio.hora());
        if (passado != null) {
            canal.erro(passado);
            return;
        }
        canal.trocar(requisicaoDeAlteracao(canal.token(), id, novaSala.getText(),
                novaData.getText(), novaInicio.hora(), novaFim.hora(),
                novaAssunto.getText(), novaParticipantes.getText()), resposta -> {
            canal.informar(resposta);
            if ("200".equals(campo(resposta, "status"))) {
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
        // O início antes do fim: a lista de fins só mostra o que vem depois
        // do início escolhido, então ela precisa saber do início primeiro.
        novaInicio.selecionarHora(texto(modelo.getValueAt(linha, 4)));
        novaFim.selecionarHora(texto(modelo.getValueAt(linha, 5)));
        novaAssunto.setText(texto(modelo.getValueAt(linha, 6)));
        novaParticipantes.setText(texto(modelo.getValueAt(linha, 7)));
    }

    private void limparFormulario() {
        novaSala.setText("");
        novaData.setText("");
        novaInicio.selecionarHora("");
        novaFim.selecionarHora("");
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

    /**
     * Diz, em português, que o horário escolhido já passou -- ou null quando
     * ele ainda não passou, ou quando não dá para saber.
     *
     * Existe por causa da regra 9.3: reservar o que já começou responde 400
     * "Dados da reserva em formato invalido", a mesma mensagem de um campo
     * mal preenchido. O protocolo tem UMA mensagem de formato por operação, e
     * trocá-la quebraria a interoperabilidade -- então quem explica melhor é o
     * cliente, que sabe o que a pessoa acabou de digitar.
     *
     * Data inválida devolve null de propósito: aí o erro é outro, e quem tem
     * de respondê-lo é o servidor, com as regras dele.
     */
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
     * Monta o update_reservation.
     *
     * Todas as seis chaves alteraveis viajam, vazias quando o usuario apagou o
     * campo -- "" e a forma de dizer "nao alterar" (regra 2.11). O 'room_id' e
     * o 'date' sao opcionais no protocolo, mas manda-los vazios diz a mesma
     * coisa que omiti-los e deixa a requisicao com um formato so, mais facil
     * de conferir no terminal durante a avaliacao.
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
        String data = campo(resposta, "date");
        String inicio = campo(resposta, "start_time");
        String quando = data + ", das " + inicio + " às " + campo(resposta, "end_time");
        boolean livre = "true".equals(campo(resposta, "available"));

        // Consultar o passado é válido (regra 9.4) e o servidor responde de
        // verdade -- mas no passado o verbo é outro: a sala ESTAVA livre.
        // Dizer "está" sobre ontem convidaria a reservar o que não dá.
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
            // O aviso vem antes do resto: é o que muda o que fazer agora.
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
