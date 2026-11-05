package protocolo;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A grade de horarios da UTFPR, como a secao 8 do protocolo define.
 *
 * Um horario e escrito como DIA + TURNO + AULA: "2M1" e segunda-feira, manha,
 * primeira aula (07:30-08:20). O dia vai de 2 (segunda) a 7 (sabado) -- domingo
 * nao existe na grade, e por isso nenhuma sala funciona nele.
 *
 * Fica no pacote 'protocolo' e nao no 'servidor' porque os dois lados precisam
 * dela: o servidor para montar a agenda e conferir a disponibilidade, e o
 * cliente para mostrar na tela que "3T1" quer dizer terca-feira, 13:00 as
 * 13:50. Se cada lado tivesse a sua copia da tabela, bastaria uma linha
 * diferente para os dois discordarem sobre o que e o mesmo horario.
 */
public final class Horarios {

    /** As aulas na ordem da tabela -- e a ordem em que a agenda e devolvida. */
    private static final Map<String, LocalTime[]> AULAS;

    /** Os dias da grade, de segunda (2) a sabado (7). */
    private static final Map<DayOfWeek, Character> DIAS;

    static {
        Map<String, LocalTime[]> m = new LinkedHashMap<>();
        m.put("M1", faixa("07:30", "08:20"));
        m.put("M2", faixa("08:20", "09:10"));
        m.put("M3", faixa("09:10", "10:00"));
        m.put("M4", faixa("10:20", "11:10"));
        m.put("M5", faixa("11:10", "12:00"));
        m.put("M6", faixa("12:00", "12:50"));
        m.put("T1", faixa("13:00", "13:50"));
        m.put("T2", faixa("13:50", "14:40"));
        m.put("T3", faixa("14:40", "15:30"));
        m.put("T4", faixa("15:50", "16:40"));
        m.put("T5", faixa("16:40", "17:30"));
        m.put("T6", faixa("17:30", "18:20"));
        m.put("N1", faixa("18:40", "19:30"));
        m.put("N2", faixa("19:30", "20:20"));
        m.put("N3", faixa("20:20", "21:10"));
        m.put("N4", faixa("21:20", "22:10"));
        m.put("N5", faixa("22:10", "23:00"));
        AULAS = Collections.unmodifiableMap(m);

        Map<DayOfWeek, Character> d = new LinkedHashMap<>();
        d.put(DayOfWeek.MONDAY, '2');
        d.put(DayOfWeek.TUESDAY, '3');
        d.put(DayOfWeek.WEDNESDAY, '4');
        d.put(DayOfWeek.THURSDAY, '5');
        d.put(DayOfWeek.FRIDAY, '6');
        d.put(DayOfWeek.SATURDAY, '7');
        // Domingo fica de fora de proposito: a grade da UTFPR nao tem domingo,
        // entao nenhuma sala pode funcionar nele (regra 8.4).
        DIAS = Collections.unmodifiableMap(d);
    }

    private Horarios() {
    }

    private static LocalTime[] faixa(String inicio, String fim) {
        return new LocalTime[] {LocalTime.parse(inicio), LocalTime.parse(fim)};
    }

    /** As aulas na ordem da tabela: M1, M2, ..., N5. */
    public static List<String> aulas() {
        return new ArrayList<>(AULAS.keySet());
    }

    /** O digito do dia para uma data, ou null no domingo. */
    public static Character digitoDoDia(LocalDate dia) {
        return DIAS.get(dia.getDayOfWeek());
    }

    /** O codigo do dia de um horario: o '3' de "3T1". */
    public static char diaDe(String slot) {
        return slot.charAt(0);
    }

    /** A aula de um horario: o "T1" de "3T1". */
    public static String aulaDe(String slot) {
        return slot.substring(1);
    }

    public static LocalTime inicioDe(String slot) {
        return AULAS.get(aulaDe(slot))[0];
    }

    public static LocalTime fimDe(String slot) {
        return AULAS.get(aulaDe(slot))[1];
    }

    /** Diz se o texto e um horario valido da grade ("2M1", "5N3"). */
    public static boolean ehSlot(String slot) {
        return slot != null && slot.length() >= 3
                && DIAS.containsValue(slot.charAt(0))
                && AULAS.containsKey(aulaDe(slot));
    }

    /**
     * Diz se o horario e o INICIO de alguma aula da grade.
     *
     * A regra 8.3 manda recusar qualquer outro valor em reservas e consultas:
     * reservar das 14:15 as 15:00 nao faz sentido numa grade de aulas fixas.
     */
    public static boolean ehInicioDeAula(LocalTime hora) {
        return AULAS.values().stream().anyMatch(f -> f[0].equals(hora));
    }

    /** Diz se o horario e o FIM de alguma aula da grade (regra 8.3). */
    public static boolean ehFimDeAula(LocalTime hora) {
        return AULAS.values().stream().anyMatch(f -> f[1].equals(hora));
    }

    /**
     * As aulas cobertas por um periodo, na ordem da tabela.
     *
     * Uma aula entra quando cabe inteira dentro do periodo. E por isso que
     * 09:10-11:10 cobre M3 (09:10-10:00) e M4 (10:20-11:10) mesmo com o
     * intervalo das 10:00 as 10:20 no meio -- o intervalo nao e aula, entao
     * nao ha o que reservar nele.
     */
    public static List<String> aulasEntre(LocalTime inicio, LocalTime fim) {
        List<String> cobertas = new ArrayList<>();
        for (Map.Entry<String, LocalTime[]> e : AULAS.entrySet()) {
            LocalTime comeco = e.getValue()[0];
            LocalTime termino = e.getValue()[1];
            if (!comeco.isBefore(inicio) && !termino.isAfter(fim)) {
                cobertas.add(e.getKey());
            }
        }
        return cobertas;
    }

    /**
     * Os horarios de uma sala que caem num dia da semana, na ordem da tabela.
     *
     * E o que o read_room com 'date' devolve como agenda (regra 8.5): um item
     * por horario da sala naquele dia. Sala que nao funciona no dia devolve
     * lista vazia, e domingo sempre devolve vazia.
     */
    public static List<String> slotsDoDia(List<String> slots, LocalDate dia) {
        Character digito = digitoDoDia(dia);
        if (digito == null || slots == null) {
            return List.of();
        }
        List<String> doDia = new ArrayList<>();
        for (String aula : AULAS.keySet()) {
            String slot = digito + aula;
            if (slots.contains(slot)) {
                doDia.add(slot);
            }
        }
        return doDia;
    }

    /**
     * Ordena uma lista de horarios: primeiro por dia, depois pela ordem da
     * tabela de aulas. Guardados fora de ordem, "3T1" viria antes de "2M1".
     */
    public static List<String> ordenar(List<String> slots) {
        List<String> ordenados = new ArrayList<>();
        for (char dia = '2'; dia <= '7'; dia++) {
            for (String aula : AULAS.keySet()) {
                String slot = dia + aula;
                if (slots.contains(slot)) {
                    ordenados.add(slot);
                }
            }
        }
        return ordenados;
    }

    /** "3T1" lido por gente: "terça, 13:00-13:50". */
    public static String porExtenso(String slot) {
        String[] nomes = {"segunda", "terça", "quarta", "quinta", "sexta", "sábado"};
        int indice = diaDe(slot) - '2';
        String dia = indice >= 0 && indice < nomes.length ? nomes[indice] : "?";
        return dia + ", " + inicioDe(slot) + "-" + fimDe(slot);
    }
}
