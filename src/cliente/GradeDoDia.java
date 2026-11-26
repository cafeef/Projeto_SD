package cliente;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import protocolo.Horarios;
import ui.Estilo;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * O dia de todas as salas em blocos: uma coluna por sala, uma linha por aula.
 *
 * É a leitura que a regra 9.16 passou a permitir numa requisição só -- o
 * `read_room` sem `room_id`, com `date`. Em vez de uma tabela com uma palavra
 * por sala ("livre"/"indisponível"), o dia aparece como ele é: um quadro de
 * horários, em que se enxerga de relance onde há buraco.
 *
 * Clicar num bloco livre o seleciona; clicar em outro bloco livre da MESMA
 * sala estende a seleção até ele. É assim que se pede duas aulas seguidas sem
 * digitar horário nenhum -- a seleção vira o `start_time` e o `end_time` da
 * reserva.
 *
 * Quatro estados, e cada um diz uma coisa diferente a quem olha:
 *
 *   livre ............ dá para reservar, e o clique leva à reserva
 *   ocupada .......... tem dono, e o nome dele aparece no bloco
 *   não funciona ..... a sala não abre nessa aula (regra 8.4)
 *   já passou ........ livre no servidor, mas o create_reservation recusa
 *                      (regras 9.3 e 9.15), então não aceita clique
 */
final class GradeDoDia extends JPanel {

    private static final long serialVersionUID = 1L;

    private static final Color FUNDO_LIVRE = new Color(0xE8, 0xF5, 0xE9);
    private static final Color BORDA_LIVRE = new Color(0xA5, 0xD6, 0xA7);
    private static final Color FUNDO_OCUPADA = new Color(0xFF, 0xEB, 0xEE);
    private static final Color BORDA_OCUPADA = new Color(0xEF, 0x9A, 0x9A);
    private static final Color FUNDO_MORTA = new Color(0xF5, 0xF5, 0xF5);
    private static final Color BORDA_MORTA = new Color(0xDD, 0xDD, 0xDD);
    private static final Color FUNDO_ESCOLHIDO = new Color(0x19, 0x76, 0xD2);

    /** Um bloco da grade: sala, aula e o que ele é naquele dia. */
    private static final class Bloco {
        final String salaId;
        final String nomeDaSala;
        final String slot;
        final String inicio;
        final String fim;
        final boolean livre;
        final JButton botao;
        boolean escolhido;

        Bloco(String salaId, String nomeDaSala, String slot, String inicio, String fim,
              boolean livre, JButton botao) {
            this.salaId = salaId;
            this.nomeDaSala = nomeDaSala;
            this.slot = slot;
            this.inicio = inicio;
            this.fim = fim;
            this.livre = livre;
            this.botao = botao;
        }
    }

    private final transient List<Bloco> blocos = new ArrayList<>();
    private final transient Consumer<GradeDoDia> aoMudarSelecao;

    /** @param aoMudarSelecao avisado a cada clique, para a tela atualizar os botões */
    GradeDoDia(Consumer<GradeDoDia> aoMudarSelecao) {
        this.aoMudarSelecao = aoMudarSelecao;
        setLayout(new GridBagLayout());
    }

