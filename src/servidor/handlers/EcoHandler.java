package servidor.handlers;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import protocolo.Resposta;
import servidor.Handler;

/**
 * Operacao "eco": devolve o campo 'texto' em maiusculas.
 *
 * TEMPORARIA. Nao faz parte do protocolo -- serve para exercitar o caminho
 * completo (enquadramento, despacho, resposta) enquanto as operacoes reais nao
 * existem, e para dar aos testes uma operacao de resposta previsivel. Sai quando
 * register, login e o CRUD entrarem.
 */
public class EcoHandler implements Handler {

    @Override
    public JsonObject tratar(JsonObject requisicao) {
        JsonElement texto = requisicao.get("texto");
        boolean valido = texto != null
                && !texto.isJsonNull()
                && texto.isJsonPrimitive()
                && texto.getAsJsonPrimitive().isString()
                && !texto.getAsString().isEmpty();

        if (!valido) {
            return Resposta.de("eco", "400", "Dados em formato invalido");
        }

        JsonObject resposta = Resposta.de("eco", "200", "Eco realizado com sucesso");
        resposta.addProperty("echo", texto.getAsString().toUpperCase());
        return resposta;
    }
}
