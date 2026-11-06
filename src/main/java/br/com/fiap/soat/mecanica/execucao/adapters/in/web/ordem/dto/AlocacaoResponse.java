package br.com.fiap.soat.mecanica.execucao.adapters.in.web.ordem.dto;

import br.com.fiap.soat.mecanica.execucao.domain.enums.StatusRecursoEnum;

import java.math.BigDecimal;
import java.util.UUID;

public record AlocacaoResponse(UUID id, StatusRecursoEnum status, UUID pecaId, String descricao, int quantidade,
                               BigDecimal valorUnitario) {
}
