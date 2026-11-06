package br.com.fiap.soat.mecanica.execucao.application.saga.dto;

import br.com.fiap.soat.mecanica.execucao.domain.ordem.MotivoLiberacao;

import java.util.UUID;

/** Payload de {@code LiberarReservaPecas} v1 (contrato do OS). */
public record LiberarReservaPecasPayload(UUID osId, MotivoLiberacao motivo) {
}
