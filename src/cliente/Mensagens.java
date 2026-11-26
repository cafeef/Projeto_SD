package cliente;

import java.util.Map;

/**
 * Traducao das mensagens do protocolo para exibicao na tela.
 *
 * A regra 2.8 obriga 'message' a viajar em portugues SEM acentos, com o texto
 * exato das abas de mensagens -- e assim que os grupos comparam as respostas
 * entre si. Mudar o texto no fio quebraria a interoperabilidade.
 *
 * Aqui a traducao acontece so na hora de mostrar: o JSON continua exato no
 * terminal e na rede, e o usuario le portugues correto na janela.
 *
 * Mensagem desconhecida -- de um servidor de outro grupo, por exemplo -- e
 * exibida como veio, sem traducao.
 */
final class Mensagens {

    private static final Map<String, String> ACENTUADAS = Map.ofEntries(
            // register
            Map.entry("Usuario cadastrado com sucesso", "Usuário cadastrado com sucesso."),
            Map.entry("Dados de cadastro em formato invalido",
                    "Dados de cadastro em formato inválido."),
            Map.entry("Usuario ou email ja cadastrado", "Usuário ou e-mail já cadastrado."),
            // login
            Map.entry("Login realizado com sucesso", "Login realizado com sucesso."),
            Map.entry("Email ou senha em formato invalido", "E-mail ou senha em formato inválido."),
            Map.entry("Email ou senha incorretos", "E-mail ou senha incorretos."),
            Map.entry("Usuario ja possui sessao ativa", "Este usuário já possui uma sessão ativa."),
            // logout
            Map.entry("Logout realizado com sucesso", "Logout realizado com sucesso."),
            Map.entry("Token em formato invalido", "Token em formato inválido."),
            Map.entry("Token invalido ou expirado", "Sessão inválida ou expirada."),
            // read_user
            Map.entry("Consulta realizada com sucesso", "Consulta realizada com sucesso."),
            // update_user
            Map.entry("Dados em formato invalido", "Dados em formato inválido."),
            Map.entry("Dados atualizados com sucesso", "Dados atualizados com sucesso."),
            Map.entry("Usuario ja esta em uso", "Este nome de usuário já está em uso."),
            // delete_user
            Map.entry("Senha em formato invalido", "Senha em formato inválido."),
            Map.entry("Usuario removido com sucesso", "Usuário removido com sucesso."),
            Map.entry("Nao e possivel remover o ultimo administrador",
                    "Não é possível remover o último administrador."),
            Map.entry("Senha incorreta", "Senha incorreta."),
            // operacoes de administrador (EP-2)
            Map.entry("Permissao insuficiente",
                    "Esta operação é restrita a administradores."),
            Map.entry("Usuario nao encontrado", "Usuário não encontrado."),
            Map.entry("Nenhum dado para atualizar", "Nenhum dado para atualizar."),
            // salas (EP-2)
            Map.entry("Sala cadastrada com sucesso", "Sala cadastrada com sucesso."),
            Map.entry("Dados da sala em formato invalido",
                    "Dados da sala em formato inválido."),
            Map.entry("Sala ja cadastrada", "Já existe uma sala com esse nome."),
            Map.entry("Filtros em formato invalido", "Filtros em formato inválido."),
            Map.entry("Sala nao encontrada", "Sala não encontrada."),
            Map.entry("Sala atualizada com sucesso", "Sala atualizada com sucesso."),
            Map.entry("Sala removida com sucesso", "Sala removida com sucesso."),
            Map.entry("Sala possui reservas ativas", "Esta sala possui reservas ativas."),
            // Regra 9.6: encolher a sala abaixo de um grupo ja marcado.
            Map.entry("Capacidade menor que participantes de reservas ativas",
                    "A capacidade é menor que o número de participantes de uma reserva ativa."),
            // --- reservas (EP-3) ---
            Map.entry("Reserva realizada com sucesso", "Reserva realizada com sucesso."),
            Map.entry("Reserva cancelada com sucesso", "Reserva cancelada com sucesso."),
            Map.entry("Reserva atualizada com sucesso", "Reserva atualizada com sucesso."),
            Map.entry("Reserva nao encontrada", "Reserva não encontrada."),
            Map.entry("Dados da reserva em formato invalido",
                    "Dados da reserva em formato inválido."),
            Map.entry("Sala ja reservada no periodo solicitado",
                    "Esta sala já está reservada nesse horário."),
            Map.entry("Sala indisponivel para reserva",
                    "Esta sala está inativa e não aceita reservas."),
            Map.entry("Horario fora do funcionamento da sala",
                    "A sala não funciona nesse horário."),
            Map.entry("Reserva ja encerrada", "Esta reserva já terminou."),
            // erros de protocolo (secao 4)
            Map.entry("Requisicao invalida", "Requisição inválida."),
            Map.entry("Operacao desconhecida", "Operação desconhecida."),
            Map.entry("Mensagem excede o tamanho maximo", "A mensagem excede o tamanho máximo."),
            Map.entry("Erro interno do servidor", "Erro interno do servidor."));

    private Mensagens() {
    }

    static String paraExibicao(String mensagemDoProtocolo) {
        if (mensagemDoProtocolo == null) {
            return "O servidor respondeu sem mensagem.";
        }
        return ACENTUADAS.getOrDefault(mensagemDoProtocolo, mensagemDoProtocolo);
    }

    /** Orientacao extra para os casos em que o proximo passo nao e obvio. */
    static String dica(String status, String op) {
        if ("409".equals(status) && "login_response".equals(op)) {
            return "Faça logout na outra sessão ou aguarde 30 minutos de inatividade.";
        }
        if ("401".equals(status) && "delete_user_response".equals(op)) {
            return "Confira a senha digitada.";
        }
        if ("403".equals(status) && "delete_user_response".equals(op)) {
            return "Confira a senha digitada. Sua sessão continua ativa.";
        }
        if ("409".equals(status) && "delete_room_response".equals(op)) {
            return "Para tirá-la de circulação sem apagar as reservas,"
                    + " altere o estado da sala para \"inactive\".";
        }
        if ("400".equals(status) && "list_rooms_response".equals(op)) {
            // Sem hora, "a sala está livre no dia 30?" não tem resposta única:
            // ela pode estar livre de manhã e ocupada à tarde. Quem responde
            // pelo dia inteiro é o read_room sem room_id (regra 9.16).
            return "Data, início e fim precisam ser informados juntos, ou nenhum dos três."
                    + "\nPara ver o que está livre num dia, use a aba Salas:"
                    + " escolha o dia e clique em \"Ver o dia\".";
        }
        if ("400".equals(status) && "check_availability_response".equals(op)) {
            return "Informe a sala, a data, o início e o fim."
                    + "\nPara ver um dia inteiro sem escolher sala nem horário,"
                    + " use a grade da aba Salas.";
        }
        if ("400".equals(status) && "create_reservation_response".equals(op)) {
            return "Confira a data, os horários de aula e se o grupo cabe na sala."
                    + " Reservas só valem para horários futuros.";
        }
        if ("409".equals(status) && "create_reservation_response".equals(op)) {
            return "Consulte a disponibilidade e tente outro horário ou outra sala.";
        }
        if ("403".equals(status) && "list_reservations_response".equals(op)) {
            return "Ver as reservas de todos é uma operação de administrador.";
        }
        if ("403".equals(status) && op != null && op.startsWith("admin_")) {
            return "Seu perfil pode ter sido alterado. Recarregue seus dados.";
        }
        return null;
    }
}
