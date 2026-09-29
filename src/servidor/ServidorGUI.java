package servidor;

import servidor.dao.Banco;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.net.BindException;
import java.sql.SQLException;

/**
 * Interface grafica do servidor: controle da porta e do estado de escuta.
 *
 * Atende a obs. 4 da grade -- campo para digitar a porta de escuta. As mensagens
 * JSON trocadas (obs. 3) saem no TERMINAL que executa a aplicacao, nao aqui.
 */
public final class ServidorGUI extends JFrame {

    private static final long serialVersionUID = 1L;

    private final JTextFieldPorta campoPorta = new JTextFieldPorta();
    private final JButton botaoIniciar = new JButton("Iniciar");
    private final JButton botaoParar = new JButton("Parar");
    private final JLabel rotuloStatus = new JLabel("Parado");
    private final JLabel rotuloConectados = new JLabel("Clientes conectados: 0");

    private transient ServidorSocket servidor;

    /** Campo de porta com largura fixa, so para nao poluir o construtor. */
    private static final class JTextFieldPorta extends javax.swing.JTextField {
        private static final long serialVersionUID = 1L;

        JTextFieldPorta() {
            super("5000", 6);
            setMaximumSize(new Dimension(90, 30));
        }
    }

    public ServidorGUI() {
        super("Servidor - Reserva de Salas");
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        montarTela();

        botaoIniciar.addActionListener(e -> iniciarServidor());
        botaoParar.addActionListener(e -> pararServidor());
        botaoParar.setEnabled(false);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                pararServidor();
                Banco.encerrar();
                dispose();
                System.exit(0);
            }
        });

        setSize(560, 220);
        setLocationRelativeTo(null);
    }

    private void montarTela() {
        JPanel linhaPorta = new JPanel();
        linhaPorta.setLayout(new BoxLayout(linhaPorta, BoxLayout.X_AXIS));
        linhaPorta.add(new JLabel("Porta de escuta: "));
        linhaPorta.add(campoPorta);
        linhaPorta.add(Box.createHorizontalStrut(12));
        linhaPorta.add(botaoIniciar);
        linhaPorta.add(Box.createHorizontalStrut(6));
        linhaPorta.add(botaoParar);
        linhaPorta.add(Box.createHorizontalGlue());

        rotuloStatus.setFont(rotuloStatus.getFont().deriveFont(Font.BOLD));

        JPanel estado = new JPanel();
        estado.setLayout(new BoxLayout(estado, BoxLayout.Y_AXIS));
        estado.setAlignmentX(LEFT_ALIGNMENT);
        estado.add(rotuloStatus);
        estado.add(Box.createVerticalStrut(4));
        estado.add(rotuloConectados);

        JLabel aviso = new JLabel(
                "As mensagens JSON trocadas aparecem no terminal que executa o servidor.");
        aviso.setFont(aviso.getFont().deriveFont(Font.ITALIC));

        JPanel conteudo = new JPanel();
        conteudo.setLayout(new BoxLayout(conteudo, BoxLayout.Y_AXIS));
        conteudo.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        linhaPorta.setAlignmentX(LEFT_ALIGNMENT);
        aviso.setAlignmentX(LEFT_ALIGNMENT);
        conteudo.add(linhaPorta);
        conteudo.add(Box.createVerticalStrut(16));
        conteudo.add(estado);
        conteudo.add(Box.createVerticalGlue());
        conteudo.add(aviso);

        setLayout(new BorderLayout());
        add(conteudo, BorderLayout.CENTER);
    }

    private void iniciarServidor() {
        int porta;
        try {
            porta = Integer.parseInt(campoPorta.getText().trim());
        } catch (NumberFormatException e) {
            erro("Porta invalida: informe um numero.");
            return;
        }
        if (porta < 1 || porta > 65535) {
            erro("Porta invalida: use um valor entre 1 e 65535.");
            return;
        }

        try {
            if (!Banco.estaIniciado()) {
                Banco.iniciar();
                RegistradorLog.CONSOLE.registrar("Banco de dados aberto em dados/reservasalas.");
            }
        } catch (SQLException e) {
            erro("Falha ao abrir o banco de dados:\n" + e.getMessage());
            return;
        }

        // O log vai para o terminal; a janela so acompanha o estado.
        servidor = new ServidorSocket(porta, ServidorConsole.criarDispatcher(),
                RegistradorLog.CONSOLE);
        servidor.setAoMudarConectados(quantos -> SwingUtilities.invokeLater(
                () -> rotuloConectados.setText("Clientes conectados: " + quantos)));

        try {
            servidor.iniciar();
        } catch (BindException e) {
            erro("A porta " + porta + " ja esta em uso. Escolha outra.");
            servidor = null;
            return;
        } catch (IOException e) {
            erro("Nao foi possivel abrir a porta " + porta + ":\n" + e.getMessage());
            servidor = null;
            return;
        }

        rotuloStatus.setText("Escutando na porta " + porta);
        botaoIniciar.setEnabled(false);
        botaoParar.setEnabled(true);
        campoPorta.setEnabled(false);
    }

    private void pararServidor() {
        if (servidor != null) {
            servidor.parar();
            servidor = null;
        }
        rotuloStatus.setText("Parado");
        rotuloConectados.setText("Clientes conectados: 0");
        botaoIniciar.setEnabled(true);
        botaoParar.setEnabled(false);
        campoPorta.setEnabled(true);
    }

    /**
     * Mostra o erro na janela E no terminal.
     *
     * Como o log das mensagens vive no terminal, uma falha que aparecesse so na
     * caixa de dialogo sumiria assim que fosse fechada, sem deixar rastro em
     * lugar nenhum -- justamente quando se precisa dele.
     */
    private void erro(String mensagem) {
        System.err.println("ERRO: " + mensagem.replace('\n', ' '));
        JOptionPane.showMessageDialog(this, mensagem, "Servidor", JOptionPane.ERROR_MESSAGE);
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignorado) {
            // Segue com o visual padrao do Swing.
        }
        SwingUtilities.invokeLater(() -> new ServidorGUI().setVisible(true));
    }
}
