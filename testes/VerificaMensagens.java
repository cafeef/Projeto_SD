package cliente;

/**
 * Verifica a traducao das mensagens para exibicao.
 *
 * Fica no pacote cliente para enxergar a classe Mensagens, que e de uso interno
 * da interface e nao precisa ser publica.
 */
public class VerificaMensagens {

    static int ok = 0, falhas = 0;

    public static void main(String[] args) {
        // O que vem do protocolo e exibido com acento.
        acentuada("Usuario cadastrado com sucesso");
        acentuada("Usuario ou email ja cadastrado");
        acentuada("Email ou senha em formato invalido");
        acentuada("Usuario ja possui sessao ativa");
        acentuada("Token invalido ou expirado");
        acentuada("Dados em formato invalido");
        acentuada("Usuario ja esta em uso");
        acentuada("Nao e possivel remover o ultimo administrador");
        acentuada("Requisicao invalida");
        acentuada("Operacao desconhecida");
        acentuada("Mensagem excede o tamanho maximo");

        // Mensagem de outro grupo, fora do nosso mapa, e exibida como veio.
        String desconhecida = "Mensagem que nenhum grupo combinou";
        confere("mensagem desconhecida e exibida sem traducao",
                desconhecida.equals(Mensagens.paraExibicao(desconhecida)));

        confere("mensagem nula nao quebra a tela",
                Mensagens.paraExibicao(null) != null);

        // A dica extra aparece so onde o proximo passo nao e obvio.
        confere("409 no login orienta a fazer logout",
                Mensagens.dica("409", "login_response") != null);
        confere("200 nao tem dica", Mensagens.dica("200", "login_response") == null);

        System.out.println("\n===== mensagens: " + ok + " passaram, " + falhas + " falharam =====");
        if (falhas > 0) System.exit(1);
    }

    /** A versao exibida tem que ser diferente da original e conter acento. */
    static void acentuada(String doProtocolo) {
        String exibida = Mensagens.paraExibicao(doProtocolo);
        boolean temAcento = false;
        for (char c : exibida.toCharArray()) {
            if (c > 0x7E) {
                temAcento = true;
                break;
            }
        }
        confere("\"" + doProtocolo + "\" -> \"" + exibida + "\"",
                temAcento && !exibida.equals(doProtocolo));
    }

    static void confere(String nome, boolean passou) {
        if (passou) { ok++; System.out.println("  OK    " + nome); }
        else { falhas++; System.out.println("  FALHA " + nome); }
    }
}
