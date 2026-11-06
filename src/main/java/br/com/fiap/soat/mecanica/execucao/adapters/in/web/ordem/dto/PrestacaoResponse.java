package br.com.fiap.soat.mecanica.execucao.adapters.in.web.ordem.dto;

import br.com.fiap.soat.mecanica.execucao.domain.enums.StatusRecursoEnum;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PrestacaoResponse(UUID id, StatusRecursoEnum status, UUID servicoId, String nomeServico,
                                BigDecimal precoMaoDeObra, Instant dataInicio, Instant dataFim,
                                List<AlocacaoResponse> alocacoes) {
}
