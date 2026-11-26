package cliente;

import protocolo.Horarios;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import java.util.ArrayList;
import java.util.List;

/**
 * Lista de escolha de um horario da grade da UTFPR.
 *
 * Substitui o campo de texto em que se digitava "13:00". A regra 8.3 so aceita
 * comeco e fim de aula, entao qualquer outro valor digitado vira 400 -- e um
 * campo livre existia apenas para dar a quem usa a chance de errar. Aqui so
 * existem as opcoes validas.
 *
 * O texto mostrado e "13:00 (T1)": a hora primeiro, porque e o que a pessoa
 * procura, e o codigo da aula entre parenteses, porque e ele que aparece nos
 * horarios de funcionamento da sala. Quem nunca viu a grade escolhe pela hora;
 * quem ja decorou confere pelo codigo. O que viaja no protocolo e so "13:00".
 */
final class ComboDeHorario extends JComboBox<String> {

    private static final long serialVersionUID = 1L;

    /** Rotulo da opcao vazia nos filtros de sala: nenhum horario pedido. */
    static final String QUALQUER = "(qualquer)";

    /**
     * Rotulo da opcao vazia no formulario de reserva.
     *
     * O mesmo formulario cria e altera, e o vazio quer dizer coisas diferentes
     * nos dois: ao criar, falta escolher -- e a tela diz isso antes de mandar;
     * ao alterar, e o "" do protocolo, que mantem o horario atual (regra 2.11).
     * Um rotulo neutro serve aos dois sem mentir em nenhum.
     */
    static final String NAO_INFORMADO = "(não informado)";

    private final boolean ehInicio;
    private final String rotuloVazio;

    private ComboDeHorario(boolean ehInicio, String rotuloVazio) {
        this.ehInicio = ehInicio;
        this.rotuloVazio = rotuloVazio;
        setModel(new DefaultComboBoxModel<>(opcoes(null).toArray(new String[0])));
        // Largura fixa pelo item mais largo que a lista pode ter -- incluindo
        // o rotulo vazio, que costuma ser o maior. Sem isso a lista cortaria
        // "(nao informado)" num "(nao info...", e mudaria de largura a cada
        // vez que seguir() a reconstruisse, fazendo a linha inteira dancar.
        String maisLargo = "00:00 (M0)";
        if (rotuloVazio != null && rotuloVazio.length() > maisLargo.length()) {
            maisLargo = rotuloVazio;
        }
        setPrototypeDisplayValue(maisLargo);
        // Minimo igual ao preferido: sem isso o GridBagLayout encolhe a lista
        // ate ela nao caber o proprio texto quando a janela aperta.
        setMinimumSize(getPreferredSize());
    }

    /**
     * Lista dos inicios de aula (o 'start_time' do protocolo).
     *
     * @param rotuloVazio texto da primeira opcao, que vale ""; null deixa a
     *                    lista so com horarios de verdade
     */
    static ComboDeHorario inicio(String rotuloVazio) {
        return new ComboDeHorario(true, rotuloVazio);
    }

    /** Lista dos fins de aula (o 'end_time' do protocolo). */
    static ComboDeHorario fim(String rotuloVazio) {
        return new ComboDeHorario(false, rotuloVazio);
    }

    /**
     * Faz esta lista de FIM mostrar so o que vem depois do inicio escolhido.
     *
     * Evita de uma vez o 400 mais facil de cometer -- fim antes do inicio --
     * e encurta a lista para as opcoes que fazem sentido naquele turno.
     * Quando o fim escolhido deixa de ser possivel, cai para o primeiro
     * valido, que e o fim da propria aula que acabou de ser escolhida.
     */
    void seguir(ComboDeHorario comboDeInicio) {
        comboDeInicio.addActionListener(e -> {
            String fimAtual = hora();
            List<String> novas = opcoes(comboDeInicio.hora());
            setModel(new DefaultComboBoxModel<>(novas.toArray(new String[0])));
            selecionarHora(novas.contains(rotuloDe(fimAtual))
                    ? fimAtual
                    : primeiraHora(novas, rotuloVazio));
        });
    }

    /** O valor para o protocolo: "13:00", ou "" quando nada foi escolhido. */
    String hora() {
        Object escolhido = getSelectedItem();
        return escolhido == null || escolhido.equals(rotuloVazio)
                ? ""
                : escolhido.toString().substring(0, 5);
    }

    /** Escolhe pelo valor do protocolo. Texto vazio ou desconhecido limpa. */
    void selecionarHora(String hora) {
        if (hora == null || hora.isBlank()) {
            setSelectedIndex(0);
            return;
        }
        if (hora.equals(rotuloVazio)) {
            setSelectedIndex(0);
            return;
        }
        String alvo = rotuloDe(hora.trim());
        for (int i = 0; i < getItemCount(); i++) {
            if (getItemAt(i).equals(alvo)) {
                setSelectedIndex(i);
                return;
            }
        }
        setSelectedIndex(0);
    }

    /**
     * As opcoes da lista.
     *
     * @param depoisDe quando informado, mantem so os fins posteriores a essa
     *                 hora; null traz a tabela inteira
     */
    private List<String> opcoes(String depoisDe) {
        List<String> rotulos = new ArrayList<>();
        if (rotuloVazio != null) {
            rotulos.add(rotuloVazio);
        }
        for (String hora : ehInicio ? Horarios.iniciosDeAula() : Horarios.finsDeAula()) {
            if (depoisDe == null || depoisDe.isEmpty() || hora.compareTo(depoisDe) > 0) {
                rotulos.add(rotuloDe(hora));
            }
        }
        return rotulos;
    }

    private String rotuloDe(String hora) {
        if (hora == null || hora.isEmpty()) {
            return rotuloVazio;
        }
        String aula = ehInicio ? Horarios.aulaQueComecaEm(hora) : Horarios.aulaQueTerminaEm(hora);
        return aula == null ? hora : hora + " (" + aula + ")";
    }

    private static String primeiraHora(List<String> rotulos, String rotuloVazio) {
        for (String rotulo : rotulos) {
            if (!rotulo.equals(rotuloVazio)) {
                return rotulo.substring(0, 5);
            }
        }
        return "";
    }
}
