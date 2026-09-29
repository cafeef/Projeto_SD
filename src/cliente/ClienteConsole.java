package cliente;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Cliente em modo console.
 *
 * A interface oficial e a {@link ClienteGUI}; esta versao existe para testar
 * rapidamente sem depender de tela -- inclusive contra o servidor de outro
 * grupo, no teste de interoperabilidade.
 *
 * Comandos:
 *
 *   register &lt;email&gt; &lt;user&gt; &lt;senha&gt;
 *   login &lt;email&gt; &lt;senha&gt;
 *   read
 *   update &lt;user|-&gt; &lt;senha|-&gt;      ("-" deixa o campo vazio: nao alterar)
 *   delete &lt;senha&gt;
 *   logout
 *   raw &lt;json&gt;                        envia um JSON qualquer, como esta
 *   /sair
 */
public class ClienteConsole {

    private static final Gson GSON = new Gson();

    private static String token;

    public static void main(String[] args) throws IOException {
        BufferedReader teclado = new BufferedReader(
                new InputStreamReader(System.in, StandardCharsets.UTF_8));

        // Obs. 5 da grade: um campo para o IP e outro para a porta.
        System.out.print("IP do servidor: ");
        String ip = teclado.readLine().trim();
        System.out.print("Porta do servidor: ");
        int porta;
        try {
            porta = Integer.parseInt(teclado.readLine().trim());
        } catch (NumberFormatException e) {
            System.err.println("Porta invalida: informe um numero.");
            return;
        }

        System.out.println("Conectando em " + ip + ":" + porta + "...");
        try (Conexao conexao = new Conexao()) {
            conexao.conectar(ip, porta);
            System.out.println("Conectado. Comandos: register, login, read, update, delete,"
                    + " logout, raw, /sair\n");

            String linha;
            while ((linha = teclado.readLine()) != null) {
                linha = linha.trim();
                if (linha.isEmpty()) {
                    continue;
                }
                if ("/sair".equalsIgnoreCase(linha)) {
                    System.out.println("Encerrando a conexao.");
                    break;
                }

                String json = montar(linha);
                if (json == null) {
                    continue;
                }

                try {
                    System.out.println("  enviou:  " + json);
                    String resposta = conexao.enviarEReceber(json);
                    if (resposta == null) {
                        System.out.println("  o servidor encerrou a conexao.");
                        break;
                    }
                    System.out.println("  recebeu: " + resposta);
                    guardarToken(resposta);
                } catch (Exception e) {
                    System.err.println("  falha na troca: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("Falha ao conectar em " + ip + ":" + porta + " -- " + e.getMessage());
        }
    }

    /** Traduz o comando digitado em uma requisicao do protocolo. */
    private static String montar(String linha) {
        String[] p = linha.split("\\s+");
        String comando = p[0].toLowerCase();

        switch (comando) {
            case "register":
                if (p.length < 4) {
                    return erroUso("register <email> <user> <senha>");
                }
                return GSON.toJson(requisicao("register", "email", p[1], "user", p[2],
                        "password", p[3]));

            case "login":
                if (p.length < 3) {
                    return erroUso("login <email> <senha>");
                }
                return GSON.toJson(requisicao("login", "email", p[1], "password", p[2]));

            case "read":
                return comToken("read_user");

            case "update": {
                if (p.length < 3) {
                    return erroUso("update <user|-> <senha|->");
                }
                JsonObject req = requisicao("update_user");
                if (token == null) {
                    return erroUso("faca login primeiro");
                }
                req.addProperty("token", token);
                // Regra 2.11: string vazia significa "nao alterar".
                req.addProperty("user", "-".equals(p[1]) ? "" : p[1]);
                req.addProperty("password", "-".equals(p[2]) ? "" : p[2]);
                return GSON.toJson(req);
            }

            case "delete": {
                if (p.length < 2) {
                    return erroUso("delete <senha>");
                }
                if (token == null) {
                    return erroUso("faca login primeiro");
                }
                JsonObject req = requisicao("delete_user");
                req.addProperty("token", token);
                req.addProperty("password", p[1]);
                return GSON.toJson(req);
            }

            case "logout":
                return comToken("logout");

            case "raw":
                if (p.length < 2) {
                    return erroUso("raw <json>");
                }
                return linha.substring(linha.indexOf(' ') + 1).trim();

            default:
                System.err.println("  comando desconhecido: " + comando);
                return null;
        }
    }

    private static String comToken(String op) {
        if (token == null) {
            return erroUso("faca login primeiro");
        }
        JsonObject req = requisicao(op);
        req.addProperty("token", token);
        return GSON.toJson(req);
    }

    /** 'op' sempre como primeira chave (regra 2.1). */
    private static JsonObject requisicao(String op, String... pares) {
        JsonObject req = new JsonObject();
        req.addProperty("op", op);
        for (int i = 0; i < pares.length; i += 2) {
            req.addProperty(pares[i], pares[i + 1]);
        }
        return req;
    }

    /** Guarda o token do login e o descarta no logout, no delete e em todo 401. */
    private static void guardarToken(String resposta) {
        try {
            JsonObject r = JsonParser.parseString(resposta).getAsJsonObject();
            String op = r.has("op") ? r.get("op").getAsString() : "";
            String status = r.has("status") ? r.get("status").getAsString() : "";

            if ("login_response".equals(op) && "200".equals(status) && r.has("token")) {
                token = r.get("token").getAsString();
                System.out.println("  (token guardado)");
            } else if ("401".equals(status)
                    || ("logout_response".equals(op) && "200".equals(status))
                    || ("delete_user_response".equals(op) && "200".equals(status))) {
                if (token != null) {
                    token = null;
                    System.out.println("  (token descartado)");
                }
            }
        } catch (RuntimeException e) {
            // Resposta ilegivel ja foi impressa crua; nada a guardar.
        }
    }

    private static String erroUso(String uso) {
        System.err.println("  uso: " + uso);
        return null;
    }
}
