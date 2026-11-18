package protocolo;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Validacao de formato dos campos, conforme a aba "Dicionario" do protocolo.
 *
 * Regras aplicadas aqui:
 *
 *  2.3  Todos os valores sao STRINGS -- numero, booleano ou objeto resulta em 400
 *  2.10 Campo obrigatorio ausente, null ou de tipo diferente de string -> 400
 *  2.11 String vazia ("") em atualizacao significa "nao alterar";
 *       em criacao e invalida -> 400
 *  2.12 Nomes de chaves sao case-sensitive
 *
 * A ordem de validacao adotada e formato primeiro: um campo reprovado aqui
 * responde 400 ANTES de qualquer consulta a credencial ou a sessao, que
 * responderiam 401. Essa ordem nao esta enunciada como regra geral, mas e a que
 * a propria planilha implica ao definir mensagens distintas para os dois casos
 * ("Token em formato invalido" com 400 e "Token invalido ou expirado" com 401).
 */
public final class Validador {

    private static final Map<String, Pattern> REGEX;

    static {
        Map<String, Pattern> m = new LinkedHashMap<>();
        // --- Campos de protocolo ---
        m.put("op",             Pattern.compile("^[a-z_]{3,30}$"));
        m.put("status",         Pattern.compile("^[0-9]{3}$"));
        m.put("token",          Pattern.compile("^[a-f0-9]{64}$"));
        // --- Usuario ---
        m.put("user",           Pattern.compile("^[a-z]{1,30}$"));
        m.put("password",       Pattern.compile("^[A-Za-z0-9]{1,20}$"));
        m.put("email",          Pattern.compile("^[a-z0-9._-]+@[a-z0-9-]+(\\.[a-z0-9-]+)*\\.[a-z]{2,}$"));
        m.put("role",           Pattern.compile("^(user|admin)$"));
        m.put("target_user",    Pattern.compile("^[a-z]{1,30}$"));
        // --- Salas ---
        m.put("room_id",        Pattern.compile("^[0-9]{1,10}$"));
        m.put("name",           Pattern.compile("^[A-Za-z0-9 -]{1,30}$"));
        m.put("capacity",       Pattern.compile("^[1-9][0-9]{0,3}$"));
        m.put("location",       Pattern.compile("^[A-Za-z0-9 ,.-]{1,60}$"));
        m.put("room_status",    Pattern.compile("^(active|inactive)$"));
        m.put("min_capacity",   Pattern.compile("^[1-9][0-9]{0,3}$"));
        // Recurso de sala (regra 9.7, v2.9). O item "0" e reservado: sozinho na
        // lista ele APAGA todos os recursos, entao nunca e um recurso de verdade.
        m.put("recurso",        Pattern.compile("^[A-Za-z0-9 -]{1,30}$"));
        // Horario da grade da UTFPR: dia + turno + aula (regra 8.1).
        m.put("slot",           Pattern.compile("^[2-7](M[1-6]|T[1-6]|N[1-5])$"));
        // --- Reservas ---
        m.put("reservation_id", Pattern.compile("^[0-9]{1,10}$"));
        m.put("date",           Pattern.compile("^[0-9]{4}-[0-9]{2}-[0-9]{2}$"));
        m.put("start_time",     Pattern.compile("^([01][0-9]|2[0-3]):[0-5][0-9]$"));
        m.put("end_time",       Pattern.compile("^([01][0-9]|2[0-3]):[0-5][0-9]$"));
        m.put("topic",          Pattern.compile("^[A-Za-z0-9 ,.-]{1,60}$"));
        m.put("participants",   Pattern.compile("^[1-9][0-9]{0,3}$"));
        m.put("available",      Pattern.compile("^(true|false)$"));
        // 'scope' nao consta na aba Dicionario; o formato vem das observacoes da
        // aba "Msg Reservas" ('scope' aceita "mine" e "all").
        m.put("scope",          Pattern.compile("^(mine|all)$"));
        // --- Campos de resposta ---
        m.put("count",          Pattern.compile("^[0-9]{1,10}$"));
        m.put("created_at",     Pattern.compile(
                "^[0-9]{4}-[0-9]{2}-[0-9]{2} ([01][0-9]|2[0-3]):[0-5][0-9]:[0-5][0-9]$"));
        REGEX = Collections.unmodifiableMap(m);
        // Sem regex de proposito: 'message' e texto livre e 'resources' e um
        // array de strings, validado por listaDeTextos() mais abaixo.
    }

    private Validador() {
    }

    /** Diz se o campo tem regex definida na aba Dicionario. */
    public static boolean temRegex(String campo) {
        return REGEX.containsKey(campo);
    }

