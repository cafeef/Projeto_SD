package ui;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.Border;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;

/**
 * Elementos visuais compartilhados pelas telas do cliente e do servidor.
 *
 * Fica num pacote proprio para que nenhuma das duas interfaces precise importar
 * a outra -- cliente e servidor continuam empacotaveis em JARs separados.
 *
 * As cores sao usadas so como destaque (pontinho de status, acao destrutiva) e
 * nunca como fundo de painel. Isso mantem a tela legivel tanto no tema claro
 * quanto no escuro do sistema, ja que os fundos continuam sendo os do
 * Look and Feel nativo.
 */
public final class Estilo {

    public static final Color VERDE = new Color(0x2E, 0x7D, 0x32);
    public static final Color VERMELHO = new Color(0xC6, 0x28, 0x28);
    public static final Color CINZA = new Color(0x75, 0x75, 0x75);

    /** Respiro padrao das bordas internas. */
    public static final int MARGEM = 16;

    private Estilo() {
    }

    public static JLabel titulo(String texto) {
        JLabel r = new JLabel(texto);
        r.setFont(r.getFont().deriveFont(Font.BOLD, r.getFont().getSize() + 7f));
        return r;
    }

    public static JLabel subtitulo(String texto) {
        JLabel r = new JLabel(texto);
        r.setFont(r.getFont().deriveFont(Font.PLAIN, r.getFont().getSize() + 1f));
        r.setForeground(CINZA);
        return r;
    }

    public static JLabel rodape(String texto) {
        JLabel r = new JLabel(texto);
        r.setFont(r.getFont().deriveFont(Font.ITALIC, r.getFont().getSize() - 1f));
        r.setForeground(CINZA);
        return r;
    }

    /** Rotulo de valor, para os pares "campo: valor" da tela de dados. */
    public static JLabel valor(String texto) {
        JLabel r = new JLabel(texto);
        r.setFont(r.getFont().deriveFont(Font.BOLD));
        return r;
    }

    /** Painel agrupado, com titulo e respiro interno. */
    public static JPanel grupo(String titulo) {
        JPanel p = new JPanel();
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(titulo),
                BorderFactory.createEmptyBorder(14, 12, 12, 12)));
        return p;
    }

    /** Botao da acao principal da tela. */
    public static JButton botaoPrincipal(String texto) {
        JButton b = new JButton(texto);
        b.setFont(b.getFont().deriveFont(Font.BOLD));
        return b;
    }

    /** Botao de acao destrutiva: o texto em vermelho sinaliza antes do clique. */
    public static JButton botaoDestrutivo(String texto) {
        JButton b = new JButton(texto);
        b.setForeground(VERMELHO);
        return b;
    }

    public static Border margem() {
        return BorderFactory.createEmptyBorder(MARGEM, MARGEM, MARGEM, MARGEM);
    }

    /** Alinha a esquerda todos os componentes de um BoxLayout vertical. */
    public static void alinharEsquerda(Component... componentes) {
        for (Component c : componentes) {
            if (c instanceof javax.swing.JComponent jc) {
                jc.setAlignmentX(Component.LEFT_ALIGNMENT);
            }
        }
    }

    /**
     * Indicador de estado: um ponto colorido seguido do texto.
     *
     * A cor sozinha nao carrega a informacao -- o texto ao lado diz a mesma
     * coisa, para quem nao distingue as cores.
     */
    public static final class Indicador extends JLabel {

        private static final long serialVersionUID = 1L;

        public Indicador(String texto, boolean ativo) {
            super();
            atualizar(texto, ativo);
            setFont(getFont().deriveFont(Font.BOLD));
        }

        public void atualizar(String texto, boolean ativo) {
            setText("●  " + texto);
            setForeground(ativo ? VERDE : CINZA);
        }
    }
}
