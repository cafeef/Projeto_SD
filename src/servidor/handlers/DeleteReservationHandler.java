package servidor.handlers;

import com.google.gson.JsonObject;

import protocolo.CampoInvalidoException;
import protocolo.Validador;
import servidor.AutorizacaoAdmin;
import servidor.dao.ReservaDAO;
import servidor.modelo.Reserva;
import servidor.modelo.Usuario;

/**
 * delete_reservation -- cancela uma reserva ativa. Item f/n da EP-3.
 *
 * O dono cancela a sua; o admin cancela a de qualquer um (regra 3.14). Repare
 * que ALTERAR e diferente: nem o admin altera reserva de terceiro (regra 9.9).
 * A assimetria e deliberada -- liberar uma sala e administracao, mudar o
 * assunto ou o tamanho do grupo de alguem e se passar por essa pessoa.
 *
 * Reserva ja encerrada responde 409 "Reserva ja encerrada": cancelar o que ja
 * aconteceu nao libera sala nenhuma, e o 200 faria o cliente dizer que
 * liberou. Encerrada e a que ja terminou -- 'date' + 'end_time' no passado
 * (regra 9.1).
 *
 * O cancelamento MARCA a reserva em vez de apagar a linha, mas para o
 * protocolo ela deixa de existir: cancelar de novo responde 404, e nao 409.
 * A linha fica so como historico no banco.
 */
public class DeleteReservationHandler extends HandlerAutenticado {

    public DeleteReservationHandler() {
        super("delete_reservation", "Dados em formato invalido");
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
        // Passo 7 da secao 4: o 409 de encerrada vem depois do 403 de dono.
        if (reserva.estaEncerrada()) {
            return resposta("409", "Reserva ja encerrada");
        }
        if (!ReservaDAO.cancelar(reserva.getId())) {
            // Cancelada por outra conexao entre a leitura e o UPDATE: para
            // quem chegou depois, a reserva ja nao existe.
            return resposta("404", "Reserva nao encontrada");
        }

        return resposta("200", "Reserva cancelada com sucesso");
    }
}
