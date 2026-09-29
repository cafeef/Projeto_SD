package servidor;

import com.google.gson.JsonObject;

/**
 * Trata uma operacao do protocolo.
 *
 * O handler recebe a requisicao ja confirmada como objeto JSON com 'op' valida,
 * e devolve a resposta completa -- inclusive as respostas de erro proprias da
 * operacao (400, 401, 403, 404, 409), porque o texto de 'message' muda de
 * operacao para operacao e so o handler sabe qual usar.
 *
 * O que o handler NAO precisa tratar: falha inesperada. Qualquer excecao que
 * escapar daqui vira o 500 da regra 4.4, montado pelo {@link Dispatcher}.
 */
@FunctionalInterface
public interface Handler {

    JsonObject tratar(JsonObject requisicao) throws Exception;
}
