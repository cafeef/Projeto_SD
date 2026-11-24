package servidor.handlers;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import protocolo.CampoInvalidoException;
import protocolo.Validador;
import servidor.AutorizacaoAdmin;
import servidor.dao.ReservaDAO;
import servidor.modelo.Reserva;
import servidor.modelo.Usuario;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * list_reservations -- lista reservas. Item c/k da EP-3.
 *
 * Sem 'scope', vale "mine": o usuario ve as proprias reservas, e so elas. O
 * scope "all" e de admin (403 para os demais) -- sem isso, qualquer pessoa
 * leria a agenda inteira do campus, com assunto de reuniao e tudo.
 *
 * Os filtros 'date' e 'room_id' sao opcionais e se acumulam. Filtro enviado
 * vazio e 400, e nao "sem filtro" (regra 2.14).
 *
 * Sala inexistente no filtro NAO da 404: devolve 200 com count "0" (regra
 * 9.12). Faz sentido -- a pergunta "quais reservas existem para a sala 999?"
 * tem resposta, e a resposta e "nenhuma". O 404 e para a sala que a operacao
 * usa como alvo, nao para um recorte de listagem.
 *
 * As reservas canceladas nao aparecem; as encerradas aparecem, porque o
 * historico do usuario e parte do que o item c pede.
 */
public class ListReservationsHandler extends HandlerAutenticado {

    public ListReservationsHandler() {
        super("list_reservations", "Filtros em formato invalido");
    }

    @Override
    protected JsonObject tratarAutenticado(JsonObject requisicao, Usuario autor, String token)
            throws Exception {
        String escopo;
        String data;
        String roomId;
        LocalDate dia = null;
        try {
            escopo = Validador.filtroOpcional(requisicao, "scope");
            data = Validador.filtroOpcional(requisicao, "date");
            roomId = Validador.filtroOpcional(requisicao, "room_id");
            if (data != null) {
                dia = LocalDate.parse(data);   // regra 9.2
            }
        } catch (CampoInvalidoException | DateTimeParseException e) {
            return erro400();
        }

        // Passo 4 da secao 4: o 403 de perfil vem depois do formato e do 401,
        // e antes de qualquer consulta.
        boolean todas = "all".equals(escopo);
        if (todas && !AutorizacaoAdmin.temPoder(token)) {
            return resposta("403", "Permissao insuficiente");
        }

        List<Reserva> reservas = ReservaDAO.listar(
                todas ? null : autor.getId(),
                dia,
                roomId == null ? null : Integer.valueOf(roomId));

        JsonArray lista = new JsonArray();
        for (Reserva reserva : reservas) {
            lista.add(ReservaJson.de(reserva, false));
        }

        JsonObject resposta = resposta("200", "Consulta realizada com sucesso");
        resposta.addProperty("count", String.valueOf(reservas.size()));
        resposta.add("reservations", lista);
        return resposta;
    }
}
