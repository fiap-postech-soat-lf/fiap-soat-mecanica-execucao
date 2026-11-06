package br.com.fiap.soat.mecanica.execucao.application.ordem.exception;

/** OS recusou o token repassado, 401 ou 403 (502 INTEGRACAO_OS). */
public class IntegracaoOsException extends RuntimeException {

    public IntegracaoOsException(String message, Throwable cause) {
        super(message, cause);
    }
}
