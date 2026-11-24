package servidor.handlers;

import com.google.gson.JsonObject;

import protocolo.CampoInvalidoException;
import protocolo.Validador;
import servidor.AutorizacaoAdmin;
import servidor.dao.ReservaDAO;
import servidor.modelo.Reserva;
import servidor.modelo.Usuario;

/**
 * read_reservation -- le uma reserva pelo id. Item c/k da EP-3.
 *
 * So o dono e o admin leem (regra 3.14): a reserva traz o assunto da reuniao,
 * que nao e informacao publica.
 *
 * A ordem entre 404 e 403 e a da secao 4, passo 5 antes do 6: o 404 vale
 * mesmo para quem nao seria o dono. Pode parecer que isso vaza informacao --
 * quem recebe 403 descobre que a reserva existe --, mas a alternativa vaza
 * mais: responder 404 para reserva de terceiro faria o sistema mentir para o
 * proprio dono quando ele errasse o id, e o protocolo prefere a resposta
 * honesta.
 *
 * A reserva CANCELADA responde 404: o cancelamento e o fim dela, e e assim que
 * o testador oficial da turma le o protocolo (ver ReservaDAO.buscarPorId).
 */
public class ReadReservationHandler extends HandlerAutenticado {

    public ReadReservationHandler() {
        super("read_reservation", "Dados em formato invalido");
    }

    @Override
    protected JsonObject tratarAutenticado(JsonObject requisicao, Usuario autor, String token)
            throws Exception {
        String reservationId;
        try {
            reservationId = Validador.obrigatorio(requisicao, "reservation_id");
        } catch (CampoInvalidoException e) {
            return erro400();
        }

        Reserva reserva = ReservaDAO.buscarPorId(Integer.parseInt(reservationId));
        if (reserva == null) {
            return resposta("404", "Reserva nao encontrada");
        }
        if (!reserva.ehDe(autor.getId()) && !AutorizacaoAdmin.temPoder(token)) {
            return resposta("403", "Permissao insuficiente");
        }

        // O 200 traz os campos no proprio corpo, como o read_room.
        JsonObject resposta = resposta("200", "Consulta realizada com sucesso");
        JsonObject dados = ReservaJson.de(reserva, true);
        for (String campo : dados.keySet()) {
            resposta.add(campo, dados.get(campo));
        }
        return resposta;
    }
}
