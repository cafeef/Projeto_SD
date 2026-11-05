package ui;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.border.Border;
import javax.swing.table.DefaultTableModel;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

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

    /**
     * Formulario de linhas "rotulo: campo", montado sobre GridBagLayout.
     *
     * Existe porque as telas de salas e de usuarios repetem o mesmo arranjo
     * varias vezes, e porque o GridBagLayout exige um detalhe facil de esquecer:
     * sem uma terceira coluna que absorva a largura sobrando, ele centraliza os
     * campos no painel e a tela fica com tudo boiando no meio.
     */
    public static final class Grade {

        private final JPanel painel;
        private final GridBagConstraints g = new GridBagConstraints();
        private int linha;

        public Grade(JPanel painel) {
            this.painel = painel;
            painel.setLayout(new GridBagLayout());
            g.insets = new Insets(5, 6, 5, 6);
            g.anchor = GridBagConstraints.WEST;
        }

        /** Uma linha com rotulo a esquerda e componente a direita. */
        public Grade campo(String rotulo, Component entrada) {
            g.gridy = linha++;
            g.gridx = 0;
            g.weightx = 0;
            g.gridwidth = 1;
            g.fill = GridBagConstraints.NONE;
            painel.add(new JLabel(rotulo), g);

            g.gridx = 1;
            painel.add(entrada, g);

            g.gridx = 2;
            g.weightx = 1.0;
            g.fill = GridBagConstraints.HORIZONTAL;
            painel.add(Box.createHorizontalGlue(), g);

            g.weightx = 0;
            g.fill = GridBagConstraints.NONE;
            return this;
        }

        /** Uma linha ocupando as tres colunas -- botoes, dicas, separadores. */
        public Grade linhaCheia(Component componente) {
            g.gridy = linha++;
            g.gridx = 0;
            g.gridwidth = 3;
            g.weightx = 1.0;
            g.fill = GridBagConstraints.HORIZONTAL;
            painel.add(componente, g);

            g.gridwidth = 1;
            g.weightx = 0;
            g.fill = GridBagConstraints.NONE;
            return this;
        }

        /** Uma dica em letra miuda, logo abaixo dos campos. */
        public Grade dica(String texto) {
            return linhaCheia(rodape(texto));
        }
    }

    /**
     * Tabela somente de leitura, de linha unica selecionavel.
     *
     * As listagens do protocolo sao consultas: a edicao acontece pelas
     * operacoes de alteracao, nunca digitando na celula. Uma celula editavel
     * daria a impressao de que o valor foi gravado quando nada foi enviado.
     */
    public static JTable tabela(String... colunas) {
        DefaultTableModel modelo = new DefaultTableModel(colunas, 0) {
            private static final long serialVersionUID = 1L;

            @Override
            public boolean isCellEditable(int linha, int coluna) {
                return false;
            }
        };
        JTable t = new JTable(modelo);
        t.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        t.setRowHeight(t.getRowHeight() + 6);
        t.getTableHeader().setReorderingAllowed(false);
        t.setFillsViewportHeight(true);
        return t;
    }

    /** O modelo de uma tabela criada por {@link #tabela(String...)}. */
    public static DefaultTableModel modeloDe(JTable tabela) {
        return (DefaultTableModel) tabela.getModel();
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
