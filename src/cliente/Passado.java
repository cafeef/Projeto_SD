package cliente;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;

/**
 * O que ja passou, do ponto de vista de quem usa a tela.
 *
 * O protocolo e explicito em duas direcoes opostas, e as duas sao
 * intencionais:
 *
 *   - CONSULTAR o passado e valido (regra 9.4): "aquela sala estava livre na
 *     terca passada?" tem resposta, e o servidor responde 200 normalmente;
 *   - RESERVAR o passado e 400 (regra 9.3), com a mesma message de formato de
 *     qualquer outro campo errado.
 *
 * Junte as duas e aparece a armadilha: a consulta de um dia que ja passou
 * responde "livre", a pessoa le isso como um convite, tenta reservar e leva
 * "Dados da reserva em formato invalido". A propria regra 9.15 manda o cliente
 * cuidar disso -- "senao oferece um horario que o create_reservation recusa".
 *
 * Esta classe e a resposta a esse pedido. Ela nao impede consultar o passado,
 * o que tiraria uma capacidade que o protocolo da de proposito; ela faz a tela
 * DIZER que aquilo nao e reservavel.
 */
final class Passado {

    private Passado() {
    }

    /**
     * Diz se a data e hora informadas ja passaram.
     *
     * Texto vazio ou data que nao existe devolve false: o problema ali e
     * outro, e quem responde por ele e o servidor, com as regras dele.
     */
    static boolean jaPassou(String data, String hora) {
        LocalDateTime instante = instante(data, hora);
        return instante != null && !instante.isAfter(LocalDateTime.now());
    }

    /** Diz se a data e anterior a hoje, sem olhar a hora. */
    static boolean diaJaPassou(String data) {
        LocalDate dia = apenasData(data);
        return dia != null && dia.isBefore(LocalDate.now());
    }

    /**
     * O aviso em portugues, ou null quando nao ha o que avisar.
     *
     * Separa os dois casos porque o conserto e diferente: dia passado pede
     * outra data, horario de hoje que ja comecou pede outra aula.
     */
    static String aviso(String data, String hora) {
        if (!jaPassou(data, hora)) {
            return null;
        }
        return diaJaPassou(data)
                ? "Esse dia já passou: " + data.trim()
                        + ". Escolha uma data de hoje em diante."
                : "Esse horário de hoje já começou (" + hora.trim()
                        + "). Escolha uma aula que ainda vai começar, ou outro dia.";
    }

    /**
     * A frase curta para acompanhar uma consulta sobre o passado, ou null.
     *
     * Vem depois da resposta do servidor, e nao no lugar dela: a consulta foi
     * respondida de verdade, e o que falta e dizer que aquilo nao da para
     * reservar.
     */
    static String naoDaParaReservar(String data, String hora) {
        if (!jaPassou(data, hora)) {
            return null;
        }
        return diaJaPassou(data)
                ? "Atenção: esse dia já passou, então não é possível reservar."
                        + " Para reservar, escolha uma data de hoje em diante."
                : "Atenção: esse horário de hoje já começou, então não é possível"
                        + " reservar. Escolha uma aula que ainda vai começar.";
    }

    private static LocalDateTime instante(String data, String hora) {
        LocalDate dia = apenasData(data);
        if (dia == null || hora == null || hora.isBlank()) {
            return null;
        }
        try {
            return dia.atTime(LocalTime.parse(hora.trim()));
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static LocalDate apenasData(String data) {
        if (data == null || data.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(data.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
