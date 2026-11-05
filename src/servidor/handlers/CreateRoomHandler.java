package servidor.handlers;

import com.google.gson.JsonObject;

import protocolo.CampoInvalidoException;
import protocolo.Validador;
import servidor.dao.DadoDuplicadoException;
import servidor.dao.SalaDAO;
import servidor.modelo.Usuario;

import java.util.List;

/**
 * create_room -- cadastra uma sala. Exige admin (regra 3.7).
 *
 * O 'room_id' e gerado pelo servidor e a sala nasce "active": o cliente nao
 * escolhe nenhum dos dois, e mandar 'room_id' ou 'room_status' aqui nao tem
 * efeito -- os campos simplesmente nao sao lidos.
 *
 * O 'slots' diz em que horarios da grade da UTFPR a sala funciona ("2M1" e
 * segunda, manha, primeira aula) e e obrigatorio (regra 8.2).
 *
 * Sobre a precedencia 403 x 400: um usuario comum mandando dados invalidos
 * recebe 403, porque a checagem de permissao vem antes da validacao dos dados.
 * O protocolo nao define quem ganha (e a questao aberta 6 do documento), e esta
 * ordem e a que a arquitetura ja impunha -- a permissao e da classe base,
 * comum a todas as operacoes de admin, e os dados sao especificos daqui.
 * Vale conferir com os outros grupos antes do teste de interoperabilidade.
 */
public class CreateRoomHandler extends HandlerAdmin {

    public CreateRoomHandler() {
        super("create_room", "Dados da sala em formato invalido");
    }

    @Override
    protected JsonObject tratarComoAdmin(JsonObject requisicao, Usuario autor, String token)
            throws Exception {
        String nome;
        String capacidade;
        String localizacao;
        List<String> recursos;
        List<String> slots;
        try {
            nome = Validador.obrigatorio(requisicao, "name");
            capacidade = Validador.obrigatorio(requisicao, "capacity");
            localizacao = Validador.obrigatorio(requisicao, "location");
            // Obrigatorio tambem: o protocolo pede a lista sempre, e a sala sem
            // recurso nenhum se declara com [], nao omitindo a chave.
            recursos = Validador.listaDeTextos(requisicao, "resources");
            // Regra 8.2: obrigatorio, com pelo menos um horario e sem repetir.
            // Sala sem horario nenhum nao poderia receber reserva alguma.
            slots = Validador.listaDeSlots(requisicao, true);
        } catch (CampoInvalidoException e) {
            return erro400();
        }

        int id;
        try {
            id = SalaDAO.criar(nome, Integer.parseInt(capacidade), localizacao, recursos, slots);
        } catch (DadoDuplicadoException e) {
            // O nome e unico no esquema, entao a deteccao resiste a dois
            // administradores cadastrando a mesma sala ao mesmo tempo: quem
            // perde a corrida esbarra no indice, e nao num SELECT desatualizado.
            return resposta("409", "Sala ja cadastrada");
        }

        JsonObject resposta = resposta("201", "Sala cadastrada com sucesso");
        resposta.addProperty("room_id", String.valueOf(id));
        return resposta;
    }
}
