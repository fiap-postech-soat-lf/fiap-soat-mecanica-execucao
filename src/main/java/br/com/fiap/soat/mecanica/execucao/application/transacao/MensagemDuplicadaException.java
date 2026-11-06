package br.com.fiap.soat.mecanica.execucao.application.transacao;

/** O registro de idempotencia da transacao ja existia: a mensagem foi processada antes (F5-g). */
public class MensagemDuplicadaException extends RuntimeException {

    public MensagemDuplicadaException(String message, Throwable cause) {
        super(message, cause);
    }
}
