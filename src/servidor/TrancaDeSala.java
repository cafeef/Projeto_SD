package servidor;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Trancas por sala, uma por room_id (regras 5.1 e 5.5).
 *
 * O protocolo exige que "verificar e gravar" aconteca na MESMA secao critica.
 * Sem isso, duas requisicoes concorrentes podem as duas consultar o banco,
 * as duas concluir que esta livre, e as duas gravar -- cada uma correta
 * sozinha, as duas erradas juntas. O banco nao resolve isso por conta propria:
 * nao existe indice unico capaz de expressar "nao pode haver reserva que
 * encoste nesta faixa de horario", porque a condicao e um intervalo que se
 * cruza, e nao um valor repetido.
 *
 * A tranca e por sala, e nao global, porque salas diferentes nao disputam nada:
 * reservar a Sala A nao pode esperar por quem esta reservando a Sala B.
 *
 * Quem usa (v2.9.1): update_room e delete_room, que leem as reservas ativas
 * antes de gravar. Na EP-3 entram create_reservation e update_reservation.
 *
 * As trancas ficam no mapa depois de usadas. Sao poucos bytes por sala, o
 * numero de salas e pequeno e limitado por cadastro de administrador, e
 * remove-las exigiria saber que ninguem mais as segura -- justamente a
 * coordenacao que elas existem para fazer.
 */
public final class TrancaDeSala {

    private static final ConcurrentHashMap<Integer, ReentrantLock> TRANCAS =
            new ConcurrentHashMap<>();

    private TrancaDeSala() {
    }

    /** O que roda dentro da secao critica. */
    @FunctionalInterface
    public interface Acao<T> {
        T executar() throws Exception;
    }

    /**
     * Roda a acao com a sala trancada. Outra thread que peca a MESMA sala
     * espera; quem pede outra sala passa direto.
     */
    public static <T> T comASalaTrancada(int roomId, Acao<T> acao) throws Exception {
        ReentrantLock tranca = TRANCAS.computeIfAbsent(roomId, id -> new ReentrantLock());
        tranca.lock();
        try {
            return acao.executar();
        } finally {
            tranca.unlock();
        }
    }

    /**
     * Roda a acao com DUAS salas trancadas -- a troca de sala do
     * update_reservation (regra 5.6).
     *
     * A ordem e sempre crescente por room_id, e isso nao e detalhe de estilo:
     * duas trocas em sentidos opostos (A->B e B->A) acontecendo ao mesmo tempo
     * travariam uma na outra para sempre se cada uma pegasse as trancas na
     * ordem que lhe conviesse. Ordenar e o que garante que nunca ha espera
     * circular.
     *
     * Salas iguais viram uma tranca so. O ReentrantLock aceitaria o segundo
     * lock(), mas pedir duas vezes a mesma tranca so confundiria quem le.
     */
    public static <T> T comAsSalasTrancadas(int salaA, int salaB, Acao<T> acao) throws Exception {
        if (salaA == salaB) {
            return comASalaTrancada(salaA, acao);
        }
        int primeira = Math.min(salaA, salaB);
        int segunda = Math.max(salaA, salaB);
        return comASalaTrancada(primeira, () -> comASalaTrancada(segunda, acao));
    }

    /** Esquece todas as trancas. So para os testes comecarem limpos. */
    public static void limpar() {
        TRANCAS.clear();
    }
}
