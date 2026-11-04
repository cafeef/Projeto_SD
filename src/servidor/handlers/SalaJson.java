package servidor.handlers;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import servidor.modelo.Sala;

/**
 * Traducao de uma Sala do banco para o objeto 'Sala' do protocolo.
 *
 * Fica em classe propria porque tres operacoes devolvem o mesmo formato --
 * read_room no corpo da resposta, list_rooms dentro de 'rooms' e, na EP-3, a
 * leitura que acompanha a agenda. Escrever o mesmo mapeamento tres vezes seria
 * convidar as tres copias a divergirem.
 */
final class SalaJson {

    private SalaJson() {
    }

    /**
     * @param disponivel null omite o campo 'available', como o protocolo manda
     *                   quando a requisicao nao traz o trio de horario
     */
    static JsonObject de(Sala sala, Boolean disponivel) {
        JsonObject item = new JsonObject();
        // Regra 2.7: tudo string, inclusive o id e a capacidade.
        item.addProperty("room_id", String.valueOf(sala.getId()));
        item.addProperty("name", sala.getNome());
        item.addProperty("capacity", String.valueOf(sala.getCapacidade()));
        item.addProperty("location", sala.getLocalizacao());

        JsonArray recursos = new JsonArray();
        for (String recurso : sala.getRecursos()) {
            recursos.add(recurso);
        }
        // Array vazio quando nao houver recurso -- nunca ausente, nunca null.
        item.add("resources", recursos);

        item.addProperty("room_status", sala.getEstado());
        if (disponivel != null) {
            item.addProperty("available", disponivel ? "true" : "false");
        }
        return item;
    }
}
