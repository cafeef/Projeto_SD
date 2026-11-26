package cliente;

import protocolo.Horarios;

import javax.swing.DefaultListModel;
import javax.swing.JList;
import javax.swing.ListSelectionModel;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Escolha dos horarios em que uma sala funciona (o 'slots' do protocolo).
 *
 * Substitui o campo em que se digitava "2M1, 2M2, 3T1". O codigo e compacto e
 * bom para a rede, mas exige decorar a grade para escrever um so: dia 2 a 7,
 * turno M/T/N, aula 1 a 6. Aqui cada linha diz o dia e a hora por extenso, e o
 * codigo fica entre parenteses para quem quiser conferir contra o protocolo.
 *
 * A lista e de selecao multipla: ctrl+clique marca avulsos e shift+clique marca
 * uma faixa -- que e o caso comum, porque uma sala costuma funcionar em aulas
 * seguidas do mesmo turno.
 */
final class SeletorDeSlots extends JList<String> {

    private static final long serialVersionUID = 1L;

    /**
     * Todos os horarios da grade, na mesma ordem das linhas da lista.
     *
     * 'transient' porque a lista do Horarios nao e Serializable, e um JList
     * e. Nenhuma tela deste projeto e serializada, mas o compilador avisa com
     * -Xlint e um aviso que se tolera vira um aviso que ninguem le.
     */
    private final transient List<String> codigos = Horarios.todosOsSlots();

    SeletorDeSlots() {
        DefaultListModel<String> modelo = new DefaultListModel<>();
        for (String slot : codigos) {
            modelo.addElement(rotuloDe(slot));
        }
        setModel(modelo);
        setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        // Oito linhas visiveis: cabe um turno inteiro sem rolar, e nao empurra
        // os botoes do formulario para fora da janela.
        setVisibleRowCount(8);
    }

    /** "3T1" vira "terça · 13:00-13:50 (3T1)". */
    private static String rotuloDe(String slot) {
        return Horarios.nomeDoDia(Horarios.diaDe(slot)) + " · "
                + Horarios.inicioDe(slot) + "-" + Horarios.fimDe(slot)
                + " (" + slot + ")";
    }

    /** Os codigos marcados, na ordem da grade. */
    List<String> selecionados() {
        List<String> escolhidos = new ArrayList<>();
        for (int indice : getSelectedIndices()) {
            escolhidos.add(codigos.get(indice));
        }
        return escolhidos;
    }

    /** Os codigos marcados como o texto que os montadores de requisicao usam. */
    String texto() {
        return String.join(", ", selecionados());
    }

    /**
     * Marca os codigos informados e desmarca o resto.
     *
     * Aceita o texto da tabela ("2M1, 2M2") para o formulario poder ser
     * carregado a partir da sala selecionada. Codigo desconhecido e ignorado
     * em silencio: pode vir de um servidor de outro grupo com a grade
     * diferente, e derrubar a tela por causa disso seria pior do que mostrar
     * os horarios que entendemos.
     */
    void selecionar(String textoComCodigos) {
        clearSelection();
        if (textoComCodigos == null || textoComCodigos.isBlank()) {
            return;
        }
        Set<String> alvos = new LinkedHashSet<>();
        for (String parte : textoComCodigos.split(",")) {
            String limpo = parte.trim().toUpperCase();
            if (!limpo.isEmpty()) {
                alvos.add(limpo);
            }
        }
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < codigos.size(); i++) {
            if (alvos.contains(codigos.get(i))) {
                indices.add(i);
            }
        }
        int[] marcados = new int[indices.size()];
        for (int i = 0; i < indices.size(); i++) {
            marcados[i] = indices.get(i);
        }
        setSelectedIndices(marcados);
        if (marcados.length > 0) {
            ensureIndexIsVisible(marcados[0]);
        }
    }

    void limpar() {
        clearSelection();
    }
}
