package br.com.fiap.soat.mecanica.execucao.domain.ordem;

import br.com.fiap.soat.mecanica.execucao.domain.Principal;
import br.com.fiap.soat.mecanica.execucao.domain.enums.StatusRecursoEnum;
import br.com.fiap.soat.mecanica.execucao.domain.exception.RegraNegocioException;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Servico a executar dentro de uma ordem, com as pecas alocadas a ele. Vive dentro da {@link OrdemExecucao}. */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Prestacao extends Principal {

    private UUID id;
    private UUID servicoId;
    private String nomeServico;
    private BigDecimal precoMaoDeObra;
    private Instant dataInicio;
    private Instant dataFim;
    private List<Alocacao> alocacoes = new ArrayList<>();

    Prestacao(UUID servicoId, String nomeServico, BigDecimal precoMaoDeObra) {
        if (servicoId == null) {
            throw new RegraNegocioException("Serviço obrigatório");
        }
        if (precoMaoDeObra == null || precoMaoDeObra.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RegraNegocioException("Preço de mão de obra é obrigatório");
        }
        this.id = UUID.randomUUID();
        this.servicoId = servicoId;
        this.nomeServico = nomeServico;
        this.precoMaoDeObra = precoMaoDeObra;
    }

    public static Prestacao reconstruir(UUID id, StatusRecursoEnum status, UUID servicoId, String nomeServico,
                                        BigDecimal precoMaoDeObra, Instant dataInicio, Instant dataFim,
                                        List<Alocacao> alocacoes) {
        Prestacao prestacao = new Prestacao();
        prestacao.id = id;
        prestacao.status = status;
        prestacao.servicoId = servicoId;
        prestacao.nomeServico = nomeServico;
        prestacao.precoMaoDeObra = precoMaoDeObra;
        prestacao.dataInicio = dataInicio;
        prestacao.dataFim = dataFim;
        prestacao.alocacoes = new ArrayList<>(alocacoes);
        return prestacao;
    }

    public List<Alocacao> getAlocacoes() {
        return List.copyOf(alocacoes);
    }

    public boolean isFinalizada() {
        return dataFim != null;
    }

    void validarPodeAlterar() {
        if (isInativo()) {
            throw new RegraNegocioException("Prestação de serviço está inativa");
        }
        if (isFinalizada()) {
            throw new RegraNegocioException("Prestação de serviço já finalizada");
        }
    }

    Alocacao alocar(UUID pecaId, String descricao, int quantidade, BigDecimal valorUnitario) {
        boolean jaAlocada = alocacoes.stream().anyMatch(a -> a.isAtivo() && a.getPecaId().equals(pecaId));
        if (jaAlocada) {
            throw new RegraNegocioException("Peça já alocada nessa prestação");
        }
        Alocacao alocacao = new Alocacao(pecaId, descricao, quantidade, valorUnitario);
        alocacoes.add(alocacao);
        return alocacao;
    }

    /** Inativa a prestacao e as alocacoes ativas dela. */
    void inativarComAlocacoes() {
        validarPodeAlterar();
        alocacoes.stream().filter(Alocacao::isAtivo).forEach(Alocacao::inativar);
        inativar();
    }

    void iniciar(Instant agora) {
        if (isInativo()) {
            throw new RegraNegocioException("Não é possível iniciar um serviço inativo");
        }
        if (dataInicio != null) {
            throw new RegraNegocioException("Serviço já iniciado");
        }
        this.dataInicio = agora;
    }

    void finalizar(Instant agora) {
        if (isInativo()) {
            throw new RegraNegocioException("Não é possível finalizar um serviço inativo");
        }
        if (dataInicio == null) {
            throw new RegraNegocioException("Serviço ainda não foi iniciado");
        }
        if (dataFim != null) {
            throw new RegraNegocioException("Serviço já finalizado");
        }
        this.dataFim = agora;
    }

    /** {@code floor((dataFim - dataInicio) / 60 s)}, nunca negativo; 0 se ainda nao finalizada. */
    public long minutos() {
        if (dataInicio == null || dataFim == null) {
            return 0;
        }
        return Math.max(0, Duration.between(dataInicio, dataFim).toMinutes());
    }
}