    /**
     * Redesenha a grade com a resposta do read_room sem room_id.
     *
     * @param data   o dia consultado, para saber o que já passou
     * @param apenasComHorarioLivre esconde as salas sem nenhum bloco livre --
     *                              é o "quero ver as salas disponíveis neste
     *                              dia", sem as outras no meio
     */
    void mostrar(JsonObject resposta, String data, boolean apenasComHorarioLivre) {
        removeAll();
        blocos.clear();

        List<JsonObject> salas = salasDe(resposta, data, apenasComHorarioLivre);
        if (salas.isEmpty()) {
            add(new JLabel(apenasComHorarioLivre
                    ? "Nenhuma sala tem horário livre em " + data + "."
                    : "Nenhuma sala cadastrada."));
            revalidate();
            repaint();
            avisar();
            return;
        }

        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(1, 1, 1, 1);
        c.fill = GridBagConstraints.BOTH;

        c.gridx = 0;
        c.gridy = 0;
        add(rotulo("Horário", 92), c);
        for (int s = 0; s < salas.size(); s++) {
            JsonObject sala = salas.get(s);
            c.gridx = s + 1;
            JLabel titulo = rotulo(campo(sala, "name"), 118);
            titulo.setToolTipText("Sala " + campo(sala, "room_id") + " · "
                    + campo(sala, "capacity") + " lugares · " + campo(sala, "location"));
            add(titulo, c);
        }

        // Só as aulas em que ALGUMA sala do dia funciona: mostrar as 17 linhas
        // sempre encheria a tela de blocos cinzas sem informação nenhuma.
        List<String> aulas = aulasRelevantes(salas);
        for (int a = 0; a < aulas.size(); a++) {
            String aula = aulas.get(a);
            c.gridy = a + 1;
            c.gridx = 0;
            add(rotulo(Horarios.inicioDe("2" + aula) + "-" + Horarios.fimDe("2" + aula), 92), c);

            for (int s = 0; s < salas.size(); s++) {
                c.gridx = s + 1;
                add(blocoDe(salas.get(s), aula, data), c);
            }
        }

        // Um preenchedor no canto empurra a grade para cima e para a
        // esquerda. Sem ele o GridBagLayout centraliza tudo, e a grade fica
        // boiando no meio da aba quando sobra espaço.
        c.gridx = salas.size() + 1;
        c.gridy = aulas.size() + 1;
        c.weightx = 1;
        c.weighty = 1;
        add(new JLabel(), c);

        revalidate();
        repaint();
        avisar();
    }

    /** As salas da resposta, já filtradas e na ordem em que vieram. */
    private List<JsonObject> salasDe(JsonObject resposta, String data,
                                     boolean apenasComHorarioLivre) {
        List<JsonObject> salas = new ArrayList<>();
        if (!resposta.has("rooms") || !resposta.get("rooms").isJsonArray()) {
            return salas;
        }
        for (JsonElement e : resposta.getAsJsonArray("rooms")) {
            JsonObject sala = e.getAsJsonObject();
            if (!apenasComHorarioLivre || temHorarioLivre(sala, data)) {
                salas.add(sala);
            }
        }
        return salas;
    }

    /**
     * Diz se a sala tem pelo menos um horário reservável naquele dia.
     *
     * "Reservável" exclui o que já passou: um bloco de ontem continua "livre"
     * na resposta do servidor (regra 9.15), mas o create_reservation o recusa
     * (regra 9.3). Contar esses faria a sala aparecer na lista de disponíveis
     * sem ter nada para oferecer.
     */
    static boolean temHorarioLivre(JsonObject sala, String data) {
        for (Map.Entry<String, JsonObject> e : agendaPorAula(sala).entrySet()) {
            JsonObject horario = e.getValue();
            if ("livre".equals(campo(horario, "slot_status"))
                    && !Passado.jaPassou(data, campo(horario, "start_time"))) {
                return true;
            }
        }
        return false;
    }

    /** A agenda da sala indexada pelo código da aula ("T1"), para busca direta. */
    private static Map<String, JsonObject> agendaPorAula(JsonObject sala) {
        Map<String, JsonObject> porAula = new LinkedHashMap<>();
        if (sala.has("agenda") && sala.get("agenda").isJsonArray()) {
            for (JsonElement e : sala.getAsJsonArray("agenda")) {
                JsonObject horario = e.getAsJsonObject();
                String slot = campo(horario, "slot");
                if (slot != null && slot.length() > 1) {
                    porAula.put(slot.substring(1), horario);
                }
            }
        }
        return porAula;
    }

    /** As aulas em que alguma das salas funciona, na ordem da grade. */
    private static List<String> aulasRelevantes(List<JsonObject> salas) {
        List<String> aulas = new ArrayList<>();
        for (String aula : Horarios.aulas()) {
            for (JsonObject sala : salas) {
                if (agendaPorAula(sala).containsKey(aula)) {
                    aulas.add(aula);
                    break;
                }
            }
        }
        return aulas;
    }

