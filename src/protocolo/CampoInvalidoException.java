package protocolo;

/**
 * Lancada pelo {@link Validador} quando um campo da requisicao nao atende as
 * regras do protocolo. Em todos os motivos a resposta correta e status 400
 * (regras 2.10 e 2.11) -- o texto de 'message', porem, varia por operacao, por
 * isso a excecao carrega apenas o campo e o motivo e deixa a escolha da
 * mensagem para o handler.
 */
public class CampoInvalidoException extends Exception {

    private static final long serialVersionUID = 1L;

    public enum Motivo {
        /** Chave obrigatoria ausente na requisicao (regra 2.10). */
        AUSENTE,
        /** Valor null explicito (regra 2.10). */
        NULO,
        /** Valor que nao e string: numero, booleano, objeto ou array (regra 2.3/2.10). */
        TIPO_INVALIDO,
        /** String vazia onde ela nao e permitida (regra 2.11). */
        VAZIO,
        /** String reprovada pela regex do campo (aba Dicionario). */
        FORMATO_INVALIDO,
        /** Chave que a operacao nao aceita, como 'email' no update_user (RNF 5.c). */
        PROIBIDO
    }

    private final String campo;
    private final Motivo motivo;

    public CampoInvalidoException(String campo, Motivo motivo) {
        super("campo '" + campo + "': " + motivo);
        this.campo = campo;
        this.motivo = motivo;
    }

    public String getCampo() {
        return campo;
    }

    public Motivo getMotivo() {
        return motivo;
    }
}
