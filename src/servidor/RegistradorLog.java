package servidor;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Destino das linhas de log do servidor.
 *
 * As mensagens trocadas sao mostradas no TERMINAL que executa a aplicacao, nao
 * na janela: a interface grafica fica so com o controle do servidor. A obs. 3 da
 * grade exige exibir as mensagens enviadas e recebidas, e o terminal cumpre
 * isso -- desde que a aplicacao seja iniciada a partir de um terminal.
 */
@FunctionalInterface
public interface RegistradorLog {

    DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm:ss");

    void registrar(String linha);

    /** Registrador padrao: imprime no terminal, com a hora na frente. */
    RegistradorLog CONSOLE =
            linha -> System.out.println(LocalTime.now().format(HORA) + "  " + linha);
}
