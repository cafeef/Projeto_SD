package cliente;

import protocolo.Horarios;
import ui.Estilo;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Os horários de funcionamento de uma sala, como uma grade de blocos.
 *
 * Uma coluna por dia, uma linha por aula -- a mesma forma do quadro de
 * horários da UTFPR. Marcar "segunda e quarta à tarde" é clicar nos blocos
 * daquele retângulo, e não digitar "2T1, 2T2, 2T3, 4T1, 4T2, 4T3".
 *
 * Os cabeçalhos são clicáveis e fazem o trabalho em lote: o nome do dia marca
 * ou desmarca a coluna inteira, e o horário da linha faz o mesmo com a linha.
 * Sem isso, uma sala que funciona a semana toda num horário custaria seis
 * cliques; com isso, custa um.
 */
final class GradeDeSlots extends JPanel {

    private static final long serialVersionUID = 1L;

    private static final Color MARCADO = new Color(0x2E, 0x7D, 0x32);
    private static final Color VAZIO = Color.WHITE;
    private static final Color BORDA_VAZIO = new Color(0xDD, 0xDD, 0xDD);

    /** O botão de cada horário, por código ("3T1"). Ordenado para o texto sair estável. */
    private final transient Map<String, JToggleButton> blocos = new TreeMap<>();