    private JButton blocoDe(JsonObject sala, String aula, String data) {
        JsonObject horario = agendaPorAula(sala).get(aula);
        JButton b = new JButton();
        b.setMargin(new Insets(0, 2, 0, 2));
        b.setPreferredSize(new Dimension(118, 26));
        // Ver a nota em GradeDeSlots: sem o mínimo, a grade encolhe até
        // virar pontinhos quando a janela aperta.
        b.setMinimumSize(b.getPreferredSize());
        b.setFont(b.getFont().deriveFont(b.getFont().getSize() - 1f));
        b.setFocusPainted(false);
        // Sem estas duas linhas o GTK pinta o botão com o tema do sistema e
        // IGNORA o setBackground: a grade inteira sai branca, só com a cor na
        // borda, e o bloco escolhido fica com texto branco sobre branco --
        // ilegível. Desligando o preenchimento do Look and Feel, quem pinta o
        // fundo é o próprio componente, e aí a cor vale em qualquer tema.
        b.setContentAreaFilled(false);
        b.setOpaque(true);

        if (horario == null) {
            // A sala não abre nessa aula.
            String explicacao = campo(sala, "name") + " não funciona neste horário.";
            b.setText("—");
            b.setBackground(FUNDO_MORTA);
            b.setBorder(BorderFactory.createLineBorder(BORDA_MORTA));
            b.setForeground(Estilo.CINZA);
            b.setToolTipText(explicacao);
            b.addActionListener(e -> explicar(explicacao));
            return b;
        }

        String inicio = campo(horario, "start_time");
        String fim = campo(horario, "fim") != null ? campo(horario, "fim")
                : campo(horario, "end_time");
        boolean livreNoServidor = "livre".equals(campo(horario, "slot_status"));
        boolean passou = Passado.jaPassou(data, inicio);
        boolean reservavel = livreNoServidor && !passou;

        if (!livreNoServidor) {
            String dono = campo(horario, "user");
            String explicacao = dono == null
                    ? campo(sala, "name") + " está indisponível neste horário"
                            + " (sala inativa ou reservada)."
                    : campo(sala, "name") + " está reservada por " + dono
                            + " das " + inicio + " às " + fim + ".";
            b.setText(dono == null ? "ocupada" : dono);
            b.setToolTipText(explicacao);
            b.setBackground(FUNDO_OCUPADA);
            b.setBorder(BorderFactory.createLineBorder(BORDA_OCUPADA));
            b.addActionListener(e -> explicar(explicacao));
            return b;
        }
        if (passou) {
            String explicacao = "Este horário já passou — não é possível reservar"
                    + " (regra 9.3). Escolha um dia ou uma aula que ainda vai começar.";
            b.setText("já passou");
            b.setToolTipText(explicacao);
            b.setBackground(FUNDO_MORTA);
            b.setBorder(BorderFactory.createLineBorder(BORDA_MORTA));
            b.setForeground(Estilo.CINZA);
            b.addActionListener(e -> explicar(explicacao));
            return b;
        }

        b.setText("livre");
        b.setToolTipText("Clique para escolher " + inicio + "-" + fim
                + " em " + campo(sala, "name") + ".");
        b.setBackground(FUNDO_LIVRE);
        b.setBorder(BorderFactory.createLineBorder(BORDA_LIVRE));

        Bloco bloco = new Bloco(campo(sala, "room_id"), campo(sala, "name"),
                campo(horario, "slot"), inicio, fim, reservavel, b);
        blocos.add(bloco);
        b.addActionListener(e -> clicar(bloco));
        return b;
    }

    /**
     * Um clique escolhe o bloco; outro clique na MESMA sala estende até ele.
     *
     * Estender em vez de exigir ctrl ou shift porque reservar duas aulas
     * seguidas é o caso comum, e teclas modificadoras não se descobrem
     * sozinhas. Clicar numa sala diferente recomeça a escolha: a reserva é de
     * uma sala só, então misturar colunas nunca daria um pedido válido.
     */
    private void clicar(Bloco alvo) {
        List<Bloco> escolhidos = escolhidos();
        if (escolhidos.isEmpty() || !escolhidos.get(0).salaId.equals(alvo.salaId)) {
            limparEscolha();
            alvo.escolhido = true;
        } else if (alvo.escolhido && escolhidos.size() == 1) {
            // Clicar de novo no único escolhido desfaz a escolha.
            alvo.escolhido = false;
        } else {
            escolherFaixa(alvo);
        }
        pintar();
        avisar();
    }

