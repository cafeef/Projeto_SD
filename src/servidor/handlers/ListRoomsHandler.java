package servidor.handlers;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import protocolo.CampoInvalidoException;
import protocolo.Validador;
import servidor.dao.SalaDAO;
import servidor.modelo.Sala;
import servidor.modelo.Usuario;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Set;

/**
 * list_rooms -- lista as salas, com filtros opcionais.
 *
 * Liberado a qualquer usuario logado. Dois detalhes do protocolo mandam no
 * formato da resposta:
 *
 *  - 'date', 'start_time' e 'end_time' andam JUNTOS: os tres ou nenhum. Parte
 *    do trio responde 400, porque "esta livre no dia 15?" sem hora nao e
 *    pergunta respondivel -- a sala pode estar livre de manha e ocupada a
 *    tarde, e qualquer resposta seria enganosa;
 *  - o campo 'available' so aparece COM o trio. Sem horario nao ha
 *    disponibilidade a calcular, e devolver 'available' fixo em "true" seria
 *    afirmar algo que nao foi perguntado nem verificado.
 *
 * O 'min_capacity' e independente: vale com ou sem o trio.
 */
public class ListRoomsHandler extends HandlerAutenticado {

    public ListRoomsHandler() {
        super("list_rooms", "Filtros em formato invalido");
    }

    @Override
    protected JsonObject tratarAutenticado(JsonObject requisicao, Usuario autor, String token)
            throws Exception {
        String data;
        String inicio;
        String fim;
        String minCapacidade;
        try {
            data = Validador.filtroOpcional(requisicao, "date");
            inicio = Validador.filtroOpcional(requisicao, "start_time");
            fim = Validador.filtroOpcional(requisicao, "end_time");
            minCapacidade = Validador.filtroOpcional(requisicao, "min_capacity");
        } catch (CampoInvalidoException e) {
            return erro400();
        }

        int pedacosDoTrio = (data == null ? 0 : 1) + (inicio == null ? 0 : 1) + (fim == null ? 0 : 1);
        if (pedacosDoTrio != 0 && pedacosDoTrio != 3) {
            return erro400();
        }

        LocalDate dia = null;
        LocalTime horaInicial = null;
        LocalTime horaFinal = null;
        if (pedacosDoTrio == 3) {
            try {
                // A regex garante o formato, mas nao a existencia da data:
                // "2026-02-31" casa com o padrao e nao existe no calendario.
                dia = LocalDate.parse(data);
                horaInicial = LocalTime.parse(inicio);
                horaFinal = LocalTime.parse(fim);
            } catch (DateTimeParseException e) {
                return erro400();
            }
            // 'end_time' tem que ser maior que 'start_time' e no mesmo dia.
            if (!horaFinal.isAfter(horaInicial)) {
                return erro400();
            }
        }

        List<Sala> salas = SalaDAO.listar(
                minCapacidade == null ? null : Integer.valueOf(minCapacidade));

        // Uma consulta so para todas as salas ocupadas no periodo, em vez de uma
        // por sala listada.
        Set<Integer> ocupadas = pedacosDoTrio == 3
                ? SalaDAO.ocupadasNoPeriodo(dia, horaInicial, horaFinal)
                : null;

        JsonArray lista = new JsonArray();
        for (Sala sala : salas) {
            Boolean disponivel = null;
            if (ocupadas != null) {
                // Sala "inactive" nao aceita reserva, logo nao esta disponivel,
                // mesmo com a agenda livre.
                disponivel = sala.estaAtiva() && !ocupadas.contains(sala.getId());
            }
            lista.add(SalaJson.de(sala, disponivel));
        }

        JsonObject resposta = resposta("200", "Consulta realizada com sucesso");
        resposta.addProperty("count", String.valueOf(salas.size()));
        resposta.add("rooms", lista);
        return resposta;
    }
}
