package br.com.fiap.soat.mecanica.execucao.application.ordem.dto;

import br.com.fiap.soat.mecanica.execucao.domain.ordem.OrdemExecucao;

import java.util.List;
import java.util.UUID;

/** Payload do {@code ExecucaoFinalizada} v1 (docs/contratos/ExecucaoFinalizada.v1.schema.json). */
public record ExecucaoFinalizadaPayload(UUID osId, String finalizadaEm, List<Tempo> tempos) {

    public record Tempo(UUID servicoId, long minutos) {
    }

    public static ExecucaoFinalizadaPayload de(OrdemExecucao ordem) {
        return new ExecucaoFinalizadaPayload(ordem.getOsId(), ordem.getFinalizadaEm().toString(),
                ordem.tempos().stream().map(t -> new Tempo(t.servicoId(), t.minutos())).toList());
    }
}
