package cliente;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import ui.Estilo;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import java.awt.Component;
import java.awt.Dimension;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Formulário de reserva: escolhe-se a sala numa lista e o horário em blocos.
 *
 * Antes eram seis campos de digitação, e dois deles pediam coisas que a
 * pessoa não tem como saber de cor: o ID da sala e um horário que caia na
 * grade da UTFPR. Agora a sala vem de uma lista com nome e capacidade, e o
 * horário vem da agenda daquele dia naquela sala -- blocos livres, ocupados
 * (com o nome de quem reservou) e os que já passaram, como na aba Salas.
 *
 * A agenda se recarrega sozinha quando a sala ou o dia mudam. É a mesma
 * pergunta da aba Salas, só que para uma sala: `read_room` com `room_id` e
 * `date` (regra 8.5).
 *
 * Ao ALTERAR, não escolher bloco nenhum quer dizer "não mexa no horário" --
 * é o `""` do protocolo (regra 2.11).
 */
final class FormularioDeReserva {

    private final transient Canal canal;

    private final JComboBox<String> sala = new JComboBox<>();
    private final JTextField data = new JTextField(11);
    private final JButton botaoHoje = new JButton("Hoje");
    private final transient GradeDoDia agenda = new GradeDoDia(g -> { });
    private final JLabel escolhido = new JLabel();
    private final JTextField assunto = new JTextField(26);
    private final JTextField participantes = new JTextField(5);

    /** O id de cada sala, pelo texto que aparece na lista. */
    private final transient Map<String, String> idsPorRotulo = new LinkedHashMap<>();

    /** Trava os recarregamentos enquanto a lista de salas está sendo montada. */
    private boolean carregando;

    /** A sala e o horário que o formulário abriu mostrando, para o modo alterar. */
    private String salaInicial = "";
    private String inicioInicial = "";
    private String fimInicial = "";

