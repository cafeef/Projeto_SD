package servidor.handlers;

import com.google.gson.JsonObject;

import protocolo.CampoInvalidoException;
import protocolo.Validador;
import servidor.TrancaDeSala;
import servidor.dao.DadoDuplicadoException;
import servidor.dao.SalaDAO;
import servidor.modelo.Usuario;

import java.util.List;

/**
 * update_room -- altera uma sala. Exige admin (regra 3.7).
 *
 * O 'room_id' identifica a sala e nunca muda. Os outros campos seguem a regra
 * 2.11: "" mantem o valor atual. Mandar o valor que a sala ja tem e alteracao
 * valida, e responde 200 (regra 2.15).
 *
 * As duas listas funcionam igual desde a v2.9 (regras 8.7 e 9.7):
 *
 *   ausente ou []  nao altera
 *   lista nova     substitui a antiga inteira
 *   ["0"]          apaga todos os recursos (so o 'resources' tem isso)
 *
 * O 'resources' precisou do sentinela "0" porque a lista vazia ja estava
 * ocupada significando "nao alterar", e sem ele nao haveria como dizer "esta
 * sala nao tem mais recurso nenhum". O 'slots' nao precisa do equivalente:
 * sala sem horario nenhum nao e um estado valido.
 *
 * Como no admin_update_user, as quatro chaves de texto ainda sao OBRIGATORIAS:
 * o protocolo as marca como required e "" ja e a forma de dizer "nao alterar",
 * entao a chave ausente responde 400.
 *
 * A leitura das reservas ativas e a gravacao acontecem com a sala trancada
 * (regra 5.5): sem isso, uma reserva nova podia entrar entre a checagem da
 * capacidade e o UPDATE, e a sala terminaria menor que um grupo ja marcado.
 */
public class UpdateRoomHandler extends HandlerAdmin {

    public UpdateRoomHandler() {
        super("update_room", "Dados da sala em formato invalido");
    }

    @Override
    protected JsonObject tratarComoAdmin(JsonObject requisicao, Usuario autor, String token)
            throws Exception {
        String roomId;
        String nome;
        String capacidade;
        String localizacao;
        String estado;
        List<String> recursos;
        List<String> slots;
        try {
            roomId = Validador.obrigatorio(requisicao, "room_id");
            nome = Validador.opcionalAtualizacao(requisicao, "name");
            capacidade = Validador.opcionalAtualizacao(requisicao, "capacity");
            localizacao = Validador.opcionalAtualizacao(requisicao, "location");
            estado = Validador.opcionalAtualizacao(requisicao, "room_status");
            // Regra 9.7: aqui "0" sozinho e um pedido valido -- apagar tudo.
            recursos = requisicao.has("resources")
                    ? Validador.listaDeRecursos(requisicao, true)
                    : List.of();
            // Regra 8.7: lista vazia quer dizer "nao alterar".
            slots = requisicao.has("slots")
                    ? Validador.listaDeSlots(requisicao, false)
                    : List.of();
        } catch (CampoInvalidoException e) {
            return erro400();
        }

        // Chave ausente (null) e 400; "" segue adiante como "nao alterar".
        if (nome == null || capacidade == null || localizacao == null || estado == null) {
            return erro400();
        }

        // Regra 2.13: nada preenchido e nenhuma lista nova -- o 200 seria
        // mentira, porque nada mudaria. Vem depois dos 400 de formato e antes
        // de tudo o mais (secao 4, passo 2).
        if (nome.isEmpty() && capacidade.isEmpty() && localizacao.isEmpty() && estado.isEmpty()
                && recursos.isEmpty() && slots.isEmpty()) {
            return resposta("400", "Nenhum dado para atualizar");
        }

        // Daqui para baixo, null passa a significar "nao alterar" para o DAO.
        nome = vazioParaNulo(nome);
        capacidade = vazioParaNulo(capacidade);
        localizacao = vazioParaNulo(localizacao);
        estado = vazioParaNulo(estado);

        // A lista vazia e "nao alterar" (null para o DAO); ["0"] vira a lista
        // vazia de verdade, que o DAO grava apagando os recursos.
        final List<String> recursosParaGravar = recursos.isEmpty()
                ? null
                : (Validador.apagaTodosOsRecursos(recursos) ? List.of() : recursos);

        final int id = Integer.parseInt(roomId);
        final String nomeFinal = nome;
        final String capacidadeFinal = capacidade;
        final String localizacaoFinal = localizacao;
        final String estadoFinal = estado;
        final List<String> slotsFinal = slots;

        return TrancaDeSala.comASalaTrancada(id, () -> {
            if (SalaDAO.buscarPorId(id) == null) {
                return resposta("404", "Sala nao encontrada");
            }

            // Secao 4, passo 9: entre os dois 409 do update_room, o nome
            // duplicado vem antes da capacidade.
            if (nomeFinal != null && SalaDAO.existeNomeDeOutraSala(id, nomeFinal)) {
                return resposta("409", "Sala ja cadastrada");
            }

            // Regra 9.6: encolher a sala abaixo de um grupo ja marcado deixaria
            // a reserva impossivel de honrar, e ninguem avisaria o dono dela.
            if (capacidadeFinal != null
                    && Integer.parseInt(capacidadeFinal) < SalaDAO.maiorGrupoComReservaAtiva(id)) {
                return resposta("409", "Capacidade menor que participantes de reservas ativas");
            }

            try {
                if (!SalaDAO.atualizar(id, nomeFinal,
                        capacidadeFinal == null ? null : Integer.valueOf(capacidadeFinal),
                        localizacaoFinal, recursosParaGravar, estadoFinal, slotsFinal)) {
                    // A sala existia no SELECT acima e sumiu antes do UPDATE.
                    return resposta("404", "Sala nao encontrada");
                }
            } catch (DadoDuplicadoException e) {
                // A tranca ja evita a corrida entre dois update_room, mas o
                // indice unico continua sendo a ultima palavra.
                return resposta("409", "Sala ja cadastrada");
            }

            return resposta("200", "Sala atualizada com sucesso");
        });
    }

    private static String vazioParaNulo(String valor) {
        return valor.isEmpty() ? null : valor;
    }
}