    /**
     * Testa um valor contra a regex do campo.
     *
     * @throws IllegalArgumentException se o campo nao existe no dicionario --
     *         isso indica erro de digitacao no nosso proprio codigo, nao dado
     *         invalido do cliente, e por isso falha alto em vez de passar batido
     */
    public static boolean formatoValido(String campo, String valor) {
        Pattern p = REGEX.get(campo);
        if (p == null) {
            throw new IllegalArgumentException("campo sem regex no dicionario: " + campo);
        }
        return valor != null && p.matcher(valor).matches();
    }

    /**
     * Le um campo obrigatorio: precisa existir, ser string, nao ser vazio e
     * casar com a regex (regras 2.10 e 2.11).
     */
    public static String obrigatorio(JsonObject requisicao, String campo)
            throws CampoInvalidoException {
        String valor = extrairTexto(requisicao, campo, true);
        if (valor.isEmpty()) {
            // Regra 2.11: vazio em criacao/identificacao e invalido.
            throw new CampoInvalidoException(campo, CampoInvalidoException.Motivo.VAZIO);
        }
        if (!formatoValido(campo, valor)) {
            throw new CampoInvalidoException(campo, CampoInvalidoException.Motivo.FORMATO_INVALIDO);
        }
        return valor;
    }

    /**
     * Le um campo de requisicao de atualizacao (regra 2.11).
     *
     * @return {@code null} quando a chave nao veio (nada a alterar),
     *         {@code ""} quando veio vazia (explicitamente "nao alterar"),
     *         ou o valor validado
     * @throws CampoInvalidoException se a chave veio com null, com tipo
     *         diferente de string ou com formato reprovado
     */
    public static String opcionalAtualizacao(JsonObject requisicao, String campo)
            throws CampoInvalidoException {
        if (!requisicao.has(campo)) {
            return null;
        }
        String valor = extrairTexto(requisicao, campo, true);
        if (valor.isEmpty()) {
            return "";
        }
        if (!formatoValido(campo, valor)) {
            throw new CampoInvalidoException(campo, CampoInvalidoException.Motivo.FORMATO_INVALIDO);
        }
        return valor;
    }

    /**
     * Le um filtro opcional de listagem (regra 2.14, v2.9.1).
     *
     * Ausente = sem filtro. Mas o filtro ENVIADO precisa ser valido: "" e null
     * respondem 400, e nao "sem filtro". A diferenca importa porque o vazio
     * aqui nao tem o sentido que tem numa atualizacao -- numa listagem nao ha
     * valor anterior para "manter". Quem nao quer filtrar omite a chave, e e o
     * que o nosso cliente faz.
     *
     * @return o filtro validado, ou {@code null} quando a chave nao veio
     */
    public static String filtroOpcional(JsonObject requisicao, String campo)
            throws CampoInvalidoException {
        if (!requisicao.has(campo)) {
            return null;
        }
        return obrigatorio(requisicao, campo);
    }

    /**
     * Le um campo que e um ARRAY de strings, sem olhar o formato dos itens.
     *
     * Base de listaDeRecursos e listaDeSlots, que acrescentam cada uma as suas
     * regras. O array vazio e valido aqui: o que ele SIGNIFICA depende da
     * operacao, e quem decide isso e o handler.
     *
     * @return a lista na ordem enviada; vazia se o array veio vazio
     * @throws CampoInvalidoException se a chave falta, veio null, nao e array
     *         ou tem algum item que nao e string
     */
    public static List<String> listaDeTextos(JsonObject requisicao, String campo)
            throws CampoInvalidoException {
        if (requisicao == null || !requisicao.has(campo)) {
            throw new CampoInvalidoException(campo, CampoInvalidoException.Motivo.AUSENTE);
        }
        JsonElement elemento = requisicao.get(campo);
        if (elemento.isJsonNull()) {
            throw new CampoInvalidoException(campo, CampoInvalidoException.Motivo.NULO);
        }
        if (!elemento.isJsonArray()) {
            throw new CampoInvalidoException(campo, CampoInvalidoException.Motivo.TIPO_INVALIDO);
        }
        List<String> itens = new ArrayList<>();
        for (JsonElement item : elemento.getAsJsonArray()) {
            if (!item.isJsonPrimitive() || !item.getAsJsonPrimitive().isString()) {
                // Regra 2.4: os valores dentro da lista tambem sao strings.
                throw new CampoInvalidoException(campo,
                        CampoInvalidoException.Motivo.TIPO_INVALIDO);
            }
            itens.add(item.getAsString());
        }
        return itens;
    }

    /** Regra 9.7: o unico item de 'resources' que nao e um recurso. */
    public static final String APAGAR_RECURSOS = "0";

    /**
     * Diz se a lista e o pedido de apagar todos os recursos: {@code ["0"]}.
     *
     * So vale sozinho. {@code ["0", "tv"]} nao apaga nada -- e 400, porque "0"
     * nao e nome de recurso valido.
     */
    public static boolean apagaTodosOsRecursos(List<String> recursos) {
        return recursos.size() == 1 && APAGAR_RECURSOS.equals(recursos.get(0));
    }

