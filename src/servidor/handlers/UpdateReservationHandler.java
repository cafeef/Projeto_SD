package servidor.handlers;

import com.google.gson.JsonObject;

import protocolo.CampoInvalidoException;
import protocolo.Horarios;
import protocolo.Validador;
import servidor.TrancaDeSala;
import servidor.dao.ReservaDAO;
import servidor.dao.SalaDAO;
import servidor.modelo.Reserva;
import servidor.modelo.Sala;
import servidor.modelo.Usuario;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * update_reservation -- altera uma reserva. Item e/m da EP-3.
 *
 * Todos os campos sao "" para manter (regra 2.11). As seis chaves alteraveis:
 *
 *   room_id, date .......... opcionais; ausentes ou "" mantem (regra 9.10)
 *   start_time, end_time ... obrigatorias como chave, "" mantem
 *   topic, participants .... idem
 *
 * O 'room_id' e o 'date' entraram na v2.10. Ate a v2.9.2 eles respondiam 400 e
 * trocar de sala ou de dia era cancelar e criar outra reserva -- o que perdia o
 * id e, se a sala nova ja tivesse dono naquele horario, deixava a pessoa sem
 * reserva nenhuma. Por serem OPCIONAIS, um cliente da v2.9.x, que nunca manda
 * essas chaves, continua funcionando sem mudar uma linha.
 *
 * So o DONO altera, nem o admin (regra 9.9). O admin cancela reserva de
 * terceiro, mas nao altera: liberar uma sala e administracao, mudar o assunto
 * ou o tamanho do grupo de alguem e se passar por essa pessoa.
 *
 * A ordem das respostas segue a secao 4:
 *
 *   400 formato ........ campos, e "nada para atualizar" (regra 2.13)
 *   404 reserva ........ primeiro a reserva...
 *   404 sala ........... ...depois a sala de destino (regra 9.10)
 *   403 ................ nao e o dono (regra 9.9)
 *   409 encerrada ...... a reserva ja terminou
 *   400 do registro .... participants acima da capacity da sala DE DESTINO;
 *                        horario resultante invertido ou no passado (9.3)
 *   409 ................ destino inactive -> fora do funcionamento -> ocupado
 *   200
 *
 * Tudo o que se checa e o estado RESULTANTE -- o que veio na requisicao
 * combinado com o que ja estava gravado. Checar so o que veio deixaria passar
 * uma reserva que, depois de alterada, fica invalida: mudar so o 'end_time'
 * para antes do 'start_time' antigo, por exemplo.
 */
public class UpdateReservationHandler extends HandlerAutenticado {

    public UpdateReservationHandler() {
        super("update_reservation", "Dados da reserva em formato invalido");
    }

