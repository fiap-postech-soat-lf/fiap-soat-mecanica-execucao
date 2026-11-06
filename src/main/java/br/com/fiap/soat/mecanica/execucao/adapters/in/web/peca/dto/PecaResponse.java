package br.com.fiap.soat.mecanica.execucao.adapters.in.web.peca.dto;

import br.com.fiap.soat.mecanica.execucao.domain.enums.StatusRecursoEnum;

import java.math.BigDecimal;
import java.util.UUID;

/** Peca com o estoque materializado: {@code qtdEstoque = qtdDisponivel + qtdReservada} (F5-e). */
public record PecaResponse(
        UUID id,
        StatusRecursoEnum status,
        String nome,
        String marca,
        BigDecimal valorUnitario,
        int qtdEstoque,
        int qtdReservada,
        int qtdDisponivel
) {
}
