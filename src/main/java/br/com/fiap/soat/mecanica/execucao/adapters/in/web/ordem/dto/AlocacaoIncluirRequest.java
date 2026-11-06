package br.com.fiap.soat.mecanica.execucao.adapters.in.web.ordem.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record AlocacaoIncluirRequest(
        @NotNull(message = "A peça não pode ser nula")
        UUID pecaId,
        @NotNull(message = "A quantidade não pode ser nula")
        @Positive(message = "A quantidade deve ser maior que zero")
        Integer quantidade
) {
}