    @Override
    protected JsonObject tratarAutenticado(JsonObject requisicao, Usuario autor, String token)
            throws Exception {
        String reservationId;
        String novaSala;
        String novoDia;
        String novoInicio;
        String novoFim;
        String novoAssunto;
        String novosParticipantes;
        try {
            reservationId = Validador.obrigatorio(requisicao, "reservation_id");
            // Regra 9.10: opcionais. A chave ausente e "" dizem a mesma coisa.
            novaSala = Validador.opcionalAtualizacao(requisicao, "room_id");
            novoDia = Validador.opcionalAtualizacao(requisicao, "date");
            // Obrigatorias como chave, mesmo que vazias -- como no update_room.
            novoInicio = Validador.opcionalAtualizacao(requisicao, "start_time");
            novoFim = Validador.opcionalAtualizacao(requisicao, "end_time");
            novoAssunto = Validador.opcionalAtualizacao(requisicao, "topic");
            novosParticipantes = Validador.opcionalAtualizacao(requisicao, "participants");
        } catch (CampoInvalidoException e) {
            return erro400();
        }

        if (novoInicio == null || novoFim == null
                || novoAssunto == null || novosParticipantes == null) {
            return erro400();
        }

        // Regra 2.13: nada preenchido e o 200 seria mentira. Vem depois dos
        // 400 de formato e antes de tudo o mais (secao 4, passo 2).
        if (vazio(novaSala) && vazio(novoDia) && novoInicio.isEmpty() && novoFim.isEmpty()
                && novoAssunto.isEmpty() && novosParticipantes.isEmpty()) {
            return resposta("400", "Nenhum dado para atualizar");
        }

        final int id = Integer.parseInt(reservationId);
        Reserva reserva = ReservaDAO.buscarPorId(id);
        if (reserva == null) {
            return resposta("404", "Reserva nao encontrada");
        }

        // A sala de destino: a nova, se veio; a atual, se nao.
        final int salaDestinoId = vazio(novaSala)
                ? reserva.getSalaId()
                : Integer.parseInt(novaSala);

        // Regra 9.10: o 404 da sala vem DEPOIS do 404 da reserva, e antes do
        // 403 de dono -- quem nao e dono nem chega a descobrir se a sala
        // existe, mas quem erra o id da sala e avisado disso, e nao do resto.
        Sala destino = SalaDAO.buscarPorId(salaDestinoId);
        if (destino == null) {
            return resposta("404", "Sala nao encontrada");
        }

        // Regra 9.9: so o dono. O admin nao entra aqui nem com a whitelist.
        if (!reserva.ehDe(autor.getId())) {
            return resposta("403", "Permissao insuficiente");
        }
        if (reserva.estaEncerrada()) {
            return resposta("409", "Reserva ja encerrada");
        }

        // --- o estado resultante, campo a campo ---
        LocalDate dia;
        LocalTime inicio;
        LocalTime fim;
        try {
            dia = vazio(novoDia) ? reserva.getDia() : LocalDate.parse(novoDia);   // regra 9.2
            inicio = novoInicio.isEmpty() ? reserva.getInicio() : LocalTime.parse(novoInicio);
            fim = novoFim.isEmpty() ? reserva.getFim() : LocalTime.parse(novoFim);
        } catch (DateTimeParseException e) {
            return erro400();
        }
        String assunto = novoAssunto.isEmpty() ? reserva.getAssunto() : novoAssunto;
        int participantes = novosParticipantes.isEmpty()
                ? reserva.getParticipantes()
                : Integer.parseInt(novosParticipantes);

        // Passo 8 da secao 4: os 400 que so da para julgar com o registro na
        // mao. A capacidade e a da sala DE DESTINO, nao a da sala atual.
        if (participantes > destino.getCapacidade()) {
            return erro400();
        }
        if (!fim.isAfter(inicio)) {                                  // regra 9.11
            return erro400();
        }
        if (!Horarios.ehInicioDeAula(inicio) || !Horarios.ehFimDeAula(fim)) {   // regra 8.3
            return erro400();
        }
        // Regra 9.3: nem o inicio nem o FIM resultantes podem estar no passado.
        // O fim entrou na v2.10 porque alterar nao e encerrar: jogar o fim para
        // tras do relogio seria usar o update para apagar o que ja aconteceu, e
        // para encerrar existe o delete_reservation. Encurtar ate o fim de uma
        // aula que ainda nao terminou continua valendo, e e assim que o usuario
        // "libera a sala" sem cancelar a reserva inteira.
        LocalDateTime agora = LocalDateTime.now();
        if (!dia.atTime(inicio).isAfter(agora) || !dia.atTime(fim).isAfter(agora)) {
            return erro400();
        }

        // Mexer so no assunto ou no numero de participantes nao reabre as
        // perguntas de agenda: a sala e o horario continuam os mesmos, e
        // recheca-los recusaria uma alteracao inocente so porque a sala virou
        // "inactive" ou teve os horarios mudados depois da reserva feita.
        //
        // O que conta e a chave ter vindo PREENCHIDA, e nao o valor ter ficado
        // diferente: pela regra 2.15, mandar o valor que a reserva ja tem e
        // uma alteracao como outra qualquer. Mandar "end_time":"14:40" numa
        // reserva que ja termina as 14:40, com a sala "inactive", responde 409
        // -- quem pede para mexer no horario de uma sala parada recebe o nao,
        // mesmo que o horario pedido por acaso coincida com o atual.
        boolean mudouAgenda = !vazio(novaSala) || !vazio(novoDia)
                || !novoInicio.isEmpty() || !novoFim.isEmpty();

        final LocalDate diaFinal = dia;
        final LocalTime inicioFinal = inicio;
        final LocalTime fimFinal = fim;
        final String assuntoFinal = assunto;
        final int participantesFinal = participantes;
        final Sala destinoFinal = destino;

        // Regra 5.6: com troca de sala, as DUAS trancas -- a origem precisa
        // ficar parada enquanto o horario dela e liberado, senao alguem poderia
        // reservar na origem um horario que ainda parece ocupado, ou achar
        // ocupado um horario que ja saiu.
        return TrancaDeSala.comAsSalasTrancadas(reserva.getSalaId(), salaDestinoId, () -> {
            if (mudouAgenda) {
                if (!destinoFinal.estaAtiva()) {                       // regra 9.5
                    return resposta("409", "Sala indisponivel para reserva");
                }
                List<String> aulas = Horarios.aulasEntre(inicioFinal, fimFinal);
                if (!destinoFinal.funcionaNoPeriodo(diaFinal, aulas)) {   // regra 8.4
                    return resposta("409", "Horario fora do funcionamento da sala");
                }
                // Regra 9.13: a propria reserva fica FORA da comparacao. Sem
                // isso, esticar 13:00-13:50 para 13:00-14:40 colidiria com ela
                // mesma e responderia 409 -- o furo que a v2.10 fechou.
                if (ReservaDAO.temConflito(salaDestinoId, diaFinal, inicioFinal, fimFinal, id)) {
                    return resposta("409", "Sala ja reservada no periodo solicitado");
                }
            }

            if (!ReservaDAO.atualizar(id, salaDestinoId, diaFinal, inicioFinal, fimFinal,
                    assuntoFinal, participantesFinal)) {
                // Cancelada por outra conexao entre a leitura e o UPDATE.
                return resposta("404", "Reserva nao encontrada");
            }
            return resposta("200", "Reserva atualizada com sucesso");
        });
    }

    /** Chave ausente (null) ou vazia: as duas dizem "nao alterar" (regra 9.10). */
    private static boolean vazio(String valor) {
        return valor == null || valor.isEmpty();
    }
}
