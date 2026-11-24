package servidor.handlers;

import com.google.gson.JsonObject;

import protocolo.CampoInvalidoException;
import protocolo.Validador;
import servidor.dao.ReservaDAO;
import servidor.dao.SalaDAO;
import servidor.modelo.Sala;
import servidor.modelo.Usuario;

/**
 * check_availability -- diz se uma sala esta livre num periodo. Qualquer
 * usuario logado pode perguntar (item b/j da EP-3).
 *
 * E INFORMATIVO (regra 5.2): o "true" de agora nao reserva nada e nao garante
 * o create_reservation seguinte. Entre a resposta e o pedido, outra pessoa
 * pode reservar. Quem garante e a tranca do create_reservation, e e por isso
 * que o 409 continua possivel depois de um "true" -- nao e inconsistencia, e
 * a diferenca entre consultar e reservar.
 *
 * Tres coisas tornam a sala indisponivel, e basta uma:
 *
 *   - estar "inactive", que nao aceita reserva nenhuma;
 *   - nao funcionar em alguma aula do periodo (regra 8.4);
 *   - ja ter reserva que colida com o periodo (regra 5.4).
 *
 * Data passada e aceita e respondida normalmente (regra 9.4): a pergunta
 * "aquela sala estava livre na terca passada?" tem resposta, e o protocolo so
 * proibe o passado na hora de CRIAR a reserva.
 */
public class CheckAvailabilityHandler extends HandlerAutenticado {

    public CheckAvailabilityHandler() {
        super("check_availability", "Dados em formato invalido");
    }

    @Override
    protected JsonObject tratarAutenticado(JsonObject requisicao, Usuario autor, String token)
            throws Exception {
        String roomId;
        PeriodoPedido periodo;
        try {
            roomId = Validador.obrigatorio(requisicao, "room_id");
            periodo = PeriodoPedido.ler(requisicao);
        } catch (CampoInvalidoException e) {
            return erro400();
        }

        Sala sala = SalaDAO.buscarPorId(Integer.parseInt(roomId));
        if (sala == null) {
            return resposta("404", "Sala nao encontrada");
        }

        boolean disponivel = sala.estaAtiva()
                && sala.funcionaNoPeriodo(periodo.getDia(), periodo.getAulas())
                && !ReservaDAO.temConflito(sala.getId(), periodo.getDia(),
                        periodo.getInicio(), periodo.getFim(), null);

        JsonObject resposta = resposta("200", "Consulta realizada com sucesso");
        resposta.addProperty("room_id", String.valueOf(sala.getId()));
        resposta.addProperty("date", periodo.getDia().toString());
        resposta.addProperty("start_time", periodo.getInicio().toString());
        resposta.addProperty("end_time", periodo.getFim().toString());
        // Regra 2.7: booleano como string.
        resposta.addProperty("available", disponivel ? "true" : "false");
        return resposta;
    }
}
