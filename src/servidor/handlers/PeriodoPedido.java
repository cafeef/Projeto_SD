package servidor.handlers;

import com.google.gson.JsonObject;

import protocolo.CampoInvalidoException;
import protocolo.Horarios;
import protocolo.Validador;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * O trio 'date' + 'start_time' + 'end_time' de uma requisicao, ja validado.
 *
 * Reune num lugar so as quatro checagens de formato que o protocolo pede para
 * esse trio, e que o check_availability e o create_reservation fazem igual:
 *
 *   - os tres campos presentes e no formato (regra 2.10);
 *   - a data existe no calendario -- "2026-02-31" casa com a regex e nao
 *     existe (regra 9.2);
 *   - 'end_time' depois de 'start_time' (regra 9.11);
 *   - inicio e fim sao comeco e fim de aula da grade da UTFPR (regra 8.3).
 *
 * A ultima e a menos obvia e a mais importante: numa grade de aulas fixas,
 * "esta livre das 14:15 as 15:00?" nao e pergunta respondivel, porque nao
 * existe nada nesse recorte para reservar.
 *
 * Tudo aqui e erro de FORMATO, o passo 2 da ordem de validacao -- vem antes do
 * 401, do 404 da sala e de qualquer 409. Quem decide a mensagem do 400 e o
 * handler, que tem a sua.
 */
final class PeriodoPedido {

    private final LocalDate dia;
    private final LocalTime inicio;
    private final LocalTime fim;
    private final List<String> aulas;

    private PeriodoPedido(LocalDate dia, LocalTime inicio, LocalTime fim, List<String> aulas) {
        this.dia = dia;
        this.inicio = inicio;
        this.fim = fim;
        this.aulas = aulas;
    }

    /**
     * Le e valida o trio.
     *
     * @throws CampoInvalidoException em qualquer reprovacao de formato; o
     *         handler traduz para o 400 da sua operacao
     */
    static PeriodoPedido ler(JsonObject requisicao) throws CampoInvalidoException {
        String data = Validador.obrigatorio(requisicao, "date");
        String horaInicial = Validador.obrigatorio(requisicao, "start_time");
        String horaFinal = Validador.obrigatorio(requisicao, "end_time");

        LocalDate dia;
        LocalTime inicio;
        LocalTime fim;
        try {
            dia = LocalDate.parse(data);                 // regra 9.2
            inicio = LocalTime.parse(horaInicial);
            fim = LocalTime.parse(horaFinal);
        } catch (DateTimeParseException e) {
            throw new CampoInvalidoException("date",
                    CampoInvalidoException.Motivo.FORMATO_INVALIDO);
        }

        if (!fim.isAfter(inicio)) {                      // regra 9.11
            throw new CampoInvalidoException("end_time",
                    CampoInvalidoException.Motivo.FORMATO_INVALIDO);
        }
        if (!Horarios.ehInicioDeAula(inicio)) {          // regra 8.3
            throw new CampoInvalidoException("start_time",
                    CampoInvalidoException.Motivo.FORMATO_INVALIDO);
        }
        if (!Horarios.ehFimDeAula(fim)) {
            throw new CampoInvalidoException("end_time",
                    CampoInvalidoException.Motivo.FORMATO_INVALIDO);
        }

        return new PeriodoPedido(dia, inicio, fim, Horarios.aulasEntre(inicio, fim));
    }

    LocalDate getDia() {
        return dia;
    }

    LocalTime getInicio() {
        return inicio;
    }

    LocalTime getFim() {
        return fim;
    }

    /** As aulas da grade que o periodo cobre, para conferir contra os slots da sala. */
    List<String> getAulas() {
        return aulas;
    }

    /**
     * Regra 9.3: o passado da CRIACAO de reserva e 'date' + 'start_time'.
     *
     * Hoje e aceito, mas so numa aula que ainda nao comecou. Consultas nao
     * usam isto -- elas aceitam datas passadas (regra 9.4).
     */
    boolean comecouNoPassado() {
        return !dia.atTime(inicio).isAfter(java.time.LocalDateTime.now());
    }
}
