package cliente;

import com.google.gson.JsonObject;

import java.util.function.Consumer;

/**
 * O que um painel precisa da tela principal para conversar com o servidor.
 *
 * Existe para que os paineis de salas e de usuarios sejam so interface: quem
 * cuida do socket, da thread de rede, do token e da reacao ao 401 continua
 * sendo a {@link ClienteGUI}, num lugar so. Se cada painel abrisse sua propria
 * conexao, o protocolo seria violado na hora -- a regra 3.5 permite uma sessao
 * ativa por usuario, e o token da sessao vale na conexao em que foi emitido e
 * em qualquer outra, mas duas conexoes do mesmo cliente competindo pelas
 * respostas seria so uma forma elaborada de embaralhar as mensagens.
 */
interface Canal {

    /** Monta uma requisicao com 'op' como primeira chave (regra 2.1). */
    static JsonObject requisicao(String op) {
        JsonObject req = new JsonObject();
        req.addProperty("op", op);
        return req;
    }

    /**
     * Envia fora da EDT e entrega a resposta ao tratador, de volta na EDT.
     *
     * O tratador NAO precisa cuidar do 401: a tela principal ja descarta o
     * token e volta para o login antes de chamar.
     */
    void trocar(JsonObject requisicao, Consumer<JsonObject> aoResponder);

    /** O token da sessao atual, ou null se nao ha sessao. */
    String token();

    /**
     * Se a sessao atual e de administrador.
     *
     * Vem do 'role' que o login e o read_user devolvem, e serve SO para montar
     * a tela -- esconder um botao nao protege nada. Quem decide o que a
     * requisicao pode fazer e a whitelist de tokens do servidor (regra 3.14).
     */
    boolean ehAdmin();

    /** Mostra a mensagem do servidor, acentuada para leitura. */
    void informar(JsonObject resposta);

    /** Mostra um erro gerado aqui no cliente, sem ida ao servidor. */
    void erro(String mensagem);

    /**
     * Pede a tela principal para reler o cadastro.
     *
     * Chamado depois de uma alteracao que pode ter mexido no proprio perfil --
     * um admin que se rebaixa perde o poder na hora (regra 3.14), e a tela tem
     * que acompanhar em vez de seguir mostrando abas que agora respondem 403.
     */
    void relerCadastro();
}
