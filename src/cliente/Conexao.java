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
     * O limite de 8192 bytes da regra 1.6 vale para a REQUISICAO, e e conferido
     * aqui: melhor falhar com mensagem clara do que mandar uma linha que o
     * servidor vai descartar. A resposta nao tem limite desde a v2.9.1, porque
     * as listagens crescem com os dados.
     *
     * @return o JSON de resposta, ou null se o servidor encerrou a conexao
     */
    public synchronized String enviarEReceber(String json)
            throws IOException, EnvioExcedeLimiteException, MensagemExcedeLimiteException {
        if (!estaConectado()) {
            throw new IOException("nao conectado");
        }
        int bytes = json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length + 1;
        if (bytes > Framing.TAMANHO_MAXIMO) {
            throw new EnvioExcedeLimiteException(bytes);
        }
        framing.enviar(json);
        return framing.receberResposta();
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
