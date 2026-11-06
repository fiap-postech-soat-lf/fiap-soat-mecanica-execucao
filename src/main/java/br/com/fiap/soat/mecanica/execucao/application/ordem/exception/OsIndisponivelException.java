package br.com.fiap.soat.mecanica.execucao.application.ordem.exception;

/** OS fora do ar depois dos retries (503 OS_INDISPONIVEL). */
public class OsIndisponivelException extends RuntimeException {

    public OsIndisponivelException(String message, Throwable cause) {
        super(message, cause);
    }
}
