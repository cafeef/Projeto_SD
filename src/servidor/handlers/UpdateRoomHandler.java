package servidor.handlers;

import com.google.gson.JsonObject;

import protocolo.CampoInvalidoException;
import protocolo.Validador;
import servidor.dao.DadoDuplicadoException;
import servidor.dao.SalaDAO;
import servidor.modelo.Usuario;

import java.util.List;

/**
 * update_room -- altera uma sala. Exige admin (regra 3.7).
 *
 * O 'room_id' identifica a sala e nunca muda. Os outros campos seguem a regra
 * 2.11: "" mantem o valor atual.
 *
 * O 'slots' e o contrario do 'resources' em dois pontos: e opcional, e a lista
 * vazia significa "nao alterar" (regra 8.7). Faz sentido, porque a lista vazia
 * nao e um estado valido de sala -- uma sala sem horario nenhum nao receberia
 * reserva --, entao o vazio fica livre para significar outra coisa. Mudar os
 * horarios nao apaga as reservas ja feitas.
 *
 * O 'resources' e a excecao, e e excecao de proposito: ele vem como lista
 * COMPLETA, entao [] quer dizer "sala sem recurso nenhum", e nao "nao alterar".
 * Nao ha como pedir "mantenha os recursos" -- a requisicao sempre reescreve a
 * lista. E a consequencia de a lista nao ter como representar o vazio e a
 * ausencia ao mesmo tempo, ja que "" nao e valor de array.
 *
 * Como no admin_update_user, as quatro chaves opcionais ainda sao OBRIGATORIAS:
 * o protocolo as marca como required e "" ja e a forma de dizer "nao alterar",
 * entao a chave ausente responde 400.
 *
 * Nao implementado de proposito: o 409 por reduzir 'capacity' abaixo dos
 * 'participants' de uma reserva futura. O protocolo preve a situacao (questao
 * aberta 3) mas nao define a mensagem, e inventar uma faria o nosso 409 nao
 * casar com o dos outros grupos. Fica para quando a turma fechar o texto.
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
            recursos = Validador.listaDeTextos(requisicao, "resources");
            // Regra 8.7: aqui o 'slots' e opcional, e a lista VAZIA quer dizer
            // "nao alterar" -- diferente do 'resources', que nao tem como
            // dizer isso e por isso e sempre substituido.
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

        // Daqui para baixo, null passa a significar "nao alterar" para o DAO.
        nome = vazioParaNulo(nome);
        capacidade = vazioParaNulo(capacidade);
        localizacao = vazioParaNulo(localizacao);
        estado = vazioParaNulo(estado);

        int id = Integer.parseInt(roomId);
        if (SalaDAO.buscarPorId(id) == null) {
            return resposta("404", "Sala nao encontrada");
        }

        try {
            if (!SalaDAO.atualizar(id, nome,
                    capacidade == null ? null : Integer.valueOf(capacidade),
                    localizacao, recursos, estado, slots)) {
                // A sala existia no SELECT acima e sumiu antes do UPDATE: outro
                // administrador a removeu no meio. O 404 e a resposta honesta.
                return resposta("404", "Sala nao encontrada");
            }
        } catch (DadoDuplicadoException e) {
            return resposta("409", "Sala ja cadastrada");
        }

        return resposta("200", "Sala atualizada com sucesso");
    }

    private static String vazioParaNulo(String valor) {
        return valor.isEmpty() ? null : valor;
    }
}
