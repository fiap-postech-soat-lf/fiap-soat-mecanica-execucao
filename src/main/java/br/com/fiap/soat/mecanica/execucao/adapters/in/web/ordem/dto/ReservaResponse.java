package br.com.fiap.soat.mecanica.execucao.adapters.in.web.ordem.dto;

import br.com.fiap.soat.mecanica.execucao.domain.ordem.StatusReserva;

import java.time.Instant;
import java.util.UUID;

public record ReservaResponse(UUID pecaId, int quantidade, StatusReserva status, Instant reservadaEm,
                              Instant atualizadaEm) {
}
