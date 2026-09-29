package protocolo;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

/**
 * Montagem das respostas do protocolo.
 *
 * Concentra num lugar so tres regras que valem para TODA resposta:
 *
 *  1.8 a 'op' da resposta e a da requisicao mais o sufixo '_response'
 *  2.1 'op' e sempre a primeira chave
 *  2.6 toda resposta tem, no minimo, 'op', 'status' e 'message'
 *
 * A ordem das chaves sai certa porque o JsonObject do Gson preserva a ordem de
 * insercao, e os tres campos obrigatorios entram antes de qualquer outro.
 */
public final class Resposta {

    private static final Gson GSON = new Gson();

    private Resposta() {
    }

    /**
     * Cria a resposta de uma operacao.
     *
     * @param opRequisicao a 'op' que veio na requisicao, SEM o sufixo
     * @param status       o codigo como string (regra 2.7), ex.: "200"
     * @param message      o texto exato da aba de mensagens (regra 2.8)
     */
    public static JsonObject de(String opRequisicao, String status, String message) {
        JsonObject resposta = new JsonObject();
        resposta.addProperty("op", opRequisicao + "_response");
        resposta.addProperty("status", status);
        resposta.addProperty("message", message);
        return resposta;
    }

    /** Serializa em linha unica, pronta para o {@link Framing#enviar(String)}. */
    public static String serializar(JsonObject resposta) {
        return GSON.toJson(resposta);
    }
}
