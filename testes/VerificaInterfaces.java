import cliente.ClienteGUI;
import cliente.Conexao;
import servidor.ServidorGUI;

import java.awt.GraphicsEnvironment;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.SwingUtilities;

/**
 * Verifica as telas e a conexao do cliente.
 *
 * As janelas sao CONSTRUIDAS e descartadas, nunca exibidas: isso prova que nao
 * ha erro de inicializacao nem de layout, sem abrir nada na tela de ninguem.
 *
 * Uso: java VerificaInterfaces <porta-de-um-servidor-ja-rodando>
 */
public class VerificaInterfaces {

    static int ok = 0, falhas = 0;

    public static void main(String[] args) throws Exception {
        int porta = Integer.parseInt(args[0]);

        // --- conexao do cliente contra o servidor de verdade ---
        try (Conexao conexao = new Conexao()) {
            confere("comeca desconectada", !conexao.estaConectado());
            conexao.conectar("127.0.0.1", porta);
            confere("conecta no servidor", conexao.estaConectado());

            String resposta = conexao.enviarEReceber("{\"op\":\"eco\",\"texto\":\"gui\"}");
            confere("troca requisicao/resposta pela Conexao",
                    resposta != null && resposta.contains("\"echo\":\"GUI\""));

            String erro = conexao.enviarEReceber("{\"op\":\"operacao_inexistente\"}");
            confere("operacao sem handler devolve 400",
                    erro != null && erro.contains("Operacao desconhecida"));

            // --- queda da conexao: regras 1.9 e 1.11 ---
            // O servidor fecha o socket com 300 s de inatividade, mas NAO
            // invalida o token. Fechar aqui reproduz o estado em que a
            // proxima requisicao encontra a conexao: morta.
            conexao.fechar();
            confere("a conexao aparece como caida", !conexao.estaConectado());

            String depoisDaQueda =
                    conexao.enviarComReconexao("{\"op\":\"eco\",\"texto\":\"voltei\"}");
            confere("a requisicao chega mesmo com a conexao caida (regra 1.11)",
                    depoisDaQueda != null && depoisDaQueda.contains("\"echo\":\"VOLTEI\""));
            confere("e a Conexao avisa que precisou reabrir",
                    conexao.reconectouNaUltimaTroca());
            confere("depois de reabrir, ela esta conectada de novo",
                    conexao.estaConectado());

            // Sem queda, nao ha reabertura -- o reenvio so acontece quando
            // precisa, senao toda requisicao viraria duas no servidor.
            conexao.enviarComReconexao("{\"op\":\"eco\",\"texto\":\"ok\"}");
            confere("conexao de pe nao e reaberta a toa",
                    !conexao.reconectouNaUltimaTroca());

            conexao.fechar();
            confere("fecha a conexao", !conexao.estaConectado());
        }

        Conexao solta = new Conexao();
        try {
            solta.enviarEReceber("{\"op\":\"eco\",\"texto\":\"x\"}");
            confere("enviar sem conectar falha", false);
        } catch (Exception e) {
            confere("enviar sem conectar falha", true);
        }

        // --- construcao das telas ---
        if (GraphicsEnvironment.isHeadless()) {
            System.out.println("  PULADO  telas Swing (maquina sem ambiente grafico)");
        } else {
            confere("ServidorGUI constroi sem erro", constroi(() -> new ServidorGUI()));
            confere("ClienteGUI constroi sem erro", constroi(() -> new ClienteGUI()));
        }

        System.out.println("\n===== interfaces: " + ok + " passaram, " + falhas + " falharam =====");
        if (falhas > 0) System.exit(1);
    }

    /** Constroi a janela na EDT, sem exibir, e descarta em seguida. */
    static boolean constroi(java.util.function.Supplier<javax.swing.JFrame> fabrica)
            throws Exception {
        AtomicReference<Throwable> erro = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                javax.swing.JFrame janela = fabrica.get();
                janela.dispose();   // nunca chega a aparecer
            } catch (Throwable t) {
                erro.set(t);
            }
        });
        if (erro.get() != null) {
            System.out.println("         causa: " + erro.get());
        }
        return erro.get() == null;
    }

    static void confere(String nome, boolean passou) {
        if (passou) { ok++; System.out.println("  OK    " + nome); }
        else { falhas++; System.out.println("  FALHA " + nome); }
    }
}
