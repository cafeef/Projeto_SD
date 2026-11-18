package servidor;

import protocolo.Erros;
import protocolo.Framing;
import protocolo.MensagemExcedeLimiteException;


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
                    requisicao = framing.receberRequisicao();
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
     * Envia a resposta e registra no terminal (obs. 3 da planilha de avaliacao).
     *
     * Desde a v2.9.1 a resposta nao tem limite de tamanho (regra 1.6): uma
     * listagem cresce com os dados, e truncar ou recusar seria pior do que
     * mandar uma linha longa -- o cliente le ate o LF de qualquer jeito. Antes
     * disso, uma listagem grande virava 500 aqui.
     */
    private void responder(Framing framing, String par, String resposta) throws IOException {
        framing.enviar(resposta);
        log.registrar("[" + par + "] enviou:  " + resposta);
    }
}
