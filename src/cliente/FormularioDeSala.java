package cliente;

import ui.Estilo;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import java.awt.Component;
import java.awt.Dimension;

/**
 * Formulário de sala, numa janela à parte.
 *
 * Antes ele morava embaixo da tabela, na mesma aba. Com a consulta em cima, a
 * tabela no meio e o formulário embaixo, nada tinha espaço: a tabela ficava
 * com três linhas visíveis, o formulário espremido, e a rolagem da aba
 * brigava com a da tabela -- a roda do mouse sobre a tabela rolava a tabela,
 * e o formulário embaixo ficava inalcançável.
 *
 * Numa janela própria, o formulário tem o espaço que precisa e a aba fica com
 * uma coisa só crescendo: a tabela.
 */
final class FormularioDeSala {

    private final JTextField nome = new JTextField(22);
    private final JTextField capacidade = new JTextField(5);
    private final JTextField local = new JTextField(24);
    private final JTextField recursos = new JTextField(28);
    private final GradeDeSlots horarios = new GradeDeSlots();
    private final JComboBox<String> estado = new JComboBox<>(new String[] {"active", "inactive"});

    /** Começa vazio, para cadastrar uma sala nova. */
    FormularioDeSala() {
        // Os nomes identificam os campos dentro da janela. Servem à suíte que
        // dirige a tela por cliques: sem eles, achar "o campo do nome" no meio
        // de um diálogo dependeria da ordem em que os componentes foram
        // criados, e qualquer rearranjo do formulário quebraria o teste.
        nome.setName("salaNome");
        capacidade.setName("salaCapacidade");
        local.setName("salaLocal");
        recursos.setName("salaRecursos");
        horarios.setName("salaHorarios");
        estado.setName("salaEstado");
    }

    /** Carrega os dados de uma sala existente, para alterar. */
    void preencher(String nome, String capacidade, String local, String recursos,
                   String codigosDeHorario, String estado) {
        this.nome.setText(valor(nome));
        this.capacidade.setText(valor(capacidade));
        this.local.setText(valor(local));
        this.recursos.setText(valor(recursos));
        this.horarios.selecionar(codigosDeHorario);
        this.estado.setSelectedItem("inativa".equals(estado) || "inactive".equals(estado)
                ? "inactive" : "active");
    }

    String nome() {
        return nome.getText();
    }

    String capacidade() {
        return capacidade.getText();
    }

    String local() {
        return local.getText();
    }

    String recursos() {
        return recursos.getText();
    }

    /** Os códigos marcados, no formato que os montadores de requisição usam. */
    String horarios() {
        return horarios.texto();
    }

    String estado() {
        return (String) estado.getSelectedItem();
    }

    /**
     * Mostra a janela e diz se a pessoa confirmou.
     *
     * @param ehEdicao muda só as dicas: ao alterar, deixar algo em branco
     *                 mantém o valor atual (regra 2.11), e ao cadastrar não há
     *                 valor anterior para manter
     */
    boolean mostrar(Component pai, String titulo, boolean ehEdicao) {
        JPanel campos = new JPanel();
        new Estilo.Grade(campos)
                .campo("Nome:", linha(nome, new JLabel("Capacidade:"), capacidade))
                .campo("Local:", linha(local, new JLabel("Estado:"), estado))
                .campo("Recursos:", recursos)
                .campo("Funciona em:", rolagem(horarios));

        JPanel tudo = new JPanel();
        tudo.setLayout(new BoxLayout(tudo, BoxLayout.Y_AXIS));
        tudo.add(campos);
        tudo.add(Box.createVerticalStrut(10));
        tudo.add(dica("Clique nos blocos em que a sala funciona."
                + " O nome do dia marca a coluna inteira; a hora, a linha."));
        tudo.add(dica("Recursos separados por vírgula."
                + (ehEdicao ? " Em branco apaga todos." : "")));
        tudo.add(dica("Nome: letras, números, espaço e hífen, até 30 caracteres."));
        if (ehEdicao) {
            tudo.add(dica("Campo em branco mantém o valor atual."
                    + " Nenhum horário marcado mantém os horários de hoje."));
        }

        int escolha = JOptionPane.showConfirmDialog(pai, tudo, titulo,
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        return escolha == JOptionPane.OK_OPTION;
    }

    private static JComponent rolagem(GradeDeSlots grade) {
        JScrollPane rolagem = new JScrollPane(grade);
        rolagem.setPreferredSize(new Dimension(560, 300));
        rolagem.getVerticalScrollBar().setUnitIncrement(18);
        return rolagem;
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
}
