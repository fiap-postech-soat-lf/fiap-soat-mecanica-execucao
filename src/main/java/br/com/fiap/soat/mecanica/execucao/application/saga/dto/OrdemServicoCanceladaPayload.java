package br.com.fiap.soat.mecanica.execucao.application.saga.dto;

import java.time.Instant;
import java.util.UUID;

/** Payload de {@code OrdemServicoCancelada} v1 (evento do OS, F5-b). */
public record OrdemServicoCanceladaPayload(UUID osId, UUID clienteId, String motivo, Instant ocorridaEm) {
}
