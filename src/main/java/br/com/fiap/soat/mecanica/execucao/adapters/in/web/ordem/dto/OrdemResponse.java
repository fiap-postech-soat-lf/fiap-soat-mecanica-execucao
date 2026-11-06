package br.com.fiap.soat.mecanica.execucao.adapters.in.web.ordem.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import br.com.fiap.soat.mecanica.execucao.domain.ordem.StatusOrdem;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** A ordem no Execucao. {@code reservas} so vem em {@code GET /execucoes/{osId}}. */
public record OrdemResponse(UUID osId, UUID sagaId, UUID veiculoId, String placa, StatusOrdem status,
                            Instant abertaEm, Instant diagnosticoConcluidoEm, Instant iniciadaEm,
                            Instant finalizadaEm, String motivo, List<PrestacaoResponse> prestacoes,
                            @JsonInclude(JsonInclude.Include.NON_NULL) List<ReservaResponse> reservas) {
}
