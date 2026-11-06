package br.com.fiap.soat.mecanica.execucao.domain.ordem;

import java.util.List;
import java.util.UUID;

/**
 * Idempotencia por chave de negocio (F5-h): o ultimo comando de cada tipo ja atendido, com a cadeia de ids
 * (messageIds dos reenvios) e a resposta guardada para ser reemitida.
 *
 * @param ids             messageId do comando original e dos reenvios
 * @param tipoResposta    tipo da mensagem de resposta (ex.: {@code PecasReservadas})
 * @param payloadResposta payload da resposta, em JSON
 */
public record ComandoAtendido(List<UUID> ids, String tipoResposta, String payloadResposta) {

    public ComandoAtendido {
        ids = List.copyOf(ids);
    }

    public boolean contem(UUID messageId, UUID causationId) {
        return ids.contains(messageId) || (causationId != null && ids.contains(causationId));
    }

    public ComandoAtendido comReenvio(UUID messageId) {
        if (ids.contains(messageId)) {
            return this;
        }
        List<UUID> novos = new java.util.ArrayList<>(ids);
        novos.add(messageId);
        return new ComandoAtendido(novos, tipoResposta, payloadResposta);
    }
}
