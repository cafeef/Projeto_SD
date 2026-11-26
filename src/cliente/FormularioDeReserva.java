package cliente;

import ui.Estilo;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.Component;

/**
 * Formulário de reserva, numa janela à parte.
 *
 * Chega de dois jeitos, e o segundo é o que importa: ou pelo botão "Nova
 * reserva", em branco, ou já preenchido a partir do bloco escolhido na grade
 * do dia -- aí sala, dia e horário vêm prontos e sobra o assunto e o número
 * de participantes. É a diferença entre "preencha seis campos" e "você clicou
 * no bloco das 13:00 do Lab IV; sobre o que é a reunião?".
 */
final class FormularioDeReserva {

    private final JTextField sala = new JTextField(6);
    private final JTextField data = new JTextField(11);
    private final ComboDeHorario inicio = ComboDeHorario.inicio(ComboDeHorario.NAO_INFORMADO);
    private final ComboDeHorario fim = ComboDeHorario.fim(ComboDeHorario.NAO_INFORMADO);
    private final JTextField assunto = new JTextField(26);
    private final JTextField participantes = new JTextField(5);

    FormularioDeReserva() {
        fim.seguir(inicio);
        // Ver a nota em FormularioDeSala: os nomes são o que a suíte de tela
        // usa para achar cada campo dentro da janela.
        sala.setName("reservaSala");
        data.setName("reservaData");
        inicio.setName("reservaInicio");
        fim.setName("reservaFim");
        assunto.setName("reservaAssunto");
        participantes.setName("reservaParticipantes");
    }

    /** Preenche a partir do bloco escolhido na grade, ou de uma reserva existente. */
    void preencher(String sala, String data, String inicio, String fim,
                   String assunto, String participantes) {
        this.sala.setText(valor(sala));
        this.data.setText(valor(data));
        // O início antes do fim: a lista de fins só mostra o que vem depois.
        this.inicio.selecionarHora(inicio);
        this.fim.selecionarHora(fim);
        this.assunto.setText(valor(assunto));
        this.participantes.setText(valor(participantes));
    }

    String sala() {
        return sala.getText();
    }

    String data() {
        return data.getText();
    }

    String inicio() {
        return inicio.hora();
    }

    String fim() {
        return fim.hora();
    }

    String assunto() {
        return assunto.getText();
    }

    String participantes() {
        return participantes.getText();
    }

    /**
     * @param ehEdicao ao alterar, campo em branco mantém o valor atual
     *                 (regra 2.11); ao criar, falta preencher
     */
    boolean mostrar(Component pai, String titulo, boolean ehEdicao) {
        JPanel campos = new JPanel();
        new Estilo.Grade(campos)
                .campo("Sala (ID):", linha(sala, new JLabel("Dia (AAAA-MM-DD):"), data))
                .campo("Horário:", linha(inicio, new JLabel("até"), fim))
                .campo("Assunto:", assunto)
                .campo("Participantes:", participantes);

        JPanel tudo = new JPanel();
        tudo.setLayout(new BoxLayout(tudo, BoxLayout.Y_AXIS));
        tudo.add(campos);
        tudo.add(Box.createVerticalStrut(10));
        tudo.add(dica("Assunto sem acento, até 60 caracteres."));
        tudo.add(dica("Participantes não pode passar da capacidade da sala."));
        tudo.add(dica(ehEdicao
                ? "Campo em branco mantém o valor atual."
                    + " Adiantar o fim libera o resto do horário."
                : "Os horários são aulas da UTFPR; o código vem entre parênteses."));

        int escolha = JOptionPane.showConfirmDialog(pai, tudo, titulo,
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        return escolha == JOptionPane.OK_OPTION;
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
