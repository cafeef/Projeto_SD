import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

/**
 * Verificacao do enquadramento contra o ServidorEco.
 *
 * De proposito NAO usa a classe Framing: monta e le os bytes na mao, para
 * conferir o servidor de forma independente da nossa propria implementacao --
 * que e exatamente a situacao do teste de interoperabilidade com os outros
 * grupos, onde o outro lado nao compartilha codigo nenhum com o nosso.
 *
 * Uso: java VerificaProtocolo <porta>
 */
public class VerificaProtocolo {

    static int ok = 0, falhas = 0;

    public static void main(String[] args) throws Exception {
        int porta = Integer.parseInt(args[0]);

        try (Socket s = new Socket("127.0.0.1", porta)) {
            s.setSoTimeout(5000);
            OutputStream out = s.getOutputStream();
            InputStream in = s.getInputStream();

            checa("eco normal", enviaLe(out, in, "{\"op\":\"eco\",\"texto\":\"teste\"}"),
                  "\"status\":\"200\"", "\"echo\":\"TESTE\"");

            checa("UTF-8 com acento e emoji",
                  enviaLe(out, in, "{\"op\":\"eco\",\"texto\":\"reuniao cao 日本 🎉\"}"),
                  "\"status\":\"200\"", "日本");

            checa("JSON invalido (regra 4.1)", enviaLe(out, in, "{\"op\":"),
                  "\"op\":\"error\"", "\"Requisicao invalida\"");

            checa("texto solto, nao e objeto JSON", enviaLe(out, in, "abc"),
                  "\"op\":\"error\"", "\"Requisicao invalida\"");

            // Uma 'op' com formato valido mas que nao existe no dispatcher.
            // Nao usar aqui o nome de uma operacao real: elas existem agora.
            checa("op desconhecida (regra 4.2)",
                  enviaLe(out, in, "{\"op\":\"operacao_inexistente\"}"),
                  "\"op\":\"error\"", "\"Operacao desconhecida\"");

            // Regra 4.1 (v2.9.1): sem 'op', ou com 'op' que nao e string, a
            // mensagem nem chega a ser uma requisicao -- "Requisicao
            // invalida", como um JSON quebrado. So 'op' que E string e nao
            // conhecemos vira "Operacao desconhecida", que e o teste acima.
            checa("op ausente -> Requisicao invalida (regra 4.1)",
                  enviaLe(out, in, "{\"texto\":\"x\"}"),
                  "\"op\":\"error\"", "\"Requisicao invalida\"");

            checa("op numerica, nao string (regras 2.3 e 4.1)",
                  enviaLe(out, in, "{\"op\":123}"), "\"Requisicao invalida\"");

            checa("op null (regras 2.10 e 4.1)", enviaLe(out, in, "{\"op\":null}"),
                  "\"Requisicao invalida\"");

            // 'op' que e string mas nao passa na regex continua sendo uma
            // operacao que nao existe, e nao uma requisicao malformada.
            checa("op com formato estranho ainda e Operacao desconhecida",
                  enviaLe(out, in, "{\"op\":\"NAO_EXISTE 42\"}"),
                  "\"Operacao desconhecida\"");

            checa("campo obrigatorio ausente", enviaLe(out, in, "{\"op\":\"eco\"}"),
                  "\"op\":\"eco_response\"", "\"status\":\"400\"");

            checa("campo com null (regra 2.10)",
                  enviaLe(out, in, "{\"op\":\"eco\",\"texto\":null}"), "\"status\":\"400\"");

            checa("campo desconhecido e ignorado (regra 2.9)",
                  enviaLe(out, in, "{\"op\":\"eco\",\"texto\":\"ok\",\"futuro\":\"xyz\"}"),
                  "\"status\":\"200\"", "\"echo\":\"OK\"");

            checa("tolera CRLF de outra implementacao",
                  enviaLeBruto(out, in, "{\"op\":\"eco\",\"texto\":\"crlf\"}\r\n"),
                  "\"status\":\"200\"", "\"echo\":\"CRLF\"");

            checa("mensagem muito acima do limite (regra 4.3)",
                  enviaLe(out, in, comTamanho(20000)),
                  "\"op\":\"error\"", "\"Mensagem excede o tamanho maximo\"");

            checa("conexao ressincroniza depois do excesso",
                  enviaLe(out, in, "{\"op\":\"eco\",\"texto\":\"depois\"}"),
                  "\"status\":\"200\"", "\"echo\":\"DEPOIS\"");

            checa("borda: 8191 bytes de corpo + LF = 8192, aceita",
                  enviaLe(out, in, comTamanho(8191)), "\"Operacao desconhecida\"");

            checa("borda: 8192 bytes de corpo + LF = 8193, recusada",
                  enviaLe(out, in, comTamanho(8192)), "\"Mensagem excede o tamanho maximo\"");

            // Requisicao dentro do limite cuja RESPOSTA passa de 8192 bytes.
            // Regra 1.6 (v2.9.1): o limite e so da requisicao, entao a resposta
            // sai inteira. Ate a v2.8 isto virava 500, e uma listagem grande
            // ficava impossivel de devolver.
            StringBuilder grande = new StringBuilder("{\"op\":\"eco\",\"texto\":\"");
            for (int i = 0; i < 8150; i++) grande.append('c');
            grande.append("\"}");
            String respostaGrande = enviaLe(out, in, grande.toString());
            checa("resposta acima de 8192 bytes sai inteira (regra 1.6)",
                  respostaGrande, "\"op\":\"eco_response\"", "\"status\":\"200\"");
            checa("a resposta grande passou mesmo do limite de requisicao",
                  respostaGrande.length() > 8192 ? "ok" : "curta demais: "
                          + respostaGrande.length(), "ok");

            checa("conexao sobrevive a resposta grande",
                  enviaLe(out, in, "{\"op\":\"eco\",\"texto\":\"vivo\"}"),
                  "\"status\":\"200\"", "\"echo\":\"VIVO\"");

            // Duas mensagens num unico write: o framing tem que separar as duas.
            out.write("{\"op\":\"eco\",\"texto\":\"um\"}\n{\"op\":\"eco\",\"texto\":\"dois\"}\n"
                    .getBytes(StandardCharsets.UTF_8));
            out.flush();
            checa("pipeline: 1a de 2 mensagens num write", le(in), "\"echo\":\"UM\"");
            checa("pipeline: 2a de 2 mensagens num write", le(in), "\"echo\":\"DOIS\"");

            checa("resposta termina em LF puro, sem CR (regra 1.5)",
                  enviaLeConferindoCR(out, in, "{\"op\":\"eco\",\"texto\":\"lf\"}\n"), "SEM_CR");
        }

        System.out.println("\n===== transporte: " + ok + " passaram, " + falhas + " falharam =====");
        if (falhas > 0) System.exit(1);
    }

