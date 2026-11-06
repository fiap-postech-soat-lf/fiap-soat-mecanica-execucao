package br.com.fiap.soat.mecanica.execucao.domain.ordem;

import java.util.Optional;
import java.util.UUID;

public interface OrdemRepository {

    /** Leitura consistente. */
    Optional<OrdemExecucao> buscarPorId(UUID osId);

    /**
     * Registra a escrita na transacao aberta. Ordem nova so entra se nao existir; ordem lida so e gravada se a
     * {@code versao} continuar a mesma (senao a transacao falha com conflito, F5-g).
     */
    void salvar(OrdemExecucao ordem);

    /**
     * Fila de execucao (GSI esparso {@code fila}, F5-j), ja ordenada por prioridade e data de abertura.
     * {@code cursor} e o devolvido pela pagina anterior (nulo na primeira); cursor invalido lanca
     * {@link IllegalArgumentException}.
     */
    PaginaFila listarFila(int tamanho, String cursor);
}
