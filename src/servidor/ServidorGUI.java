package servidor;

import servidor.dao.Banco;
import ui.Estilo;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
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
 * Interface gráfica do servidor: controle da porta e do estado de escuta.
 *
 * Atende a obs. 4 da grade -- campo para digitar a porta de escuta. As mensagens
 * JSON trocadas (obs. 3) saem no TERMINAL que executa a aplicação, não aqui.
 *
 * O texto desta tela usa acentuação normal. As mensagens do protocolo, que
 * trafegam na rede, continuam sem acento por exigência da regra 2.8.
 */
public final class ServidorGUI extends JFrame {

    private static final long serialVersionUID = 1L;

    private final JTextField campoPorta = new JTextField("5000", 6);
    private final JButton botaoIniciar = Estilo.botaoPrincipal("Iniciar");
    private final JButton botaoParar = new JButton("Parar");
    private final Estilo.Indicador indicador = new Estilo.Indicador("Parado", false);
    private final JLabel rotuloConectados = new JLabel("Nenhum cliente conectado");

    private transient ServidorSocket servidor;

    public ServidorGUI() {
        super("Servidor - Reserva de Salas");
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        montarTela();

        botaoIniciar.addActionListener(e -> iniciarServidor());
        botaoParar.addActionListener(e -> pararServidor());
        campoPorta.addActionListener(e -> iniciarServidor());
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

        pack();
        setSize(Math.max(getWidth(), 620), Math.max(getHeight(), 330));
        setMinimumSize(getSize());
        setLocationRelativeTo(null);
    }

    private void montarTela() {
        JLabel titulo = Estilo.titulo("Servidor");
        JLabel subtitulo = Estilo.subtitulo("Sistema de Reserva de Salas de Reunião/Estudo");

        JPanel cabecalho = new JPanel();
        cabecalho.setLayout(new BoxLayout(cabecalho, BoxLayout.Y_AXIS));
        Estilo.alinharEsquerda(titulo, subtitulo);
        cabecalho.add(titulo);
        cabecalho.add(Box.createVerticalStrut(2));
        cabecalho.add(subtitulo);

        JPanel conexao = Estilo.grupo("Conexão");
        conexao.setLayout(new BoxLayout(conexao, BoxLayout.X_AXIS));
        conexao.add(new JLabel("Porta de escuta: "));
        campoPorta.setMaximumSize(new Dimension(100, 32));
        campoPorta.setHorizontalAlignment(JTextField.CENTER);
        conexao.add(campoPorta);
        conexao.add(Box.createHorizontalStrut(14));
        conexao.add(botaoIniciar);
        conexao.add(Box.createHorizontalStrut(8));
        conexao.add(botaoParar);
        conexao.add(Box.createHorizontalGlue());

        JPanel estado = Estilo.grupo("Estado");
        estado.setLayout(new BoxLayout(estado, BoxLayout.Y_AXIS));
        rotuloConectados.setForeground(Estilo.CINZA);
        Estilo.alinharEsquerda(indicador, rotuloConectados);
        estado.add(indicador);
        estado.add(Box.createVerticalStrut(6));
        estado.add(rotuloConectados);

        JLabel rodape = Estilo.rodape(
                "As mensagens JSON trocadas aparecem no terminal que executa o servidor.");

        JPanel conteudo = new JPanel();
        conteudo.setLayout(new BoxLayout(conteudo, BoxLayout.Y_AXIS));
        conteudo.setBorder(Estilo.margem());
        Estilo.alinharEsquerda(cabecalho, conexao, estado, rodape);
        conteudo.add(cabecalho);
        conteudo.add(Box.createVerticalStrut(18));
        conteudo.add(conexao);
        conteudo.add(Box.createVerticalStrut(12));
        conteudo.add(estado);
        conteudo.add(Box.createVerticalGlue());
        conteudo.add(Box.createVerticalStrut(12));
        conteudo.add(rodape);

        setLayout(new BorderLayout());
        add(conteudo, BorderLayout.CENTER);
    }

    private void iniciarServidor() {
        if (servidor != null) {
            return;
        }
        int porta;
        try {
            porta = Integer.parseInt(campoPorta.getText().trim());
        } catch (NumberFormatException e) {
            erro("Porta inválida: informe um número.");
            return;
        }
        if (porta < 1 || porta > 65535) {
            erro("Porta inválida: use um valor entre 1 e 65535.");
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

        // O log vai para o terminal; a janela só acompanha o estado.
        servidor = new ServidorSocket(porta, ServidorConsole.criarDispatcher(),
                RegistradorLog.CONSOLE);
        servidor.setAoMudarConectados(quantos -> SwingUtilities.invokeLater(
                () -> rotuloConectados.setText(descreverConectados(quantos))));

        try {
            servidor.iniciar();
        } catch (BindException e) {
            erro("A porta " + porta + " já está em uso. Escolha outra.");
            servidor = null;
            return;
        } catch (IOException e) {
            erro("Não foi possível abrir a porta " + porta + ":\n" + e.getMessage());
            servidor = null;
            return;
        }

        indicador.atualizar("Escutando na porta " + porta, true);
        botaoIniciar.setEnabled(false);
        botaoParar.setEnabled(true);
        campoPorta.setEnabled(false);
    }

    private void pararServidor() {
        if (servidor != null) {
            servidor.parar();
            servidor = null;
        }
        indicador.atualizar("Parado", false);
        rotuloConectados.setText(descreverConectados(0));
        botaoIniciar.setEnabled(true);
        botaoParar.setEnabled(false);
        campoPorta.setEnabled(true);
    }

    private static String descreverConectados(int quantos) {
        if (quantos == 0) {
            return "Nenhum cliente conectado";
        }
        return quantos == 1 ? "1 cliente conectado" : quantos + " clientes conectados";
    }

    /**
     * Mostra o erro na janela E no terminal.
     *
     * Como o log das mensagens vive no terminal, uma falha que aparecesse só na
     * caixa de diálogo sumiria assim que fosse fechada, sem deixar rastro em
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
            // Segue com o visual padrão do Swing.
        }
        SwingUtilities.invokeLater(() -> new ServidorGUI().setVisible(true));
    }
}
