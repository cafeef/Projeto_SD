package servidor.dao;

/**
 * Violacao de uma restricao UNIQUE: usuario ou email ja cadastrado.
 *
 * Corresponde ao status 409 do protocolo. As restricoes UNIQUE do esquema sao o
 * que torna essa deteccao confiavel sob concorrencia: dois clientes cadastrando
 * o mesmo usuario ao mesmo tempo nao passam os dois, porque quem perde a corrida
 * esbarra no indice, e nao num SELECT que ja ficou desatualizado.
 */
public class DadoDuplicadoException extends Exception {

    private static final long serialVersionUID = 1L;

    public DadoDuplicadoException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