    FormularioDeReserva(Canal canal) {
        this.canal = canal;
        // Sem o teto, o combo estica até o fim da janela.
        sala.setMaximumSize(new Dimension(320, sala.getPreferredSize().height));
        sala.setPreferredSize(new Dimension(320, sala.getPreferredSize().height));
        data.setMaximumSize(data.getPreferredSize());
        assunto.setMaximumSize(new Dimension(340, assunto.getPreferredSize().height));
        participantes.setMaximumSize(participantes.getPreferredSize());

        sala.setName("reservaSala");
        data.setName("reservaData");
        agenda.setName("reservaAgenda");
        assunto.setName("reservaAssunto");
        participantes.setName("reservaParticipantes");

        data.setText(LocalDate.now().toString());
        escolhido.setForeground(Estilo.CINZA);

        // Montar a lista dispara um evento por item; sem a trava, abrir o
        // formulário viraria uma rajada de read_room contra o servidor.
        sala.addActionListener(e -> {
            if (!carregando) {
                carregarAgenda();
            }
        });
        botaoHoje.addActionListener(e -> {
            data.setText(LocalDate.now().toString());
            carregarAgenda();
        });
        data.addActionListener(e -> carregarAgenda());
        // Sair do campo da data também recarrega: ninguém se lembra de dar
        // Enter num campo de texto, e sem isso a agenda ficaria do dia antigo.
        data.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                carregarAgenda();
            }
        });
    }

    /** Preenche a partir de um bloco escolhido na grade, ou de uma reserva. */
    void preencher(String salaId, String dia, String inicio, String fim,
                   String assunto, String participantes) {
        this.salaInicial = valor(salaId);
        this.inicioInicial = valor(inicio);
        this.fimInicial = valor(fim);
        if (!valor(dia).isEmpty()) {
            this.data.setText(dia);
        }
        this.assunto.setText(valor(assunto));
        this.participantes.setText(valor(participantes));
    }

    /** O 'room_id' escolhido na lista. */
    String sala() {
        Object rotulo = this.sala.getSelectedItem();
        String id = rotulo == null ? null : idsPorRotulo.get(rotulo.toString());
        return id == null ? salaInicial : id;
    }

    String data() {
        return data.getText();
    }

    /** O início da faixa escolhida nos blocos, ou "" para não alterar. */
    String inicio() {
        String escolhidoNaGrade = agenda.inicioEscolhido();
        return escolhidoNaGrade == null ? "" : escolhidoNaGrade;
    }

    String fim() {
        String escolhidoNaGrade = agenda.fimEscolhido();
        return escolhidoNaGrade == null ? "" : escolhidoNaGrade;
    }

    String assunto() {
        return assunto.getText();
    }

    String participantes() {
        return participantes.getText();
    }

    /**
     * @param ehEdicao ao alterar, não escolher bloco mantém o horário atual
     *                 (regra 2.11); ao criar, falta escolher
     */
    boolean mostrar(Component pai, String titulo, boolean ehEdicao) {
        carregarSalas();

        JPanel campos = new JPanel();
        // Num BoxLayout vertical, componentes com alinhamentos diferentes
        // ficam deslocados uns dos outros: o painel dos campos vem com o
        // alinhamento central padrão e os rótulos abaixo com o da esquerda,
        // e a grade aparecia empurrada para o meio da janela.
        campos.setAlignmentX(Component.LEFT_ALIGNMENT);
        new Estilo.Grade(campos)
                .campo("Sala:", linha(sala))
                .campo("Dia (AAAA-MM-DD):", linha(data, botaoHoje))
                .campo("Assunto:", assunto)
                .campo("Participantes:", participantes);

        JScrollPane rolagem = new JScrollPane(agenda);
        rolagem.setPreferredSize(new Dimension(360, 200));
        rolagem.setMaximumSize(new Dimension(360, 200));
        rolagem.getVerticalScrollBar().setUnitIncrement(18);

        JPanel tudo = new JPanel();
        tudo.setLayout(new BoxLayout(tudo, BoxLayout.Y_AXIS));
        tudo.add(campos);
        tudo.add(Box.createVerticalStrut(10));
        tudo.add(rotuloEsquerda("Horário — clique nos blocos livres:"));
        tudo.add(Box.createVerticalStrut(4));
        rolagem.setAlignmentX(Component.LEFT_ALIGNMENT);
        tudo.add(rolagem);
        tudo.add(Box.createVerticalStrut(4));
        escolhido.setAlignmentX(Component.LEFT_ALIGNMENT);
        tudo.add(escolhido);
        tudo.add(Box.createVerticalStrut(8));
        tudo.add(dica("Assunto sem acento, até 60 caracteres."));
        tudo.add(dica("Participantes não pode passar da capacidade da sala."));
        tudo.add(dica(ehEdicao
                ? "Não escolher bloco nenhum mantém o horário atual."
                : "Clique num bloco e depois em outro da mesma sala para pegar"
                        + " aulas seguidas."));

        atualizarEscolhido();
        int escolha = JOptionPane.showConfirmDialog(pai, tudo, titulo,
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        return escolha == JOptionPane.OK_OPTION;
    }

    // ---------------------------------------------------------------- dados

    /** Carrega a lista de salas e posiciona na sala inicial, se houver. */
    private void carregarSalas() {
        canal.trocar(PainelSalas.requisicaoDoCatalogo(canal.token()), resposta -> {
            if (!"200".equals(campo(resposta, "status"))) {
                canal.informar(resposta);
                return;
            }
            carregando = true;
            idsPorRotulo.clear();
            sala.removeAllItems();
            if (resposta.has("rooms") && resposta.get("rooms").isJsonArray()) {
                for (JsonElement e : resposta.getAsJsonArray("rooms")) {
                    JsonObject s = e.getAsJsonObject();
                    String rotulo = campo(s, "name") + "  ·  "
                            + campo(s, "capacity") + " lugares"
                            + ("active".equals(campo(s, "room_status")) ? "" : "  (inativa)");
                    idsPorRotulo.put(rotulo, campo(s, "room_id"));
                    sala.addItem(rotulo);
                }
            }
            selecionarSalaInicial();
            carregando = false;
            carregarAgenda();
        });
    }

    private void selecionarSalaInicial() {
        if (salaInicial.isEmpty()) {
            return;
        }
        for (Map.Entry<String, String> e : idsPorRotulo.entrySet()) {
            if (salaInicial.equals(e.getValue())) {
                sala.setSelectedItem(e.getKey());
                return;
            }
        }
    }

    /** Pede a agenda daquela sala naquele dia e redesenha os blocos. */
    private void carregarAgenda() {
        String salaId = sala();
        String dia = data.getText().trim();
        if (salaId == null || salaId.isEmpty() || dia.isEmpty()) {
            return;
        }
        JsonObject req = Canal.requisicao("read_room");
        req.addProperty("token", canal.token());
        req.addProperty("room_id", salaId);
        req.addProperty("date", dia);

        canal.trocar(req, resposta -> {
            if (!"200".equals(campo(resposta, "status"))) {
                canal.informar(resposta);
                return;
            }
            // A GradeDoDia desenha uma LISTA de salas; aqui há uma só, então a
            // resposta do read_room vira uma lista de um item. Reaproveitar a
            // mesma grade é o que faz o formulário mostrar o dia exatamente
            // como a aba Salas mostra.
            JsonObject comoLista = new JsonObject();
            JsonArray salas = new JsonArray();
            salas.add(resposta);
            comoLista.add("rooms", salas);
            agenda.mostrar(comoLista, dia, false);
            agenda.escolher(salaId, vazioParaNulo(inicioInicial), vazioParaNulo(fimInicial));
            // A escolha inicial vale uma vez: depois de carregada, quem manda
            // são os cliques.
            inicioInicial = "";
            fimInicial = "";
            atualizarEscolhido();
        });
    }

    private void atualizarEscolhido() {
        escolhido.setText(agenda.temEscolha()
                ? "Escolhido: " + agenda.inicioEscolhido() + " às " + agenda.fimEscolhido()
                : "Nenhum bloco escolhido.");
    }

    // ---------------------------------------------------------------- layout

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

    private static JLabel rotuloEsquerda(String texto) {
        JLabel l = new JLabel(texto);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    private static JLabel dica(String texto) {
        JLabel l = new JLabel(texto);
        l.setForeground(Estilo.CINZA);
        l.setFont(l.getFont().deriveFont(java.awt.Font.ITALIC, l.getFont().getSize() - 1f));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    private static String valor(String texto) {
        return texto == null ? "" : texto;
    }

    private static String vazioParaNulo(String texto) {
        return texto == null || texto.isEmpty() ? null : texto;
    }

    private static String campo(JsonObject o, String chave) {
        return o != null && o.has(chave) && !o.get(chave).isJsonNull()
                ? o.get(chave).getAsString()
                : null;
    }

    /** Os rótulos da lista de salas, na ordem. Usado pela suíte de tela. */
    List<String> salasDisponiveis() {
        return new ArrayList<>(idsPorRotulo.keySet());
    }
}