    GradeDeSlots() {
        setLayout(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(1, 1, 1, 1);
        c.fill = GridBagConstraints.BOTH;

        List<Character> dias = Horarios.dias();
        List<String> aulas = Horarios.aulas();

        // Canto vazio, e depois um cabeçalho por dia.
        c.gridx = 0;
        c.gridy = 0;
        add(new JLabel(), c);
        for (int d = 0; d < dias.size(); d++) {
            char dia = dias.get(d);
            c.gridx = d + 1;
            add(cabecalho(abreviado(Horarios.nomeDoDia(dia)),
                    "Marcar ou desmarcar " + Horarios.nomeDoDia(dia) + " inteira",
                    () -> alternarDia(dia)), c);
        }

        for (int a = 0; a < aulas.size(); a++) {
            String aula = aulas.get(a);
            c.gridy = a + 1;
            c.gridx = 0;
            add(cabecalho(Horarios.inicioDe("2" + aula) + "-" + Horarios.fimDe("2" + aula),
                    "Marcar ou desmarcar este horário em todos os dias",
                    () -> alternarAula(aula)), c);

            for (int d = 0; d < dias.size(); d++) {
                String slot = "" + dias.get(d) + aula;
                JToggleButton bloco = new JToggleButton();
                bloco.setToolTipText(Horarios.porExtenso(slot) + "  (" + slot + ")");
                bloco.setMargin(new Insets(0, 0, 0, 0));
                bloco.setPreferredSize(new Dimension(58, 22));
                // Mínimo igual ao preferido: quando a janela aperta, o
                // GridBagLayout recua para os tamanhos MÍNIMOS, e o mínimo de
                // um botão sem texto é um quadradinho. A grade inteira
                // encolhia até virar pontinhos ilegíveis.
                bloco.setMinimumSize(bloco.getPreferredSize());
                bloco.setFocusPainted(false);
                // Sem estas duas linhas o tema do sistema pinta o botão e
                // ignora o setBackground: no GTK o marcado fica num cinza
                // claríssimo, quase igual ao desmarcado.
                bloco.setContentAreaFilled(false);
                bloco.setOpaque(true);
                bloco.addItemListener(e -> pintar(bloco));
                pintar(bloco);
                blocos.put(slot, bloco);
                c.gridx = d + 1;
                add(bloco, c);
            }
        }

        // Preenchedor no canto: encosta a grade em cima e à esquerda, em vez
        // de o GridBagLayout centralizá-la quando sobra espaço.
        c.gridx = dias.size() + 1;
        c.gridy = aulas.size() + 1;
        c.weightx = 1;
        c.weighty = 1;
        add(new JLabel(), c);
    }

    /**
     * Pinta um bloco conforme esteja marcado.
     *
     * A cor não é o único sinal: o marcado também ganha um "✓" e a borda mais
     * forte. Num tema que pinte os botões à sua maneira, ou para quem não
     * distingue as cores, o símbolo continua dizendo o que é.
     */
    private static void pintar(JToggleButton bloco) {
        boolean marcado = bloco.isSelected();
        bloco.setText(marcado ? "✓" : "");
        bloco.setBackground(marcado ? MARCADO : VAZIO);
        bloco.setForeground(marcado ? Color.WHITE : Color.BLACK);
        bloco.setBorder(BorderFactory.createLineBorder(marcado ? MARCADO.darker() : BORDA_VAZIO));
    }

    /** Cabeçalho clicável: faz a marcação em lote de uma linha ou coluna. */
    private static JButton cabecalho(String texto, String dica, Runnable acao) {
        JButton b = new JButton(texto);
        b.setToolTipText(dica);
        b.setMargin(new Insets(0, 4, 0, 4));
        b.setPreferredSize(new Dimension(88, 22));
        b.setMinimumSize(b.getPreferredSize());
        b.setFont(b.getFont().deriveFont(b.getFont().getSize() - 1f));
        b.setForeground(Estilo.CINZA);
        b.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        b.setContentAreaFilled(false);
        b.addActionListener(e -> acao.run());
        return b;
    }

    /** "segunda" vira "seg": a coluna fica estreita e a grade inteira cabe na janela. */
    private static String abreviado(String nomeDoDia) {
        return nomeDoDia.length() <= 3 ? nomeDoDia : nomeDoDia.substring(0, 3);
    }

    /**
     * Marca a coluna toda, ou desmarca se ela já estava inteira marcada.
     *
     * Alternar em vez de só marcar deixa o mesmo clique desfazer o engano.
     */
    private void alternarDia(char dia) {
        List<JToggleButton> coluna = new ArrayList<>();
        for (Map.Entry<String, JToggleButton> e : blocos.entrySet()) {
            if (e.getKey().charAt(0) == dia) {
                coluna.add(e.getValue());
            }
        }
        alternar(coluna);
    }

    private void alternarAula(String aula) {
        List<JToggleButton> linha = new ArrayList<>();
        for (Map.Entry<String, JToggleButton> e : blocos.entrySet()) {
            if (e.getKey().substring(1).equals(aula)) {
                linha.add(e.getValue());
            }
        }
        alternar(linha);
    }

    private static void alternar(List<JToggleButton> grupo) {
        boolean todosMarcados = grupo.stream().allMatch(JToggleButton::isSelected);
        grupo.forEach(b -> b.setSelected(!todosMarcados));
    }

    /** Os códigos marcados, na ordem da grade. */
    List<String> selecionados() {
        List<String> escolhidos = new ArrayList<>();
        for (String slot : Horarios.todosOsSlots()) {
            JToggleButton bloco = blocos.get(slot);
            if (bloco != null && bloco.isSelected()) {
                escolhidos.add(slot);
            }
        }
        return escolhidos;
    }

    /** Os marcados no formato que os montadores de requisição usam. */
    String texto() {
        return String.join(", ", selecionados());
    }

    /**
     * Marca os códigos informados e desmarca o resto.
     *
     * Código que não conhecemos é ignorado em silêncio: pode vir de um
     * servidor de outro grupo com a grade diferente, e derrubar a tela por
     * causa disso seria pior do que mostrar os horários que entendemos.
     */
    void selecionar(String textoComCodigos) {
        Set<String> alvos = new LinkedHashSet<>();
        if (textoComCodigos != null) {
            for (String parte : textoComCodigos.split(",")) {
                String limpo = parte.trim().toUpperCase();
                if (!limpo.isEmpty()) {
                    alvos.add(limpo);
                }
            }
        }
        blocos.forEach((slot, bloco) -> bloco.setSelected(alvos.contains(slot)));
    }

    void limpar() {
        blocos.values().forEach(b -> b.setSelected(false));
    }
}
