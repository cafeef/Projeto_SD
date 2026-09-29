package servidor;

import protocolo.EnvioExcedeLimiteException;
import protocolo.Erros;
import protocolo.Framing;
import protocolo.MensagemExcedeLimiteException;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketTimeoutException;

/**
 * Atende UMA conexao de cliente, do inicio ao fim.
 *
 * Roda na sua propria thread (regra 1.1) e mantem o laco requisicao/resposta
 * enquanto a conexao viver (regras 1.7 e 1.9).
 */
public class ConexaoHandler implements Runnable {

    private final Socket socket;
    private final Dispatcher dispatcher;
    private final RegistradorLog log;
    private final Runnable aoEncerrar;

    public ConexaoHandler(Socket socket, Dispatcher dispatcher, RegistradorLog log,
                          Runnable aoEncerrar) {
        this.socket = socket;
        this.dispatcher = dispatcher;
        this.log = log;
        this.aoEncerrar = aoEncerrar;
    }

    @Override
    public void run() {
        String par = socket.getInetAddress().getHostAddress() + ":" + socket.getPort();
        log.registrar("[" + par + "] conexao aceita");

        try (Framing framing = new Framing(socket)) {
            while (true) {
                String requisicao;
                try {
                    requisicao = framing.receber();
                } catch (MensagemExcedeLimiteException e) {
                    // Regra 4.3: responde 400 e continua -- o Framing ja
                    // ressincronizou o stream descartando ate o proximo LF.
                    log.registrar("[" + par + "] recusada: " + e.getMessage());
                    responder(framing, par, Erros.mensagemExcedeTamanho());
                    continue;
                }

                if (requisicao == null) {
                    log.registrar("[" + par + "] conexao encerrada pelo cliente");
                    return;
                }

                // Obs. 3 da grade: mostrar as mensagens recebidas e enviadas.
                log.registrar("[" + par + "] recebeu: " + requisicao);
                responder(framing, par, dispatcher.processar(requisicao));
            }
        } catch (SocketTimeoutException e) {
            // Regra 1.9: cai por inatividade, mas o token continua valido.
            log.registrar("[" + par + "] encerrada por inatividade ("
                    + (Framing.TIMEOUT_INATIVIDADE_MS / 1000) + " s)");
        } catch (IOException e) {
            log.registrar("[" + par + "] erro de I/O: " + e.getMessage());
        } finally {
            aoEncerrar.run();
        }
    }

    /**
     * Envia garantindo que ALGUMA resposta sempre sai (regra 4.5).
     *
     * Uma resposta legitima pode passar de 8192 bytes: uma listagem com muitos
     * registros, por exemplo. Como nao existe frame valido a escrever e fechar a
     * conexao violaria a regra 4.5, cai para o 500 da regra 4.4, que e curto e
     * sempre cabe. Quando as listagens da EP-2 chegarem, a solucao correta passa
     * a ser limitar o tamanho ANTES de montar a resposta.
     */
    private void responder(Framing framing, String par, String resposta) throws IOException {
        try {
            framing.enviar(resposta);
            log.registrar("[" + par + "] enviou:  " + resposta);
            return;
        } catch (EnvioExcedeLimiteException e) {
            log.registrar("[" + par + "] " + e.getMessage() + "; respondendo 500");
        }
        String fallback = Erros.erroInterno(opDaResposta(resposta));
        try {
            framing.enviar(fallback);
        } catch (EnvioExcedeLimiteException impossivel) {
            throw new IllegalStateException("resposta de erro estourou o limite", impossivel);
        }
        log.registrar("[" + par + "] enviou:  " + fallback);
    }

    /** Recupera a 'op' original da resposta que nao coube, para o 500 sair com o nome certo. */
    private String opDaResposta(String resposta) {
        try {
            JsonObject json = JsonParser.parseString(resposta).getAsJsonObject();
            String op = json.get("op").getAsString();
            return op.endsWith("_response")
                    ? op.substring(0, op.length() - "_response".length())
                    : op;
        } catch (RuntimeException e) {
            return null;
        }
    }
}
