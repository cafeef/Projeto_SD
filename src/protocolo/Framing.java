package protocolo;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Enquadramento das mensagens do protocolo de troca de mensagens.
 *
 * Implementa as regras de transporte:
 *
 *  1.3 Codificacao ......... UTF-8 explicito nos dois sentidos
 *  1.4 Formato ............. uma mensagem = um JSON em linha unica
 *  1.5 Delimitador ......... cada mensagem termina com LF ('\n')
 *  1.6 Tamanho maximo ...... 8192 bytes por mensagem, incluindo o LF
 *  1.9 Conexao ............. timeout de inatividade de 300 s
 *
 * Por que nao usar PrintWriter/BufferedReader: PrintWriter(OutputStream) usa o
 * charset padrao da plataforma (nao necessariamente UTF-8) e println() escreve
 * System.lineSeparator(), que no Windows e CRLF -- os dois pontos violam as
 * regras 1.3 e 1.5 e quebram a interoperabilidade com os outros grupos.
 * BufferedReader.readLine(), por sua vez, conta caracteres e nao bytes, logo nao
 * serve para aplicar o limite da regra 1.6. Aqui a leitura e feita byte a byte,
 * contando bytes, e a decodificacao UTF-8 acontece so depois de achar o LF.
 */
public class Framing implements Closeable {

    /** Regra 1.6: 8192 bytes por mensagem, ja incluindo o LF. */
    public static final int TAMANHO_MAXIMO = 8192;

    /** Regra 1.9: timeout de inatividade de 300 s. */
    public static final int TIMEOUT_INATIVIDADE_MS = 300_000;

    private final Socket socket;
    private final InputStream entrada;
    private final OutputStream saida;

    public Framing(Socket socket) throws IOException {
        this.socket = socket;
        // Regra 1.9: a conexao cai por inatividade, mas o token NAO e invalidado.
        socket.setSoTimeout(TIMEOUT_INATIVIDADE_MS);
        this.entrada = new BufferedInputStream(socket.getInputStream());
        this.saida = new BufferedOutputStream(socket.getOutputStream());
    }

    /**
     * Envia uma mensagem: serializa em UTF-8, acrescenta o LF e faz flush.
     *
     * @param json objeto JSON ja serializado em linha unica (use
     *             com.google.gson.Gson#toJson, que nunca emite LF literal)
     * @throws EnvioExcedeLimiteException se a mensagem nao cabe no limite da
     *         regra 1.6; quem chama deve enviar algo menor no lugar, e nunca
     *         fechar a conexao sem resposta (regra 4.5)
     */
    public void enviar(String json) throws IOException, EnvioExcedeLimiteException {
        if (json == null) {
            throw new IllegalArgumentException("mensagem nula");
        }
        // Regra 1.4: sem quebras de linha internas. Um LF literal no meio do
        // JSON partiria a mensagem em duas para o receptor.
        if (json.indexOf('\n') >= 0 || json.indexOf('\r') >= 0) {
            throw new IllegalArgumentException(
                    "mensagem contem quebra de linha literal; use JSON em linha unica (regra 1.4)");
        }
        byte[] corpo = json.getBytes(StandardCharsets.UTF_8);
        if (corpo.length + 1 > TAMANHO_MAXIMO) {
            throw new EnvioExcedeLimiteException(corpo.length + 1);
        }
        saida.write(corpo);
        saida.write('\n');
        saida.flush();
    }

    /**
     * Le uma mensagem completa, acumulando bytes ate o LF.
     *
     * @return o JSON recebido (sem o LF), ou {@code null} quando a conexao foi
     *         encerrada pelo outro lado sem nada pendente no buffer
     * @throws MensagemExcedeLimiteException quando passa de {@link #TAMANHO_MAXIMO}
     *         bytes sem encontrar o LF; o excedente e descartado antes de lancar,
     *         de modo que a proxima chamada volta a ler mensagens normalmente
     */
    public String receber() throws IOException, MensagemExcedeLimiteException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(256);
        int b;
        while ((b = entrada.read()) != -1) {
            if (b == '\n') {
                return decodificar(buffer.toByteArray());
            }
            buffer.write(b);
            // Ao chegar a TAMANHO_MAXIMO bytes de corpo, a mensagem ja nao cabe
            // no limite nem que o proximo byte seja o LF (corpo + LF > 8192).
            if (buffer.size() >= TAMANHO_MAXIMO) {
                int descartados = descartarAteLF(buffer.size());
                throw new MensagemExcedeLimiteException(descartados);
            }
        }
        // Fim do stream: ou nao havia nada (conexao encerrada), ou o outro lado
        // fechou depois de enviar a ultima mensagem sem o LF final.
        if (buffer.size() == 0) {
            return null;
        }
        return decodificar(buffer.toByteArray());
    }

    /**
     * Decodifica o frame em UTF-8, tolerando um CR final.
     *
     * O protocolo manda LF puro (regra 1.5), mas uma implementacao que use
     * println() no Windows envia CRLF. Aceitar o CR extra e barato e garante a
     * interoperabilidade pedida pelas obs. 6 e 7 da grade -- na duvida, somos
     * rigorosos no que enviamos e tolerantes no que recebemos.
     */
    private String decodificar(byte[] bytes) {
        int fim = bytes.length;
        while (fim > 0 && bytes[fim - 1] == '\r') {
            fim--;
        }
        return new String(bytes, 0, fim, StandardCharsets.UTF_8);
    }

    /** Descarta bytes ate o proximo LF, para ressincronizar o stream. */
    private int descartarAteLF(int jaLidos) throws IOException {
        int total = jaLidos;
        int b;
        while ((b = entrada.read()) != -1) {
            total++;
            if (b == '\n') {
                break;
            }
        }
        return total;
    }

    public Socket getSocket() {
        return socket;
    }

    /** Descreve o outro lado da conexao, para uso nos logs. */
    public String descreverPar() {
        return socket.getInetAddress().getHostAddress() + ":" + socket.getPort();
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }
}