    /** Monta uma mensagem de op desconhecida com exatamente 'alvo' bytes (tudo ASCII). */
    static String comTamanho(int alvo) {
        String prefixo = "{\"op\":\"operacao_inexistente\",\"pad\":\"";
        String sufixo = "\"}";
        return prefixo + "a".repeat(alvo - prefixo.length() - sufixo.length()) + sufixo;
    }

    static String enviaLe(OutputStream out, InputStream in, String json) throws IOException {
        return enviaLeBruto(out, in, json + "\n");
    }

    static String enviaLeBruto(OutputStream out, InputStream in, String cru) throws IOException {
        out.write(cru.getBytes(StandardCharsets.UTF_8));
        out.flush();
        return le(in);
    }

    /** Confere se a resposta termina em LF puro, sem CR antes. */
    static String enviaLeConferindoCR(OutputStream out, InputStream in, String cru)
            throws IOException {
        out.write(cru.getBytes(StandardCharsets.UTF_8));
        out.flush();
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) != -1 && b != '\n') buf.write(b);
        byte[] bytes = buf.toByteArray();
        boolean temCR = bytes.length > 0 && bytes[bytes.length - 1] == '\r';
        return (temCR ? "TEM_CR " : "SEM_CR ") + new String(bytes, StandardCharsets.UTF_8);
    }

    static String le(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) != -1 && b != '\n') buf.write(b);
        return new String(buf.toByteArray(), StandardCharsets.UTF_8);
    }

    static void checa(String nome, String resposta, String... devemAparecer) {
        boolean passou = true;
        for (String esperado : devemAparecer) {
            if (!resposta.toUpperCase().contains(esperado.toUpperCase())) passou = false;
        }
        if (passou) { ok++; System.out.println("  OK    " + nome); }
        else { falhas++; System.out.println("  FALHA " + nome + "\n         resposta: "
                + (resposta.length() > 160 ? resposta.substring(0, 160) + "..." : resposta)); }
    }
}
