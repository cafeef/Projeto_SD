package servidor.handlers;

import com.google.gson.JsonObject;

import protocolo.CampoInvalidoException;
import protocolo.Resposta;
import protocolo.Validador;
import servidor.Handler;
import servidor.dao.SessaoDAO;
import servidor.modelo.Usuario;

/**
 * Base das operacoes que exigem token (regra 2.5).
 *
 * Concentra a parte que e igual em todas elas e define a ordem de validacao:
 *
 *   1. formato do token ...... 400 (mensagem propria de cada operacao)
 *   2. sessao valida ......... 401 "Token invalido ou expirado"
 *   3. regra da operacao ..... delegado a subclasse
 *
 * A ordem "formato antes de sessao" nao esta escrita como regra geral na
 * planilha, mas e a que ela implica: cada operacao define mensagens distintas
 * para 400 e 401 ("Token em formato invalido" x "Token invalido ou expirado"),
 * o que so faz sentido se o formato for conferido primeiro.
 *
 * Sobre a mensagem de 400: a planilha define UMA mensagem de 400 por operacao,
 * entao e ela que sai para qualquer reprovacao de formato daquela operacao --
 * inclusive a do token. E por isso que o delete_user responde 400 com "Senha em
 * formato invalido" mesmo quando o problema esta no token.
 */
public abstract class HandlerAutenticado implements Handler {

    private final String op;
    private final String mensagem400;

    protected HandlerAutenticado(String op, String mensagem400) {
        this.op = op;
        this.mensagem400 = mensagem400;
    }

    @Override
    public final JsonObject tratar(JsonObject requisicao) throws Exception {
        String token;
        try {
            token = Validador.obrigatorio(requisicao, "token");
        } catch (CampoInvalidoException e) {
            return Resposta.de(op, "400", mensagem400);
        }

        // Regra 3.8: o autor sai do token; o cliente nunca diz quem e.
        // Regra 3.3: esta chamada tambem renova a contagem dos 30 minutos.
        Usuario autor = SessaoDAO.validarERenovar(token);
        if (autor == null) {
            return Resposta.de(op, "401", "Token invalido ou expirado");
        }

        return tratarAutenticado(requisicao, autor, token);
    }

    /** Resposta de 400 desta operacao, para as validacoes proprias da subclasse. */
    protected JsonObject erro400() {
        return Resposta.de(op, "400", mensagem400);
    }

    protected JsonObject resposta(String status, String message) {
        return Resposta.de(op, status, message);
    }

    protected String getOp() {
        return op;
    }

    protected abstract JsonObject tratarAutenticado(JsonObject requisicao, Usuario autor,
                                                    String token) throws Exception;
}
