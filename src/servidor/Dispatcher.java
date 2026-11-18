package servidor;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import protocolo.CampoInvalidoException;
import protocolo.Erros;
import protocolo.Resposta;
import protocolo.Validador;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Encaminha cada requisicao para o handler da sua 'op'.
 *
 * Aqui ficam apenas os erros de PROTOCOLO, os da secao 4 -- os que independem da
 * operacao pedida:
 *
 *  4.1 JSON invalido ......... {"op":"error","status":"400",...}
 *  4.2 Operacao desconhecida . idem
 *  4.4 Falha interna ......... 500 com a 'op' da requisicao
 *
 * Os erros de NEGOCIO (401, 403, 404, 409 e os 400 de validacao) ficam nos
 * handlers, porque o texto de 'message' muda de operacao para operacao.
 *
 * O mapa e concorrente porque varias threads de conexao leem dele ao mesmo
 * tempo; o registro acontece uma vez, antes do servidor abrir a porta.
 */
public class Dispatcher {

    private static final Gson GSON = new Gson();

    private final Map<String, Handler> handlers = new ConcurrentHashMap<>();

    public void registrar(String op, Handler handler) {
        handlers.put(op, handler);
    }

    public boolean conhece(String op) {
        return handlers.containsKey(op);
    }

    /**
     * Processa uma requisicao crua e devolve a resposta ja serializada.
     *
     * Nunca lanca e nunca devolve null: a regra 4.5 proibe o servidor de nao
     * responder, entao toda saida deste metodo e uma mensagem valida.
     */
    public String processar(String requisicaoCrua) {
        String op = null;
        try {
            JsonElement raiz;
            try {
                raiz = JsonParser.parseString(requisicaoCrua);
            } catch (JsonSyntaxException e) {
                return Erros.requisicaoInvalida();
            }
            // Um texto solto como "abc" nao faz o Gson lancar: vira um primitivo.
            // Dai a checagem explicita de que a mensagem e um objeto (regra 1.4).
            if (raiz == null || !raiz.isJsonObject()) {
                return Erros.requisicaoInvalida();
            }
            JsonObject requisicao = raiz.getAsJsonObject();

            // Regra 4.1 (v2.9.1): os dois erros sao distintos e a diferenca e
            // quem errou. Sem 'op', ou com 'op' que nao e string, a mensagem
            // nao chega a ser uma requisicao -- e "Requisicao invalida", o
            // mesmo que um JSON quebrado. Com 'op' que e string mas nao
            // conhecemos, a mensagem esta bem formada e o que falta e a
            // operacao: "Operacao desconhecida". Util entre grupos, porque
            // separa "voce mandou lixo" de "eu nao implementei isso".
            JsonElement campoOp = requisicao.get("op");
            if (campoOp == null || !campoOp.isJsonPrimitive()
                    || !campoOp.getAsJsonPrimitive().isString()) {
                return Erros.requisicaoInvalida();
            }
            op = campoOp.getAsString();

            Handler handler = handlers.get(op);
            if (handler == null) {
                return Erros.operacaoDesconhecida();
            }

            JsonObject resposta = handler.tratar(requisicao);
            if (resposta == null) {
                throw new IllegalStateException("handler de '" + op + "' devolveu null");
            }
            return Resposta.serializar(resposta);

        } catch (Exception e) {
            // Regra 4.4: qualquer falha inesperada vira 500, sem derrubar a
            // conexao e sem vazar detalhe interno para o cliente.
            System.err.println("falha ao processar '" + op + "': " + e);
            return Erros.erroInterno(op);
        }
    }

    /** Usado so nos testes, para inspecionar a resposta como objeto. */
    public JsonObject processarComoObjeto(String requisicaoCrua) {
        return GSON.fromJson(processar(requisicaoCrua), JsonObject.class);
    }
}
