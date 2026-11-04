package servidor.handlers;

import com.google.gson.JsonObject;

import protocolo.CampoInvalidoException;
import protocolo.Validador;
import servidor.dao.SalaDAO;
import servidor.modelo.Sala;
import servidor.modelo.Usuario;

/**
 * read_room -- le uma sala.
 *
 * Liberado a qualquer usuario logado, admin ou nao: quem vai reservar precisa
 * ver a sala. Por isso herda de HandlerAutenticado, e nao de HandlerAdmin.
 *
 * Nao devolve a agenda da sala. O protocolo manda obte-la com list_reservations
 * filtrando por room_id, que e operacao da EP-3.
 */
public class ReadRoomHandler extends HandlerAutenticado {

    public ReadRoomHandler() {
        super("read_room", "Dados em formato invalido");
    }

    @Override
    protected JsonObject tratarAutenticado(JsonObject requisicao, Usuario autor, String token)
            throws Exception {
        String roomId;
        try {
            roomId = Validador.obrigatorio(requisicao, "room_id");
        } catch (CampoInvalidoException e) {
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
        return resposta;
    }
}
