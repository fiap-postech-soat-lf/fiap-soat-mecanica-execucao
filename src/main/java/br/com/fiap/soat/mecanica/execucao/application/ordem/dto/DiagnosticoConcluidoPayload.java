package br.com.fiap.soat.mecanica.execucao.application.ordem.dto;

import br.com.fiap.soat.mecanica.execucao.domain.ordem.OrdemExecucao;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Payload do {@code DiagnosticoConcluido} v1 (docs/contratos/DiagnosticoConcluido.v1.schema.json). */
public record DiagnosticoConcluidoPayload(UUID osId, List<Item> itens) {

    public record Item(UUID servicoId, String descricao, BigDecimal precoMaoDeObra, List<Peca> pecas) {
    }

    public record Peca(UUID pecaId, String descricao, int quantidade, BigDecimal valorUnitario) {
    }

    public static DiagnosticoConcluidoPayload de(OrdemExecucao ordem) {
        List<Item> itens = ordem.itensDoDiagnostico().stream()
                .map(i -> new Item(i.servicoId(), i.descricao(), i.precoMaoDeObra(), i.pecas().stream()
                        .map(p -> new Peca(p.pecaId(), p.descricao(), p.quantidade(), p.valorUnitario()))
                        .toList()))
                .toList();
        return new DiagnosticoConcluidoPayload(ordem.getOsId(), itens);
    }
}