    /**
     * Le o 'resources' de uma sala (regra 9.7, v2.9).
     *
     * Cada recurso segue a mesma regex do nome da sala, e "0" nunca e um
     * recurso: ele e o sentinela que apaga a lista inteira no update_room.
     * Como "0" PASSA na regex, a recusa dele precisa ser explicita -- sem
     * isso, cadastrar um recurso chamado "0" criaria uma sala que, na proxima
     * alteracao, nao teria como ser descrita sem apagar tudo.
     *
     * @param aceitaApagarTudo true no update_room, onde {@code ["0"]} e um
     *                         pedido valido; false no create_room, onde apagar
     *                         o que ainda nao existe nao quer dizer nada
     * @throws CampoInvalidoException se a chave falta, nao e array, ou tem item
     *         fora do formato
     */
    public static List<String> listaDeRecursos(JsonObject requisicao, boolean aceitaApagarTudo)
            throws CampoInvalidoException {
        List<String> recursos = listaDeTextos(requisicao, "resources");
        if (aceitaApagarTudo && apagaTodosOsRecursos(recursos)) {
            return recursos;
        }
        for (String recurso : recursos) {
            if (APAGAR_RECURSOS.equals(recurso) || !formatoValido("recurso", recurso)) {
                throw new CampoInvalidoException("resources",
                        CampoInvalidoException.Motivo.FORMATO_INVALIDO);
            }
        }
        return recursos;
    }

    /**
     * Le o 'slots' de uma sala: a lista de horarios em que ela funciona.
     *
     * Alem do formato de cada item, exige o que a regra 8.2 manda: pelo menos
     * um horario e nenhum repetido. Sala sem horario nenhum nao poderia receber
     * reserva alguma, e horario repetido faria a agenda mostrar a mesma aula
     * duas vezes.
     *
     * @param obrigatoria true no create_room; false no update_room, onde a
     *                    lista vazia significa "nao alterar" (regra 8.7)
     * @throws CampoInvalidoException se a chave falta (quando obrigatoria), nao
     *         e array, tem item fora do formato, repetido, ou a lista veio
     *         vazia numa operacao que exige pelo menos um
     */
    public static List<String> listaDeSlots(JsonObject requisicao, boolean obrigatoria)
            throws CampoInvalidoException {
        List<String> slots = listaDeTextos(requisicao, "slots");
        if (slots.isEmpty()) {
            if (obrigatoria) {
                throw new CampoInvalidoException("slots", CampoInvalidoException.Motivo.VAZIO);
            }
            return slots;
        }
        Set<String> vistos = new LinkedHashSet<>();
        for (String slot : slots) {
            if (!formatoValido("slot", slot)) {
                throw new CampoInvalidoException("slots",
                        CampoInvalidoException.Motivo.FORMATO_INVALIDO);
            }
            if (!vistos.add(slot)) {
                throw new CampoInvalidoException("slots",
                        CampoInvalidoException.Motivo.FORMATO_INVALIDO);
            }
        }
        return slots;
    }

    /**
     * Recusa uma chave que a operacao nao aceita. O caso concreto e o 'email'
     * no update_user: se a chave vier, responde 400 (RNF 5.c).
     */
    public static void proibido(JsonObject requisicao, String campo)
            throws CampoInvalidoException {
        if (requisicao.has(campo)) {
            throw new CampoInvalidoException(campo, CampoInvalidoException.Motivo.PROIBIDO);
        }
    }

    /** Extrai o valor como string, aplicando as regras 2.3 e 2.10. */
    private static String extrairTexto(JsonObject requisicao, String campo, boolean exigirPresenca)
            throws CampoInvalidoException {
        if (requisicao == null || !requisicao.has(campo)) {
            if (exigirPresenca) {
                throw new CampoInvalidoException(campo, CampoInvalidoException.Motivo.AUSENTE);
            }
            return null;
        }
        JsonElement elemento = requisicao.get(campo);
        if (elemento.isJsonNull()) {
            throw new CampoInvalidoException(campo, CampoInvalidoException.Motivo.NULO);
        }
        if (!elemento.isJsonPrimitive()) {
            // Objeto ou array onde se esperava string.
            throw new CampoInvalidoException(campo, CampoInvalidoException.Motivo.TIPO_INVALIDO);
        }
        JsonPrimitive primitivo = elemento.getAsJsonPrimitive();
        if (!primitivo.isString()) {
            // Regra 2.3: numero e booleano nao sao aceitos, mesmo que o valor
            // "pareca" certo. {"capacity": 12} e invalido; {"capacity": "12"} e valido.
            throw new CampoInvalidoException(campo, CampoInvalidoException.Motivo.TIPO_INVALIDO);
        }
        return primitivo.getAsString();
    }
}
