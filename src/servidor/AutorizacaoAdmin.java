package servidor;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Whitelist dos tokens com poder de administrador (regra 3.14).
 *
 * O perfil NUNCA vem do cliente: um "role" enviado em qualquer requisicao fora
 * do admin_update_user e ignorado. Quem decide e esta lista, que o servidor
 * mantem em memoria.
 *
 * Um token entra quando o login e de uma conta admin, ou quando um admin
 * promove alguem que ja esta logado. Sai quando a conta e rebaixada, no logout
 * e na remocao da conta. A mudanca vale na hora: o alvo nao precisa entrar de
 * novo para ganhar ou perder o poder.
 *
 * Duas decisoes que merecem explicacao:
 *
 *  - Por que uma lista separada, em vez de reler o role do banco a cada
 *    requisicao. E o mecanismo que a turma combinou no protocolo, e ele deixa
 *    visivel o instante exato em que o poder e concedido e retirado.
 *
 *  - Por que a expiracao nao precisa mexer na lista. Vencer um token nao
 *    dispara evento nenhum, entao um token vencido segue aqui dentro. E
 *    inofensivo: a sessao e recusada com 401 antes de a lista ser consultada, e
 *    os tokens tem 64 caracteres sorteados, de modo que um token novo nunca
 *    repete um antigo.
 */
public final class AutorizacaoAdmin {

    private static final Set<String> TOKENS = ConcurrentHashMap.newKeySet();

    private AutorizacaoAdmin() {
    }

    /** Da poder de admin ao token (login de conta admin, ou promocao). */
    public static void conceder(String token) {
        if (token != null) {
            TOKENS.add(token);
        }
    }

    /** Tira o poder (rebaixamento, logout, remocao da conta). */
    public static void revogar(String token) {
        if (token != null) {
            TOKENS.remove(token);
        }
    }

    /** Acompanha uma mudanca de perfil: promover concede, rebaixar revoga. */
    public static void sincronizar(String token, String role) {
        if ("admin".equals(role)) {
            conceder(token);
        } else {
            revogar(token);
        }
    }

    /** O unico lugar que decide se uma requisicao tem poder de admin. */
    public static boolean temPoder(String token) {
        return token != null && TOKENS.contains(token);
    }

    /**
     * Esvazia a lista. Chamado quando o servidor derruba as sessoes anteriores:
     * nenhum token sobrevive ao reinicio, logo nenhum poder tambem.
     */
    public static void limpar() {
        TOKENS.clear();
    }

    /** Quantos tokens tem poder agora. Existe para os testes conferirem. */
    public static int tamanho() {
        return TOKENS.size();
    }
}
