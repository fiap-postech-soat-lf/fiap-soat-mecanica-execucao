package br.com.fiap.soat.mecanica.execucao.adapters.in.web.ordem.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record TempoMedioResponse(UUID servicoId, String nomeServico, BigDecimal minutosMedio, long amostras) {
}