    /** Marca todos os blocos livres da sala entre o primeiro escolhido e o alvo. */
    private void escolherFaixa(Bloco alvo) {
        List<Bloco> daSala = new ArrayList<>();
        for (Bloco b : blocos) {
            if (b.salaId.equals(alvo.salaId)) {
                daSala.add(b);
            }
        }
        List<Bloco> escolhidos = escolhidos();
        int inicio = daSala.indexOf(escolhidos.get(0));
        int fim = daSala.indexOf(alvo);
        limparEscolha();
        for (int i = Math.min(inicio, fim); i <= Math.max(inicio, fim); i++) {
            daSala.get(i).escolhido = true;
        }
    }

    private void limparEscolha() {
        blocos.forEach(b -> b.escolhido = false);
    }

    /**
     * Repinta os blocos livres conforme a escolha.
     *
     * A cor não é o único sinal: o texto muda junto, e a borda do escolhido
     * fica mais grossa. Num tema que pinte os botões à sua maneira, ou para
     * quem não distingue as cores, a palavra continua dizendo o que é.
     */
    private void pintar() {
        for (Bloco b : blocos) {
            b.botao.setBackground(b.escolhido ? FUNDO_ESCOLHIDO : FUNDO_LIVRE);
            b.botao.setForeground(b.escolhido ? Color.WHITE : Color.BLACK);
            b.botao.setText(b.escolhido ? "escolhido" : "livre");
            b.botao.setBorder(b.escolhido
                    ? BorderFactory.createLineBorder(FUNDO_ESCOLHIDO.darker(), 2)
                    : BorderFactory.createLineBorder(BORDA_LIVRE));
        }
    }

    private List<Bloco> escolhidos() {
        List<Bloco> lista = new ArrayList<>();
        for (Bloco b : blocos) {
            if (b.escolhido) {
                lista.add(b);
            }
        }
        return lista;
    }

    /**
     * Diz por que aquele bloco não dá para escolher.
     *
     * Antes esses blocos eram botões DESABILITADOS. Desabilitar tinha dois
     * defeitos: o GTK repinta o botão desabilitado com o tema e engole a cor
     * de fundo -- a grade saía toda branca --, e um botão cinza não explica
     * nada a quem clica nele esperando uma resposta. Agora eles respondem.
     */
    private void explicar(String texto) {
        javax.swing.JOptionPane.showMessageDialog(this, texto, "Este horário",
                javax.swing.JOptionPane.INFORMATION_MESSAGE);
    }

    private void avisar() {
        if (aoMudarSelecao != null) {
            aoMudarSelecao.accept(this);
        }
    }

    /** Limpa a escolha, depois de reservar. */
    void desmarcar() {
        limparEscolha();
        pintar();
        avisar();
    }

    boolean temEscolha() {
        return !escolhidos().isEmpty();
    }

    String salaEscolhida() {
        List<Bloco> e = escolhidos();
        return e.isEmpty() ? null : e.get(0).salaId;
    }

    String nomeDaSalaEscolhida() {
        List<Bloco> e = escolhidos();
        return e.isEmpty() ? null : e.get(0).nomeDaSala;
    }

    /** O 'start_time' da reserva: o início do primeiro bloco escolhido. */
    String inicioEscolhido() {
        List<Bloco> e = escolhidos();
        return e.isEmpty() ? null : e.get(0).inicio;
    }

    /** O 'end_time' da reserva: o fim do último bloco escolhido. */
    String fimEscolhido() {
        List<Bloco> e = escolhidos();
        return e.isEmpty() ? null : e.get(e.size() - 1).fim;
    }

    /** A escolha em uma frase, para a barra embaixo da grade. */
    String resumoDaEscolha() {
        if (!temEscolha()) {
            return "Clique num bloco livre para escolher o horário.";
        }
        return nomeDaSalaEscolhida() + " · " + inicioEscolhido() + "-" + fimEscolhido();
    }

    private static JLabel rotulo(String texto, int largura) {
        JLabel l = new JLabel(texto == null ? "" : texto);
        l.setPreferredSize(new Dimension(largura, 24));
        l.setMinimumSize(l.getPreferredSize());
        l.setFont(l.getFont().deriveFont(java.awt.Font.BOLD, l.getFont().getSize() - 1f));
        l.setForeground(Estilo.CINZA);
        return l;
    }

    private static String campo(JsonObject o, String chave) {
        return o != null && o.has(chave) && !o.get(chave).isJsonNull()
                ? o.get(chave).getAsString()
                : null;
    }
}
