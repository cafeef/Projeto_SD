package cliente;

import protocolo.EnvioExcedeLimiteException;
import protocolo.Framing;
import protocolo.MensagemExcedeLimiteException;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * Conexao do cliente com o servidor.
 *
 * O protocolo e estritamente requisicao/resposta na mesma conexao (regra 1.7),
 * entao a operacao natural aqui e "envia e espera a resposta" -- nao ha
 * mensagem do servidor sem pedido do cliente.
 *
 * O metodo de troca e synchronized porque a tela pode disparar duas acoes em
 * sequencia: sem isso, duas requisicoes se misturariam no mesmo socket e cada
 * uma leria a resposta da outra.
 */
public class Conexao implements Closeable {

    /** Tempo maximo para estabelecer a conexao TCP. */
    private static final int TIMEOUT_CONEXAO_MS = 5000;

    private Socket socket;
    private Framing framing;

    public void conectar(String ip, int porta) throws IOException {
        fechar();
        socket = new Socket();
        socket.connect(new InetSocketAddress(ip, porta), TIMEOUT_CONEXAO_MS);
        framing = new Framing(socket);
    }

    public boolean estaConectado() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    /**
     * Envia a requisicao e devolve a resposta crua.
     *
     * @return o JSON de resposta, ou null se o servidor encerrou a conexao
     */
    public synchronized String enviarEReceber(String json)
            throws IOException, EnvioExcedeLimiteException, MensagemExcedeLimiteException {
        if (!estaConectado()) {
            throw new IOException("nao conectado");
        }
        framing.enviar(json);
        return framing.receber();
    }

    public void fechar() {
        if (framing != null) {
            try {
                framing.close();
            } catch (IOException ignorado) {
                // Fechando de qualquer forma.
            }
            framing = null;
        }
        socket = null;
    }

    @Override
    public void close() {
        fechar();
    }
}
