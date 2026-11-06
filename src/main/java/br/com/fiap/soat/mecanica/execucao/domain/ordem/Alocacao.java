package br.com.fiap.soat.mecanica.execucao.domain.ordem;

import br.com.fiap.soat.mecanica.execucao.domain.Principal;
import br.com.fiap.soat.mecanica.execucao.domain.enums.StatusRecursoEnum;
import br.com.fiap.soat.mecanica.execucao.domain.exception.RegraNegocioException;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Peca alocada a uma prestacao. Vive dentro da {@link OrdemExecucao} e guarda o snapshot (descricao e valor
 * unitario) da peca no momento da alocacao; alocar nao mexe no estoque (a reserva vem da Saga).
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Alocacao extends Principal {

    private UUID id;
    private UUID pecaId;
    private String descricao;
    private int quantidade;
    private BigDecimal valorUnitario;

    Alocacao(UUID pecaId, String descricao, int quantidade, BigDecimal valorUnitario) {
        if (pecaId == null) {
            throw new RegraNegocioException("Peça obrigatória");
        }
        if (quantidade <= 0) {
            throw new RegraNegocioException("Quantidade obrigatória");
        }
        this.id = UUID.randomUUID();
        this.pecaId = pecaId;
        this.descricao = descricao;
        this.quantidade = quantidade;
        this.valorUnitario = valorUnitario;
    }

    public static Alocacao reconstruir(UUID id, StatusRecursoEnum status, UUID pecaId, String descricao,
                                       int quantidade, BigDecimal valorUnitario) {
        Alocacao alocacao = new Alocacao();
        alocacao.id = id;
        alocacao.status = status;
        alocacao.pecaId = pecaId;
        alocacao.descricao = descricao;
        alocacao.quantidade = quantidade;
        alocacao.valorUnitario = valorUnitario;
        return alocacao;
    }
}
