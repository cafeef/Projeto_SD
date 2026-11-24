package servidor.handlers;

import com.google.gson.JsonObject;

import servidor.modelo.Reserva;

/**
 * Traducao de uma Reserva do banco para o objeto 'Reserva' do protocolo.
 *
 * Duas operacoes devolvem o mesmo conjunto de campos -- list_reservations
 * dentro de 'reservations' e read_reservation no proprio corpo --, e a unica
 * diferenca e o 'created_at', que so o read_reservation traz. Uma classe so
 * impede que as duas divirjam com o tempo.
 */
final class ReservaJson {

    private ReservaJson() {
    }

    /**
     * @param comCriadaEm true no read_reservation, que exige 'created_at';
     *                    false nos itens da listagem, cujo esquema nao o tem
     */
    static JsonObject de(Reserva reserva, boolean comCriadaEm) {
        JsonObject item = new JsonObject();
        // Regra 2.7: tudo string, inclusive ids e numero de participantes.
        item.addProperty("reservation_id", String.valueOf(reserva.getId()));
        item.addProperty("room_id", String.valueOf(reserva.getSalaId()));
        // 'name' e o nome da SALA, e nao um nome da reserva.
        item.addProperty("name", reserva.getNomeSala());
        item.addProperty("user", reserva.getUsuario());
        item.addProperty("date", reserva.getDia().toString());
        item.addProperty("start_time", reserva.getInicio().toString());
        item.addProperty("end_time", reserva.getFim().toString());
        item.addProperty("topic", reserva.getAssunto());
        item.addProperty("participants", String.valueOf(reserva.getParticipantes()));
        if (comCriadaEm && reserva.getCriadaEm() != null) {
            // "AAAA-MM-DD hh:mm:ss": espaco no lugar do 'T' do ISO, e sem os
            // nanossegundos que o LocalDateTime.toString() acrescentaria.
            item.addProperty("created_at", reserva.getCriadaEm()
                    .format(java.time.format.DateTimeFormatter
                            .ofPattern("yyyy-MM-dd HH:mm:ss")));
        }
        return item;
    }
}
