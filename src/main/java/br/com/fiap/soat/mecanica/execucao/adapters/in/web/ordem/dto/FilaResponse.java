package br.com.fiap.soat.mecanica.execucao.adapters.in.web.ordem.dto;

import br.com.fiap.soat.mecanica.execucao.domain.ordem.StatusOrdem;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Pagina da fila de execucao; {@code proximoCursor} e nulo na ultima pagina. */
public record FilaResponse(List<Item> itens, String proximoCursor) {

    /** {@code prestacoes} conta as prestacoes ativas da ordem. */
    public record Item(UUID osId, String placa, StatusOrdem status, Instant abertaEm, int prestacoes) {
    }
}
