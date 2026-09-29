package servidor;

import servidor.dao.Banco;
import servidor.handlers.DeleteUserHandler;
import servidor.handlers.EcoHandler;
import servidor.handlers.LoginHandler;
import servidor.handlers.LogoutHandler;
import servidor.handlers.ReadUserHandler;
import servidor.handlers.RegisterHandler;
import servidor.handlers.UpdateUserHandler;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.BindException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;

/**
 * Entrada do servidor em modo console.
 *
 * A interface oficial e a {@link ServidorGUI}; esta versao existe para rodar os
 * testes automatizados, que precisam subir o servidor sem depender de tela.
 */
public class ServidorConsole {

    public static void main(String[] args) throws IOException {
        int porta;
        if (args.length > 0) {
            porta = Integer.parseInt(args[0]);
        } else {
            BufferedReader teclado = new BufferedReader(
                    new InputStreamReader(System.in, StandardCharsets.UTF_8));
            System.out.print("Porta de escuta do servidor: ");
            try {
                porta = Integer.parseInt(teclado.readLine().trim());
            } catch (NumberFormatException e) {
                System.err.println("Porta invalida: informe um numero.");
                return;
            }
        }
        if (porta < 1 || porta > 65535) {
            System.err.println("Porta invalida: use um valor entre 1 e 65535.");
            return;
        }

        // --memoria: banco em memoria, sem criar nem travar o arquivo dados/.
        // E o que os testes automatizados usam.
        boolean emMemoria = false;
        for (String arg : args) {
            if ("--memoria".equals(arg)) {
                emMemoria = true;
            }
        }
        try {
            if (emMemoria) {
                Banco.iniciarComUrl("jdbc:h2:mem:servidor;DB_CLOSE_DELAY=-1");
                System.out.println("Banco em memoria (modo de teste).");
            } else {
                Banco.iniciar();
            }
        } catch (SQLException e) {
            System.err.println("Falha ao abrir o banco: " + e.getMessage());
            return;
        }

        Dispatcher dispatcher = emMemoria ? criarDispatcherDeTeste() : criarDispatcher();
        ServidorSocket servidor = new ServidorSocket(porta, dispatcher, RegistradorLog.CONSOLE);
        try {
            servidor.iniciar();
        } catch (BindException e) {
            System.err.println("A porta " + porta + " esta ocupada. Escolha outra.");
            return;
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            servidor.parar();
            Banco.encerrar();
        }));

        // Segura a thread principal enquanto o servidor roda.
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Monta o dispatcher com as operacoes do protocolo.
     *
     * As operacoes de administrador, de salas e de reservas entram aqui nas
     * EP-2 e EP-3; cada uma e so mais um registrar().
     */
    public static Dispatcher criarDispatcher() {
        Dispatcher dispatcher = new Dispatcher();
        dispatcher.registrar("register", new RegisterHandler());
        dispatcher.registrar("login", new LoginHandler());
        dispatcher.registrar("logout", new LogoutHandler());
        dispatcher.registrar("read_user", new ReadUserHandler());
        dispatcher.registrar("update_user", new UpdateUserHandler());
        dispatcher.registrar("delete_user", new DeleteUserHandler());
        return dispatcher;
    }

    /**
     * Dispatcher dos testes: as operacoes reais mais a "eco".
     *
     * A "eco" nao faz parte do protocolo e por isso NAO entra no servidor de
     * verdade -- um grupo que recebesse essa operacao nao saberia o que fazer
     * com ela. Ela sobrevive so aqui porque os testes de transporte precisam de
     * uma operacao de resposta previsivel e sem efeito no banco.
     */
    static Dispatcher criarDispatcherDeTeste() {
        Dispatcher dispatcher = criarDispatcher();
        dispatcher.registrar("eco", new EcoHandler());
        return dispatcher;
    }
}
