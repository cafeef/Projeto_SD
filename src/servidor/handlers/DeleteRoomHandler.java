package servidor.handlers;

import com.google.gson.JsonObject;

import protocolo.CampoInvalidoException;
import protocolo.Validador;
import servidor.dao.SalaDAO;
import servidor.modelo.Usuario;

/**
 * delete_room -- remove uma sala. Exige admin (regra 3.7).
 *
 * Sala com reserva futura responde 409: a remocao apagaria reservas que outras
 * pessoas contam usar, sem nenhum aviso a elas. Para tirar de circulacao uma
 * sala que tem agenda, o protocolo oferece o caminho certo -- update_room com
 * "room_status": "inactive", que bloqueia reservas NOVAS e preserva as que ja
 * existem.
 *
 * Sala sem reserva futura e removida de fato, e as reservas antigas vao com ela
 * pelo ON DELETE CASCADE.
 */
public class DeleteRoomHandler extends HandlerAdmin {

    public DeleteRoomHandler() {
        super("delete_room", "Dados em formato invalido");
    }

    @Override
    protected JsonObject tratarComoAdmin(JsonObject requisicao, Usuario autor, String token)
            throws Exception {
        String roomId;
        try {
            roomId = Validador.obrigatorio(requisicao, "room_id");
        } catch (CampoInvalidoException e) {
            return erro400();
        }

        int id = Integer.parseInt(roomId);
        if (SalaDAO.buscarPorId(id) == null) {
            return resposta("404", "Sala nao encontrada");
        }

        if (SalaDAO.temReservaFutura(id)) {
            return resposta("409", "Sala possui reservas ativas");
        }

        if (!SalaDAO.remover(id)) {
            // Removida por outro administrador entre o SELECT e o DELETE.
            return resposta("404", "Sala nao encontrada");
        }

        return resposta("200", "Sala removida com sucesso");
    }
}
