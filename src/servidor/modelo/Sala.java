package servidor.modelo;

import protocolo.Horarios;

import java.util.Collections;
import java.util.List;

/**
 * Uma sala, como esta no banco.
 *
 * Os nomes dos campos aqui seguem o banco, nao o protocolo: a traducao para
 * room_id / name / capacity / location / resources / room_status acontece nos
 * handlers, que sao a fronteira com o mundo de fora. Manter a traducao num
 * lugar so e o que permitiu a mudanca de versao do protocolo na EP-1 nao
 * espalhar alteracoes pelo DAO.
 */
public class Sala {

    private final int id;
    private final String nome;
    private final int capacidade;
    private final String localizacao;
    private final List<String> recursos;
    private final String estado;
    private final List<String> slots;

    public Sala(int id, String nome, int capacidade, String localizacao,
                List<String> recursos, String estado, List<String> slots) {
        this.id = id;
        this.nome = nome;
        this.capacidade = capacidade;
        this.localizacao = localizacao;
        this.recursos = recursos == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(recursos);
        this.estado = estado;
        // Sempre na ordem da grade da UTFPR, venham como vierem do banco: a
        // agenda do read_room tem que sair nessa ordem (regra 8.5), e ordenar
        // aqui garante isso para todo mundo que use a Sala.
        this.slots = slots == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(Horarios.ordenar(slots));
    }

    public int getId() {
        return id;
    }

    /** Corresponde ao campo 'name' do protocolo. */
    public String getNome() {
        return nome;
    }

    /** Corresponde a 'capacity'. Inteiro aqui, string na resposta (regra 2.7). */
    public int getCapacidade() {
        return capacidade;
    }

    /** Corresponde a 'location'. */
    public String getLocalizacao() {
        return localizacao;
    }

    /** Corresponde a 'resources'. Lista imutavel, possivelmente vazia -- nunca nula. */
    public List<String> getRecursos() {
        return recursos;
    }

    /**
     * Corresponde a 'slots': os horarios em que a sala funciona, no codigo da
     * UTFPR, ja na ordem da grade. Lista imutavel.
     */
    public List<String> getSlots() {
        return slots;
    }

    /** Os horarios da sala que caem num dia, na ordem da grade (regra 8.5). */
    public List<String> slotsDoDia(java.time.LocalDate dia) {
        return Horarios.slotsDoDia(slots, dia);
    }

    /**
     * Diz se a sala funciona em TODAS as aulas de um periodo (regra 8.4).
     *
     * Basta uma aula fora do funcionamento para a sala nao servir: reservar
     * das 13:00 as 14:40 numa terca exige 3T1 e 3T2. Domingo nunca passa,
     * porque a grade da UTFPR nao tem domingo.
     *
     * Fica no modelo, e nao no handler, porque a mesma pergunta e feita em
     * tres lugares -- list_rooms, check_availability e create_reservation --
     * e tres copias dela poderiam discordar sobre a mesma sala.
     *
     * @param aulas as aulas que o periodo cobre, de Horarios.aulasEntre
     */
    public boolean funcionaNoPeriodo(java.time.LocalDate dia, List<String> aulas) {
        Character digito = Horarios.digitoDoDia(dia);
        if (digito == null || aulas.isEmpty()) {
            return false;
        }
        for (String aula : aulas) {
            if (!slots.contains(digito + aula)) {
                return false;
            }
        }
        return true;
    }

    /** Corresponde a 'room_status': "active" ou "inactive". */
    public String getEstado() {
        return estado;
    }

    /** Uma sala "inactive" esta em manutencao e nao aceita novas reservas. */
    public boolean estaAtiva() {
        return "active".equals(estado);
    }

    @Override
    public String toString() {
        return "Sala{" + id + ", " + nome + ", " + capacidade + " lugares, " + estado
                + ", " + slots.size() + " horarios}";
    }
}
