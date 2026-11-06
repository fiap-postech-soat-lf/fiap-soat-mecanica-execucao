package br.com.fiap.soat.mecanica.execucao.application.transacao;

/**
 * A versao da ordem ou o estoque de uma peca mudou entre a leitura e a confirmacao (F5-g). No consumidor
 * a operacao e repetida com nova leitura; no REST vira 409 {@code CONFLITO_CONCORRENTE}.
 */
public class ConflitoConcorrenciaException extends RuntimeException {

    public ConflitoConcorrenciaException(String message, Throwable cause) {
        super(message, cause);
    }
}
