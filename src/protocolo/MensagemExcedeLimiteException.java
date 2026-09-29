package protocolo;

/**
 * Lancada quando uma mensagem recebida passa de {@link Framing#TAMANHO_MAXIMO}
 * bytes (regra 1.6 do protocolo). O frame excedente ja foi descartado ate o
 * proximo LF, portanto a conexao continua utilizavel: o receptor deve apenas
 * responder com status 400 (regra 4.3) e seguir lendo a proxima mensagem.
 */
public class MensagemExcedeLimiteException extends Exception {

    private static final long serialVersionUID = 1L;

    private final int bytesLidos;

    public MensagemExcedeLimiteException(int bytesLidos) {
        super("Mensagem excede o tamanho maximo de " + Framing.TAMANHO_MAXIMO
                + " bytes (lidos " + bytesLidos + " bytes sem encontrar o LF)");
        this.bytesLidos = bytesLidos;
    }

    public int getBytesLidos() {
        return bytesLidos;
    }
}
