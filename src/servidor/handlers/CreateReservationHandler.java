package servidor.handlers;

import com.google.gson.JsonObject;

import protocolo.CampoInvalidoException;
import protocolo.Validador;
import servidor.TrancaDeSala;
import servidor.dao.ReservaDAO;
import servidor.dao.SalaDAO;
import servidor.modelo.Sala;
import servidor.modelo.Usuario;

/**
 * create_reservation -- reserva uma sala. Qualquer usuario logado (item d/l).
 *
 * O dono sai do token (regra 3.8): o cliente nunca diz quem esta reservando.
 *
 * A ordem das respostas segue a secao 4 do protocolo, e cada degrau existe por
 * um motivo diferente:
 *
 *   400 formato ............ campos, data inexistente, fim <= inicio, horario
 *                            fora da grade, inicio no passado (regra 9.3)
 *   404 .................... a sala nao existe
 *   400 participants ....... grupo maior que a capacidade da sala
 *   409 sala inactive ...... "Sala indisponivel para reserva"
 *   409 funcionamento ...... "Horario fora do funcionamento da sala" (8.4)
 *   409 ocupada ............ "Sala ja reservada no periodo solicitado" (5.4)
 *   201 .................... com o reservation_id
 *
 * Os tres 409 nessa ordem porque dizem coisas diferentes a quem pergunta:
 * "esta sala nao aceita reservas", "esta sala nao funciona nesse horario" e
 * "esta sala funciona, mas ja tem dono nesse horario". Trocar a ordem faria o
 * cliente mostrar o motivo errado.
 *
 * Item i da EP-3: a verificacao e a gravacao acontecem dentro da MESMA tranca
 * por room_id (regra 5.1). Sem isso, dois clientes pedindo o mesmo horario
 * podem os dois consultar, os dois achar livre e os dois gravar -- cada um
 * correto sozinho, os dois errados juntos. Com a tranca, o primeiro recebe 201
 * e o segundo, 409.
 */
public class CreateReservationHandler extends HandlerAutenticado {

    public CreateReservationHandler() {
        super("create_reservation", "Dados da reserva em formato invalido");
    }

    @Override
    protected JsonObject tratarAutenticado(JsonObject requisicao, Usuario autor, String token)
            throws Exception {
        String roomId;
        PeriodoPedido periodo;
        String assunto;
        String participantes;
        try {
            roomId = Validador.obrigatorio(requisicao, "room_id");
            periodo = PeriodoPedido.ler(requisicao);
            assunto = Validador.obrigatorio(requisicao, "topic");
            participantes = Validador.obrigatorio(requisicao, "participants");
        } catch (CampoInvalidoException e) {
            return erro400();
        }

        // Regra 9.3: reservar o que ja comecou nao muda nada no mundo. Hoje
        // vale, desde que a aula ainda nao tenha comecado.
        if (periodo.comecouNoPassado()) {
            return erro400();
        }

        final int salaId = Integer.parseInt(roomId);
        final int grupo = Integer.parseInt(participantes);
        final String assuntoFinal = assunto;

        return TrancaDeSala.comASalaTrancada(salaId, () -> {
            Sala sala = SalaDAO.buscarPorId(salaId);
            if (sala == null) {
                return resposta("404", "Sala nao encontrada");
            }

            // Passo 8 da secao 4: o 400 que so da para julgar com o registro
            // na mao, depois do 404 e antes dos 409.
            if (grupo > sala.getCapacidade()) {
                return erro400();
            }

            if (!sala.estaAtiva()) {
                return resposta("409", "Sala indisponivel para reserva");
            }
            if (!sala.funcionaNoPeriodo(periodo.getDia(), periodo.getAulas())) {
                return resposta("409", "Horario fora do funcionamento da sala");
            }
            if (ReservaDAO.temConflito(salaId, periodo.getDia(),
                    periodo.getInicio(), periodo.getFim(), null)) {
                return resposta("409", "Sala ja reservada no periodo solicitado");
            }

            int id = ReservaDAO.criar(salaId, autor.getId(), periodo.getDia(),
                    periodo.getInicio(), periodo.getFim(), assuntoFinal, grupo);

            JsonObject resposta = resposta("201", "Reserva realizada com sucesso");
            resposta.addProperty("reservation_id", String.valueOf(id));
            return resposta;
        });
    }
}
