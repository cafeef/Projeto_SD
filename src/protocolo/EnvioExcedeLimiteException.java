package protocolo;

/**
 * Lancada por {@link Framing#enviar(String)} quando a mensagem que ESTE lado
 * quer enviar nao cabe em {@link Framing#TAMANHO_MAXIMO} bytes (regra 1.6).
 *
 * Diferente da {@link MensagemExcedeLimiteException}, que trata do que chega,
 * esta indica um problema nosso: nao existe frame valido a escrever. Quem chama
 * precisa mandar alguma coisa menor no lugar -- fechar a conexao violaria a
 * regra 4.5, que proibe o servidor de encerrar sem responder.
 *
 * O caso realista nao e erro de codigo, e volume: as listagens (list_rooms,
 * admin_list_users, list_reservations) crescem com o numero de registros e
 * passam do limite sem nada de errado na requisicao.
 */
public class EnvioExcedeLimiteException extends Exception {

    private static final long serialVersionUID = 1L;

    private final int bytes;

    public EnvioExcedeLimiteException(int bytes) {
        super("mensagem de " + bytes + " bytes excede o limite de "
                + Framing.TAMANHO_MAXIMO + " bytes (regra 1.6)");
        this.bytes = bytes;
    }

    public int getBytes() {
        return bytes;
    }
}
