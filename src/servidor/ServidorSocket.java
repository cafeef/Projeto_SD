package servidor;

import servidor.dao.SessaoDAO;

import java.io.IOException;
import java.sql.SQLException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;

/**
 * Laco de aceite de conexoes, com inicio e parada controlaveis.
 *
 * Separado da interface para que o console e a tela Swing usem o mesmo servidor:
 * a tela so precisa chamar iniciar()/parar() e receber as linhas de log.
 */
public class ServidorSocket {

    private final int porta;
    private final Dispatcher dispatcher;
    private final RegistradorLog log;

    private ServerSocket serverSocket;
    private Thread threadAceite;
    private volatile boolean rodando;

    private final AtomicInteger conectados = new AtomicInteger();
    private IntConsumer aoMudarConectados = quantos -> { };

    public ServidorSocket(int porta, Dispatcher dispatcher, RegistradorLog log) {
        this.porta = porta;
        this.dispatcher = dispatcher;
        this.log = log;
    }

    /** Avisa a interface quando o numero de clientes conectados muda. */
    public void setAoMudarConectados(IntConsumer ouvinte) {
        this.aoMudarConectados = ouvinte == null ? quantos -> { } : ouvinte;
    }

    /**
     * Abre a porta e comeca a aceitar conexoes.
     *
     * A abertura da porta acontece AQUI, de forma sincrona, para que "porta
     * ocupada" chegue a quem chamou como excecao -- e a tela consiga mostrar o
     * erro em vez de parecer que iniciou.
     */
    public void iniciar() throws IOException {
        if (rodando) {
            return;
        }
        serverSocket = new ServerSocket(porta);
        rodando = true;

        // Sessoes de antes nao sobrevivem ao servidor: quando ele para, todas as
        // conexoes caem e nenhum cliente consegue continuar de onde parou. Fica
        // aqui, e nao na abertura do banco, para cobrir tambem o caso de parar e
        // iniciar de novo pela tela, sem fechar a aplicacao.
        try {
            int encerradas = SessaoDAO.invalidarTodas();
            if (encerradas > 0) {
                log.registrar("Sessoes anteriores encerradas: " + encerradas);
            }
        } catch (SQLException e) {
            log.registrar("Aviso: nao foi possivel encerrar as sessoes anteriores: "
                    + e.getMessage());
        }

        log.registrar("Servidor escutando na porta " + porta);

        threadAceite = new Thread(this::aceitar, "aceite-" + porta);
        threadAceite.setDaemon(true);
        threadAceite.start();
    }

    private void aceitar() {
        while (rodando) {
            try {
                Socket cliente = serverSocket.accept();
                conectados.incrementAndGet();
                aoMudarConectados.accept(conectados.get());

                Thread t = new Thread(new ConexaoHandler(cliente, dispatcher, log, () -> {
                    conectados.decrementAndGet();
                    aoMudarConectados.accept(conectados.get());
                }), "cliente-" + cliente.getPort());
                t.setDaemon(true);
                t.start();
            } catch (IOException e) {
                if (rodando) {
                    log.registrar("Erro ao aceitar conexao: " + e.getMessage());
                }
                // Com rodando == false, a excecao e o efeito esperado do close().
            }
        }
    }

    /** Fecha a porta. As conexoes ja abertas seguem ate o cliente encerrar. */
    public void parar() {
        if (!rodando) {
            return;
        }
        rodando = false;
        try {
            if (serverSocket != null) {
                serverSocket.close();   // desbloqueia o accept()
            }
        } catch (IOException e) {
            log.registrar("Erro ao fechar a porta: " + e.getMessage());
        }
        log.registrar("Servidor parado.");
    }

    public boolean estaRodando() {
        return rodando;
    }

    public int getConectados() {
        return conectados.get();
    }

    public int getPorta() {
        return porta;
    }
}
