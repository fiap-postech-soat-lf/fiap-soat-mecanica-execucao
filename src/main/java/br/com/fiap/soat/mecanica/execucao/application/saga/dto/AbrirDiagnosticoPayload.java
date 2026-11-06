package br.com.fiap.soat.mecanica.execucao.application.saga.dto;

import java.util.UUID;

/** Payload de {@code AbrirDiagnostico} v1 (contrato do OS). */
public record AbrirDiagnosticoPayload(UUID osId, UUID veiculoId, String placa) {
}
