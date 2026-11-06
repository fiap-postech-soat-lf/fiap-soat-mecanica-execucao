package br.com.fiap.soat.mecanica.execucao.application.saga.dto;

import java.util.UUID;

/** Payload de {@code IniciarExecucao} v1 (contrato do OS). */
public record IniciarExecucaoPayload(UUID osId) {
}
