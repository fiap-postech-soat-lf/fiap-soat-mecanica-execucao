package br.com.fiap.soat.mecanica.execucao.domain.ordem;

import java.util.List;

/** Uma pagina da fila de execucao; {@code proximoCursor} e opaco e nulo na ultima pagina. */
public record PaginaFila(List<OrdemExecucao> ordens, String proximoCursor) {
}
