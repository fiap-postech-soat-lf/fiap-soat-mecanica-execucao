package br.com.fiap.soat.mecanica.execucao.application.saga.dto;

import java.util.List;
import java.util.UUID;

/** Payload de {@code ReservarPecas} v1 (contrato do OS). */
public record ReservarPecasPayload(UUID osId, List<PecaQuantidade> pecas) {

    public record PecaQuantidade(UUID pecaId, int quantidade) {
    }
}
