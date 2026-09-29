import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import protocolo.CampoInvalidoException;
import protocolo.CampoInvalidoException.Motivo;
import protocolo.Validador;

/**
 * Verificacao das regex da aba "Dicionario" e das regras 2.3, 2.10 e 2.11.
 * Nao usa rede: e teste puro do Validador.
 */
public class VerificaValidador {

    static int ok = 0, falhas = 0;
    static final String TOKEN = "c0fc3c713f09a43384ac08f7d91fca430dcbc6466fff9284ce4571bdc2c8f9f9";

    public static void main(String[] args) {
        // --- user: sem numero, conforme decidido pela turma ---
        valido("user", "joao");
        valido("user", "mariaeduarda");
        invalido("user", "joao123");
        invalido("user", "Joao");
        invalido("user", "joao silva");
        invalido("user", "joão");
        invalido("user", "");

        // --- target_user: alinhado ao user ---
        valido("target_user", "maria");
        invalido("target_user", "maria456");

        // --- token ---
        valido("token", TOKEN);
        invalido("token", TOKEN.substring(0, 63));
        invalido("token", TOKEN.toUpperCase());

        // --- password: aceita digito de proposito ---
        valido("password", "senha123");
        valido("password", "S");
        invalido("password", "senha 123");
        invalido("password", "a".repeat(21));

        // --- email ---
        valido("email", "joao.silva@email.com");
        valido("email", "a@b.co");
        invalido("email", "JOAO@email.com");
        invalido("email", "joao@email");
        invalido("email", "joao silva@email.com");

        // --- outros campos ---
        valido("capacity", "12");
        invalido("capacity", "0");
        valido("date", "2026-09-15");
        valido("start_time", "23:59");
        invalido("start_time", "24:00");
        valido("available", "true");
        invalido("available", "True");
        valido("room_status", "inactive");
        valido("created_at", "2026-09-09 14:32:10");

        // --- regras 2.3 e 2.10 sobre o JSON ---
        motivo("numero onde se espera string (2.3)", "{\"capacity\":12}", "capacity", Motivo.TIPO_INVALIDO);
        motivo("booleano onde se espera string (2.3)", "{\"available\":true}", "available", Motivo.TIPO_INVALIDO);
        motivo("objeto onde se espera string", "{\"user\":{\"a\":\"b\"}}", "user", Motivo.TIPO_INVALIDO);
        motivo("null explicito (2.10)", "{\"user\":null}", "user", Motivo.NULO);
        motivo("chave ausente (2.10)", "{}", "user", Motivo.AUSENTE);
        motivo("vazio na criacao (2.11)", "{\"user\":\"\"}", "user", Motivo.VAZIO);
        motivo("formato reprovado pela regex", "{\"user\":\"joao123\"}", "user", Motivo.FORMATO_INVALIDO);

        // --- regra 2.11 na atualizacao ---
        atualizacao("ausente significa nada a alterar", "{}", "user", null);
        atualizacao("vazio significa nao alterar", "{\"user\":\"\"}", "user", "");
        atualizacao("valor valido passa", "{\"user\":\"novonome\"}", "user", "novonome");
        motivoAtualizacao("null na atualizacao ainda e 400", "{\"user\":null}", "user", Motivo.NULO);

        // --- email imutavel no update_user (RNF 5.c) ---
        try {
            Validador.proibido(json("{\"email\":\"joao.silva@email.com\"}"), "email");
            falha("email presente no update_user deveria ser recusado");
        } catch (CampoInvalidoException e) {
            confere("email presente no update_user vira 400", e.getMotivo() == Motivo.PROIBIDO);
        }
        try {
            Validador.proibido(json("{\"user\":\"joao\"}"), "email");
            confere("update_user sem email passa", true);
        } catch (CampoInvalidoException e) {
            falha("update_user sem email nao deveria falhar");
        }

        // --- erro de digitacao no nosso codigo falha alto, nao passa batido ---
        try {
            Validador.formatoValido("usuario", "joao");
            falha("campo fora do dicionario deveria lancar IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            confere("campo fora do dicionario falha alto", true);
        }

        System.out.println("\n===== validacao: " + ok + " passaram, " + falhas + " falharam =====");
        if (falhas > 0) System.exit(1);
    }

    static JsonObject json(String s) {
        return JsonParser.parseString(s).getAsJsonObject();
    }

    static void valido(String campo, String valor) {
        confere("valido   " + campo + " = \"" + valor + "\"", Validador.formatoValido(campo, valor));
    }

    static void invalido(String campo, String valor) {
        confere("invalido " + campo + " = \"" + valor + "\"", !Validador.formatoValido(campo, valor));
    }

    static void motivo(String nome, String req, String campo, Motivo esperado) {
        try {
            Validador.obrigatorio(json(req), campo);
            falha(nome + " (nao lancou)");
        } catch (CampoInvalidoException e) {
            confere(nome + " -> " + e.getMotivo(), e.getMotivo() == esperado);
        }
    }

    static void motivoAtualizacao(String nome, String req, String campo, Motivo esperado) {
        try {
            Validador.opcionalAtualizacao(json(req), campo);
            falha(nome + " (nao lancou)");
        } catch (CampoInvalidoException e) {
            confere(nome + " -> " + e.getMotivo(), e.getMotivo() == esperado);
        }
    }

    static void atualizacao(String nome, String req, String campo, String esperado) {
        try {
            String obtido = Validador.opcionalAtualizacao(json(req), campo);
            confere(nome + " -> " + (obtido == null ? "null" : "\"" + obtido + "\""),
                    esperado == null ? obtido == null : esperado.equals(obtido));
        } catch (CampoInvalidoException e) {
            falha(nome + " lancou " + e.getMotivo());
        }
    }

    static void confere(String nome, boolean passou) {
        if (passou) { ok++; System.out.println("  OK    " + nome); }
        else { falhas++; System.out.println("  FALHA " + nome); }
    }

    static void falha(String nome) { confere(nome, false); }
}
