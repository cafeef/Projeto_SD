package servidor.modelo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Uma reserva, como esta no banco, ja com o nome da sala e o dono resolvidos.
 *
 * O nome da sala e o usuario vem do JOIN, e nao de outra consulta, porque o
 * protocolo os exige dentro do item de 'reservations' (esquema Reserva) e do
 * read_reservation: buscar um por um faria uma listagem de N reservas custar
 * 2N+1 consultas.
 *
 * Cuidado com a palavra "ativa", que tem dois sentidos neste sistema:
 *
 *   - a coluna 'ativa' do banco diz que a reserva NAO foi cancelada;
 *   - a regra 9.1 chama de ativa a reserva que ainda nao TERMINOU.
 *
 * Os dois precisam valer juntos: uma reserva cancelada nao conta, e uma de
 * ontem tambem nao. Aqui o campo e 'cancelada', o inverso da coluna, para que
 * a palavra "ativa" nao signifique duas coisas conforme a linha. Quem responde
 * pela regra 9.1 e estaEncerrada(); quem responde pelo cancelamento e
 * foiCancelada().
 */
public class Reserva {

    private final int id;
    private final int salaId;
    private final String nomeSala;
    private final int usuarioId;
    private final String usuario;
    private final LocalDate dia;
    private final LocalTime inicio;
    private final LocalTime fim;
    private final String assunto;
    private final int participantes;
    private final boolean cancelada;
    private final LocalDateTime criadaEm;

    public Reserva(int id, int salaId, String nomeSala, int usuarioId, String usuario,
                   LocalDate dia, LocalTime inicio, LocalTime fim, String assunto,
                   int participantes, boolean cancelada, LocalDateTime criadaEm) {
        this.id = id;
        this.salaId = salaId;
        this.nomeSala = nomeSala;
        this.usuarioId = usuarioId;
        this.usuario = usuario;
        this.dia = dia;
        this.inicio = inicio;
        this.fim = fim;
        this.assunto = assunto;
        this.participantes = participantes;
        this.cancelada = cancelada;
        this.criadaEm = criadaEm;
    }

    /** Corresponde a 'reservation_id'. */
    public int getId() {
        return id;
    }

    /** Corresponde a 'room_id'. */
    public int getSalaId() {
        return salaId;
    }

    /** Corresponde a 'name': o nome da SALA, nao o da reserva. */
    public String getNomeSala() {
        return nomeSala;
    }

    /** Id do dono. Nao vai para a resposta -- serve para comparar sem consultar de novo. */
    public int getUsuarioId() {
        return usuarioId;
    }

    /** Corresponde a 'user': o dono da reserva. */
    public String getUsuario() {
        return usuario;
    }

    /** Corresponde a 'date'. */
    public LocalDate getDia() {
        return dia;
    }

    /** Corresponde a 'start_time'. */
    public LocalTime getInicio() {
        return inicio;
    }

    /** Corresponde a 'end_time'. */
    public LocalTime getFim() {
        return fim;
    }

    /** Corresponde a 'topic'. */
    public String getAssunto() {
        return assunto;
    }

    /** Corresponde a 'participants'. */
    public int getParticipantes() {
        return participantes;
    }

    /** Corresponde a 'created_at'. */
    public LocalDateTime getCriadaEm() {
        return criadaEm;
    }

    /** Reserva cancelada pelo delete_reservation. Some das consultas. */
    public boolean foiCancelada() {
        return cancelada;
    }

    /**
     * Regra 9.1: encerrada e a que ja terminou -- 'dia' + 'fim' no passado.
     *
     * E a condicao do 409 "Reserva ja encerrada": alterar ou cancelar algo que
     * ja aconteceu nao muda nada no mundo.
     */
    public boolean estaEncerrada() {
        return !dia.atTime(fim).isAfter(LocalDateTime.now());
    }

    /** Diz se o usuario e o dono desta reserva (regras 9.9 e 3.14). */
    public boolean ehDe(int idDoUsuario) {
        return usuarioId == idDoUsuario;
    }

    @Override
    public String toString() {
        return "Reserva{" + id + ", sala " + salaId + ", " + usuario + ", "
                + dia + " " + inicio + "-" + fim + (cancelada ? ", cancelada}" : "}");
    }
}
