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
            return "Data, início e fim precisam ser informados juntos, ou nenhum dos três.";
        }
        if ("403".equals(status) && op != null && op.startsWith("admin_")) {
            return "Seu perfil pode ter sido alterado. Recarregue seus dados.";
        }
        return null;
    }
}
