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

    /** Para onde reconectar depois de uma queda (regra 1.11). */
    private String ip;
    private int porta;
    private boolean reconectou;

    public void conectar(String ip, int porta) throws IOException {
        fechar();
        this.ip = ip;
        this.porta = porta;
        socket = new Socket();
        socket.connect(new InetSocketAddress(ip, porta), TIMEOUT_CONEXAO_MS);
        framing = new Framing(socket);
    }

    /**
     * Diz se ha para onde reconectar: ja houve um conectar() bem-sucedido.
     *
     * Separa "a conexao caiu" de "nunca houve conexao". No primeiro caso a
     * tela reabre sozinha (regra 1.11); no segundo ela pede o IP e a porta,
     * porque nao ha o que reabrir.
     */
    public synchronized boolean podeReconectar() {
        return ip != null;
    }

    /** Diz se a ultima troca precisou reabrir a conexao. So para o registro. */
    public synchronized boolean reconectouNaUltimaTroca() {
        return reconectou;
    }

    /**
     * Envia e, se a conexao tiver caido, reabre e reenvia uma vez.
     *
     * E o que as regras 1.9 e 1.11 pedem: com 300 s de inatividade o servidor
     * fecha a conexao mas NAO invalida o token, e o cliente deve abrir outra e
     * reenviar a requisicao com o mesmo token. A requisicao que esbarrou no
     * socket morto nao foi respondida, entao "nao conta como concluida" -- por
     * isso reenviar e seguro mesmo para create_reservation.
     *
     * A queda aparece de duas formas, e as duas precisam ser tratadas: a
     * leitura devolve null (o servidor fechou depois que escrevemos) ou a
     * escrita estoura IOException (ele ja tinha fechado). Tratar so a primeira
     * deixaria metade dos casos de fora.
     *
     * @return a resposta, ou null se nem a reconexao resolveu
     */
    public synchronized String enviarComReconexao(String json)
            throws IOException, EnvioExcedeLimiteException, MensagemExcedeLimiteException {
        reconectou = false;
        try {
            String resposta = enviarEReceber(json);
            if (resposta != null) {
                return resposta;
            }
        } catch (IOException e) {
            // Socket quebrado na escrita: mesma situacao, mesmo remedio.
        }
        if (!reconectar()) {
            return null;
        }
        reconectou = true;
        return enviarEReceber(json);
    }

    /**
     * Abre outra conexao para o mesmo servidor.
     *
     * Regra 1.9: com 300 s de inatividade o servidor fecha a conexao, mas NAO
     * invalida o token. Regra 1.11: o cliente abre outra e reenvia a
     * requisicao com o mesmo token. Sem isto, uma pausa de cinco minutos
     * derrubava a sessao da tela enquanto o servidor ainda a tinha por ate 30
     * minutos -- e o login seguinte batia em 409.
     *
     * @return false se nao ha para onde reconectar ou a tentativa falhou
     */
    public synchronized boolean reconectar() {
        if (ip == null) {
            return false;
        }
        try {
            conectar(ip, porta);
            return true;
        } catch (IOException e) {
            return false;
        }
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
