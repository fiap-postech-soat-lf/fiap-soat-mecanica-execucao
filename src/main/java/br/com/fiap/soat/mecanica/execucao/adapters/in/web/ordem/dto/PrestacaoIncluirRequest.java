package br.com.fiap.soat.mecanica.execucao.adapters.in.web.ordem.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

public record PrestacaoIncluirRequest(
        @NotNull(message = "O serviço é obrigatório")
        UUID servicoId,
        @NotNull(message = "O preço da mão de obra é obrigatório")
        @Positive(message = "O preço da mão de obra deve ser maior que zero")
        BigDecimal precoMaoDeObra
) {
}
