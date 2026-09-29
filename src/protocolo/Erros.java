package protocolo;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

/**
 * Respostas de erro de protocolo, secao 4 da aba "Regras Gerais".
 *
 * Os textos de 'message' sao copiados literalmente da planilha: a regra 2.8
 * exige portugues sem acento e sem emoji, exatamente como especificado, porque
 * os outros grupos comparam a mensagem recebida com a que esperam.
 */
public final class Erros {

    private static final Gson GSON = new Gson();

    private Erros() {
    }

    /** Regra 4.1: JSON invalido. */
    public static String requisicaoInvalida() {
        return montar("error", "400", "Requisicao invalida");
    }

    /** Regra 4.2: operacao desconhecida. */
    public static String operacaoDesconhecida() {
        return montar("error", "400", "Operacao desconhecida");
    }

    /** Regra 4.3: mensagem acima do limite de 8192 bytes. */
    public static String mensagemExcedeTamanho() {
        return montar("error", "400", "Mensagem excede o tamanho maximo");
    }

    /**
     * Regra 4.4: falha interna. Diferente dos anteriores, este mantem a 'op' da
     * requisicao com o sufixo '_response' (regra 1.8), porque aqui a operacao
     * foi reconhecida -- o que falhou foi o processamento.
     */
    public static String erroInterno(String op) {
        String nome = (op == null || op.isEmpty()) ? "error" : op + "_response";
        return montar(nome, "500", "Erro interno do servidor");
    }

    /**
     * Monta a resposta. A ordem de insercao importa: a regra 2.1 diz que 'op' e
     * sempre a primeira chave, e o JsonObject do Gson preserva a ordem em que
     * as chaves foram adicionadas.
     */
    private static String montar(String op, String status, String message) {
        JsonObject resposta = new JsonObject();
        resposta.addProperty("op", op);
        resposta.addProperty("status", status);
        resposta.addProperty("message", message);
        return GSON.toJson(resposta);
    }
}
