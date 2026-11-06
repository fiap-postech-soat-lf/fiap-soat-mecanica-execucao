package br.com.fiap.soat.mecanica.execucao.application.ordem.exception;

/** Servico inexistente ou inativo no catalogo do OS (422 SERVICO_INVALIDO). */
public class ServicoInvalidoException extends RuntimeException {

    public ServicoInvalidoException(String message) {
        super(message);
    }
}
