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
 * Comandos da EP-1:
 *
 *   register &lt;email&gt; &lt;user&gt; &lt;senha&gt;
 *   login &lt;email&gt; &lt;senha&gt;
 *   read
 *   update &lt;user|-&gt; &lt;senha|-&gt;      ("-" deixa o campo vazio: nao alterar)
 *   delete &lt;senha&gt;
 *   logout
 *
 * Comandos da EP-2 -- administracao:
 *
 *   users                             lista todos os cadastros
 *   user &lt;alvo&gt;                 le um cadastro
 *   setuser &lt;alvo&gt; &lt;user|-&gt; &lt;senha|-&gt; &lt;role|-&gt;
 *   deluser &lt;alvo&gt;              remove um cadastro (sem senha)
 *
 * Comandos da EP-2 -- salas:
 *
 *   rooms                             lista todas
 *   rooms &lt;data&gt; &lt;inicio&gt; &lt;fim&gt; [capMin]
 *   rooms - - - &lt;capMin&gt;              so o filtro de capacidade
 *   room &lt;id&gt;                   le uma sala
 *   newroom &lt;nome&gt;;&lt;cap&gt;;&lt;local&gt;;&lt;recursos&gt;
 *   setroom &lt;id&gt;;&lt;nome|-&gt;;&lt;cap|-&gt;;&lt;local|-&gt;;&lt;recursos&gt;;&lt;estado|-&gt;
 *   delroom &lt;id&gt;
 *
 * Os comandos de sala usam ';' entre os campos, e nao espaco, porque nome e
 * local tem espaco dentro. Os recursos vao separados por virgula, e viajam
 * SEMPRE como lista completa: campo vazio quer dizer "sala sem recurso nenhum",
 * nao "manter os de antes" -- e assim que o protocolo define 'resources'.
 *
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
            System.out.println("Conectado. Comandos:");
            System.out.println("  EP-1: register, login, read, update, delete, logout");
            System.out.println("  EP-2: users, user, setuser, deluser,"
                    + " rooms, room, newroom, setroom, delroom");
            System.out.println("  raw <json>, /sair\n");

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

            // ------------------------------- EP-2: administracao de usuarios

            case "users":
                return comToken("admin_list_users");

            case "user":
                if (p.length < 2) {
                    return erroUso("user <alvo>");
                }
                return comToken("admin_read_user", "target_user", p[1]);

            case "setuser": {
                if (p.length < 5) {
                    return erroUso("setuser <alvo> <user|-> <senha|-> <role|->");
                }
                // As tres chaves sao obrigatorias; "" significa "nao alterar".
                return comToken("admin_update_user",
                        "target_user", p[1],
                        "user", vazioSeTraco(p[2]),
                        "password", vazioSeTraco(p[3]),
                        "role", vazioSeTraco(p[4]));
            }

            case "deluser":
                if (p.length < 2) {
                    return erroUso("deluser <alvo>");
                }
                // Sem senha: quem e removido nao esta no teclado para confirmar.
                return comToken("admin_delete_user", "target_user", p[1]);

            // ------------------------------------------- EP-2: salas

            case "rooms": {
                if (p.length == 1) {
                    return comToken("list_rooms");
                }
                if (p.length < 4) {
                    return erroUso("rooms [<data|-> <inicio|-> <fim|-> [capMin]]");
                }
                JsonObject req = requisicao("list_rooms");
                if (token == null) {
                    return erroUso("faca login primeiro");
                }
                req.addProperty("token", token);
                // Filtro "-" nao vira chave: a ausencia e que diz "sem filtro".
                acrescentarSeNaoForTraco(req, "date", p[1]);
                acrescentarSeNaoForTraco(req, "start_time", p[2]);
                acrescentarSeNaoForTraco(req, "end_time", p[3]);
                if (p.length > 4) {
                    acrescentarSeNaoForTraco(req, "min_capacity", p[4]);
                }
                return GSON.toJson(req);
            }

            case "room":
                if (p.length < 2) {
                    return erroUso("room <id>");
                }
                return comToken("read_room", "room_id", p[1]);

            case "newroom": {
                String[] c = campos(linha, 4);
                if (c == null) {
                    return erroUso("newroom <nome>;<cap>;<local>;<recursos>");
                }
                if (token == null) {
                    return erroUso("faca login primeiro");
                }
                JsonObject req = requisicao("create_room");
                req.addProperty("token", token);
                req.addProperty("name", c[0]);
                req.addProperty("capacity", c[1]);
                req.addProperty("location", c[2]);
                req.add("resources", PainelSalas.recursosDeTexto(c[3]));
                return GSON.toJson(req);
            }

            case "setroom": {
                String[] c = campos(linha, 6);
                if (c == null) {
                    return erroUso("setroom <id>;<nome|->;<cap|->;<local|->;"
                            + "<recursos>;<estado|->");
                }
                if (token == null) {
                    return erroUso("faca login primeiro");
                }
                JsonObject req = requisicao("update_room");
                req.addProperty("token", token);
                req.addProperty("room_id", c[0]);
                req.addProperty("name", vazioSeTraco(c[1]));
                req.addProperty("capacity", vazioSeTraco(c[2]));
                req.addProperty("location", vazioSeTraco(c[3]));
                // Sem "-" aqui de proposito: 'resources' nao tem como dizer
                // "nao alterar", porque "" nao e valor de array. A lista
                // enviada substitui a anterior inteira.
                req.add("resources", PainelSalas.recursosDeTexto(c[4]));
                req.addProperty("room_status", vazioSeTraco(c[5]));
                return GSON.toJson(req);
            }

            case "delroom":
                if (p.length < 2) {
                    return erroUso("delroom <id>");
                }
                return comToken("delete_room", "room_id", p[1]);

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

    private static String comToken(String op, String... pares) {
        if (token == null) {
            return erroUso("faca login primeiro");
        }
        JsonObject req = requisicao(op);
        req.addProperty("token", token);
        for (int i = 0; i < pares.length; i += 2) {
            req.addProperty(pares[i], pares[i + 1]);
        }
        return GSON.toJson(req);
    }

    /**
     * Separa os campos de um comando de sala, que vem depois do nome do
     * comando e sao divididos por ';'.
     *
     * O separador e ';' e nao espaco porque 'name' e 'location' tem espaco
     * dentro ("Sala de Estudo B12", "Bloco B - 1 andar").
     *
     * @return os campos ja aparados, ou null se vieram menos do que o esperado
     */
    private static String[] campos(String linha, int quantos) {
        int espaco = linha.indexOf(' ');
        if (espaco < 0) {
            return null;
        }
        // -1 preserva os campos vazios do fim: "a;b;c;" tem quatro campos, e o
        // ultimo vazio e justamente "sala sem recurso nenhum".
        String[] partes = linha.substring(espaco + 1).split(";", -1);
        if (partes.length < quantos) {
            return null;
        }
        String[] limpos = new String[quantos];
        for (int i = 0; i < quantos; i++) {
            limpos[i] = partes[i].trim();
        }
        return limpos;
    }

    /** Regra 2.11: "-" digitado vira "", que quer dizer "nao alterar". */
    private static String vazioSeTraco(String valor) {
        return "-".equals(valor) ? "" : valor;
    }

    private static void acrescentarSeNaoForTraco(JsonObject req, String chave, String valor) {
        if (!"-".equals(valor) && !valor.isEmpty()) {
            req.addProperty(chave, valor);
        }
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
