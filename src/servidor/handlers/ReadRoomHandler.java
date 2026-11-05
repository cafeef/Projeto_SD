package servidor.handlers;

import com.google.gson.JsonObject;

import com.google.gson.JsonArray;

import protocolo.CampoInvalidoException;
import protocolo.Horarios;
import protocolo.Validador;
import servidor.AutorizacaoAdmin;
import servidor.dao.SalaDAO;
import servidor.modelo.Sala;
import servidor.modelo.Usuario;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Set;

/**
 * read_room -- le uma sala.
 *
 * Liberado a qualquer usuario logado, admin ou nao: quem vai reservar precisa
 * ver a sala. Por isso herda de HandlerAutenticado, e nao de HandlerAdmin.
 *
 * Com 'date', devolve tambem a AGENDA daquele dia (regra 8.5): um item por
 * horario de funcionamento da sala naquele dia da semana, na ordem da grade,
 * dizendo se esta livre ou indisponivel e, quando reservado, quem reservou.
 *
 * Sem 'date' a agenda NAO vai junto -- nem como lista vazia. Agenda vazia
 * significa "a sala nao funciona nesse dia", que e uma afirmacao sobre um dia;
 * sem data nao ha dia sobre o qual afirmar.
 *
 * O 'user' de um horario reservado aparece para qualquer usuario logado, porque
 * e o que o item (e) da Entrega 2 pede. Ja o 'reservation_id' so vai para o
 * admin e para o dono da reserva, pela mesma regra do read_reservation.
 */
public class ReadRoomHandler extends HandlerAutenticado {

    public ReadRoomHandler() {
        super("read_room", "Dados em formato invalido");
    }

    @Override
    protected JsonObject tratarAutenticado(JsonObject requisicao, Usuario autor, String token)
            throws Exception {
        String roomId;
        String data;
        try {
            roomId = Validador.obrigatorio(requisicao, "room_id");
            data = Validador.filtroOpcional(requisicao, "date");
            if (data != null) {
                // A regex aprova a data, o calendario nao: "2026-02-31" casa
                // com o padrao e nao existe.
                LocalDate.parse(data);
            }
        } catch (CampoInvalidoException | DateTimeParseException e) {
            return erro400();
        }

        Sala sala = SalaDAO.buscarPorId(Integer.parseInt(roomId));
        if (sala == null) {
            return resposta("404", "Sala nao encontrada");
        }

        // O 200 traz os campos da sala no proprio corpo, e nao dentro de um
        // objeto -- diferente do list_rooms, que os aninha em 'rooms'.
        JsonObject resposta = resposta("200", "Consulta realizada com sucesso");
        JsonObject dados = SalaJson.de(sala, null);
        for (String campo : dados.keySet()) {
            resposta.add(campo, dados.get(campo));
        }

        if (data != null) {
            resposta.addProperty("date", data);
            resposta.add("agenda", agendaDoDia(sala, LocalDate.parse(data), autor, token));
        }
        return resposta;
    }

    /**
     * A agenda da sala num dia: um item por horario de funcionamento, na ordem
     * da grade da UTFPR.
     *
     * Sala que nao funciona nesse dia da semana devolve lista vazia, e domingo
     * sempre devolve vazia -- a grade da UTFPR nao tem domingo.
     */
    private JsonArray agendaDoDia(Sala sala, LocalDate dia, Usuario autor, String token)
            throws Exception {
        JsonArray agenda = new JsonArray();
        // Sala inactive nao aceita reserva nenhuma, entao todo horario dela
        // esta indisponivel, mesmo sem ninguem ter reservado (regra 8.5).
        boolean salaParada = !sala.estaAtiva();

        for (String slot : sala.slotsDoDia(dia)) {
            JsonObject item = new JsonObject();
            item.addProperty("slot", slot);
            item.addProperty("start_time", Horarios.inicioDe(slot).toString());
            item.addProperty("end_time", Horarios.fimDe(slot).toString());

            Set<Integer> ocupadas = salaParada
                    ? Set.of()
                    : SalaDAO.ocupadasNoPeriodo(dia, Horarios.inicioDe(slot),
                            Horarios.fimDe(slot));
            boolean reservado = ocupadas.contains(sala.getId());
            item.addProperty("slot_status",
                    (salaParada || reservado) ? "indisponivel" : "livre");

            // 'user' e 'reservation_id' so existem quando ha reserva de
            // verdade. Sala parada nao tem dono do horario -- nao ha reserva,
            // so indisponibilidade.
            if (reservado) {
                String[] dono = SalaDAO.donoDaReserva(sala.getId(), dia,
                        Horarios.inicioDe(slot), Horarios.fimDe(slot));
                if (dono != null) {
                    item.addProperty("user", dono[0]);
                    // Regra 8.5: o id da reserva so para o admin e para o dono.
                    if (AutorizacaoAdmin.temPoder(token)
                            || dono[0].equals(autor.getUsuario())) {
                        item.addProperty("reservation_id", dono[1]);
                    }
                }
            }
            agenda.add(item);
        }
        return agenda;
    }


}
