package servidor.handlers;

import com.google.gson.JsonObject;

import servidor.AutorizacaoAdmin;
import servidor.modelo.Usuario;

/**
 * Base das operacoes restritas a administradores (regra 3.14).
 *
 * Acrescenta um degrau a ordem da HandlerAutenticado:
 *
 *   1. formato do token ...... 400 (mensagem propria de cada operacao)
 *   2. sessao valida ......... 401 "Token invalido ou expirado"
 *   3. poder de admin ........ 403 "Permissao insuficiente"
 *   4. regra da operacao ..... delegado a subclasse
 *
 * O passo 3 consulta a whitelist, e nao o role gravado no banco nem qualquer
 * campo da requisicao. E o que faz uma promocao valer na hora para quem ja esta
 * logado, e o que impede um cliente de se declarar admin mandando "role":
 * "admin" junto da requisicao.
 */
public abstract class HandlerAdmin extends HandlerAutenticado {

    protected HandlerAdmin(String op, String mensagem400) {
        super(op, mensagem400);
    }

    @Override
    protected final JsonObject tratarAutenticado(JsonObject requisicao, Usuario autor,
                                                 String token) throws Exception {
        if (!AutorizacaoAdmin.temPoder(token)) {
            return resposta("403", "Permissao insuficiente");
        }
        return tratarComoAdmin(requisicao, autor, token);
    }

    protected abstract JsonObject tratarComoAdmin(JsonObject requisicao, Usuario autor,
                                                  String token) throws Exception;
}
