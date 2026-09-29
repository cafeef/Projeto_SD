package servidor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

/**
 * Hash de senha com SHA-256 e salt por usuario.
 *
 * O protocolo nao exige isso -- a senha trafega em claro dentro do JSON, e nao
 * ha o que fazer quanto a isso sem sair da especificacao combinada com a turma.
 * Mas guardar senha em claro no banco seria pior ainda, e a regra "a senha nunca
 * aparece em resposta" fica mais facil de garantir quando o banco simplesmente
 * nao tem a senha para devolver.
 */
public final class Senhas {

    private static final SecureRandom ALEATORIO = new SecureRandom();

    private Senhas() {
    }

    /** Gera um salt novo, 16 bytes em hexadecimal (32 caracteres). */
    public static String novoSalt() {
        byte[] bytes = new byte[16];
        ALEATORIO.nextBytes(bytes);
        return paraHex(bytes);
    }

    /** SHA-256 de salt + senha, em hexadecimal minusculo (64 caracteres). */
    public static String hash(String senha, String salt) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            sha.update(salt.getBytes(StandardCharsets.UTF_8));
            sha.update(senha.getBytes(StandardCharsets.UTF_8));
            return paraHex(sha.digest());
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 e obrigatorio em toda JVM; se faltar, nao ha o que fazer.
            throw new IllegalStateException("SHA-256 indisponivel nesta JVM", e);
        }
    }

    /**
     * Confere a senha em tempo constante.
     *
     * MessageDigest.isEqual nao interrompe na primeira diferenca, ao contrario
     * de String.equals -- evita vazar informacao pelo tempo de resposta.
     */
    public static boolean confere(String senhaInformada, String salt, String hashGuardado) {
        if (senhaInformada == null || salt == null || hashGuardado == null) {
            return false;
        }
        byte[] calculado = hash(senhaInformada, salt).getBytes(StandardCharsets.UTF_8);
        byte[] guardado = hashGuardado.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(calculado, guardado);
    }

    /**
     * Gera um token de sessao: 64 caracteres hexadecimais minusculos, no formato
     * exigido pela regra 3.1 (regex ^[a-f0-9]{64}$).
     */
    public static String novoToken() {
        byte[] bytes = new byte[32];
        ALEATORIO.nextBytes(bytes);
        return paraHex(bytes);
    }

    private static String paraHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
