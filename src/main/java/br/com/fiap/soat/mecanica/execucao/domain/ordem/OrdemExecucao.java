package br.com.fiap.soat.mecanica.execucao.domain.ordem;

import br.com.fiap.soat.mecanica.execucao.domain.exception.RecursoNaoEncontradoException;
import br.com.fiap.soat.mecanica.execucao.domain.exception.RegraNegocioException;
import br.com.fiap.soat.mecanica.execucao.domain.peca.Peca;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Ordem de servico do ponto de vista do Execucao: diagnostico (prestacoes e pecas), reserva de estoque,
 * execucao e finalizacao. Segue a tabela 7.1 da F5. As prestacoes e as alocacoes vivem dentro do agregado,
 * gravado como um item so (A3); a {@code versao} protege as escritas concorrentes (F5-g).
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrdemExecucao {

    private UUID osId;
    private UUID sagaId;
    private UUID veiculoId;
    private String placa;
    private StatusOrdem status;
    private Instant abertaEm;
    private Instant diagnosticoConcluidoEm;
    private Instant iniciadaEm;
    private Instant finalizadaEm;
    private String motivo;
    private List<Prestacao> prestacoes = new ArrayList<>();
    private Map<String, ComandoAtendido> comandosAtendidos = new LinkedHashMap<>();
    private long versao;

    /** Ordem nova, vinda do comando {@code AbrirDiagnostico}: entra na fila com a prioridade 3. */
    public static OrdemExecucao abrir(UUID osId, UUID sagaId, UUID veiculoId, String placa, Clock clock) {
        if (osId == null) {
            throw new RegraNegocioException("Ordem de serviço obrigatória");
        }
        OrdemExecucao ordem = new OrdemExecucao();
        ordem.osId = osId;
        ordem.sagaId = sagaId;
        ordem.veiculoId = veiculoId;
        ordem.placa = placa;
        ordem.status = StatusOrdem.EM_DIAGNOSTICO;
        ordem.abertaEm = clock.instant();
        return ordem;
    }

    /** Lapide: o cancelamento chegou antes da abertura. Um {@code AbrirDiagnostico} atrasado nao a reabre. */
    public static OrdemExecucao lapide(UUID osId, Clock clock) {
        OrdemExecucao ordem = new OrdemExecucao();
        ordem.osId = osId;
        ordem.status = StatusOrdem.CANCELADA;
        ordem.abertaEm = clock.instant();
        ordem.motivo = "Cancelada antes da abertura do diagnóstico";
        return ordem;
    }

    @SuppressWarnings("java:S107")
    public static OrdemExecucao reconstruir(UUID osId, UUID sagaId, UUID veiculoId, String placa,
                                            StatusOrdem status, Instant abertaEm, Instant diagnosticoConcluidoEm,
                                            Instant iniciadaEm, Instant finalizadaEm, String motivo,
                                            List<Prestacao> prestacoes,
                                            Map<String, ComandoAtendido> comandosAtendidos, long versao) {
        OrdemExecucao ordem = new OrdemExecucao();
        ordem.osId = osId;
        ordem.sagaId = sagaId;
        ordem.veiculoId = veiculoId;
        ordem.placa = placa;
        ordem.status = status;
        ordem.abertaEm = abertaEm;
        ordem.diagnosticoConcluidoEm = diagnosticoConcluidoEm;
        ordem.iniciadaEm = iniciadaEm;
        ordem.finalizadaEm = finalizadaEm;
        ordem.motivo = motivo;
        ordem.prestacoes = new ArrayList<>(prestacoes);
        ordem.comandosAtendidos = new LinkedHashMap<>(comandosAtendidos);
        ordem.versao = versao;
        return ordem;
    }

    public List<Prestacao> getPrestacoes() {
        return List.copyOf(prestacoes);
    }

    public Map<String, ComandoAtendido> getComandosAtendidos() {
        return Map.copyOf(comandosAtendidos);
    }

    // ---- consulta ----------------------------------------------------------------------------------

    public boolean naFila() {
        return status.naFila();
    }

    public int prioridadeFila() {
        return status.prioridadeFila();
    }

    public List<Prestacao> prestacoesAtivas() {
        return prestacoes.stream().filter(Prestacao::isAtivo).toList();
    }

    public Prestacao buscarPrestacao(UUID prestacaoId) {
        return prestacoes.stream().filter(p -> p.getId().equals(prestacaoId)).findFirst()
                .orElseThrow(() -> new RecursoNaoEncontradoException("Prestação de serviço não encontrada"));
    }

    /** Pecas a reservar: quantidades somadas por {@code pecaId} entre as alocacoes ativas das prestacoes ativas (F5-e). */
    public Map<UUID, Integer> pecasParaReservar() {
        Map<UUID, Integer> soma = new LinkedHashMap<>();
        prestacoesAtivas().forEach(p -> p.getAlocacoes().stream().filter(Alocacao::isAtivo)
                .forEach(a -> soma.merge(a.getPecaId(), a.getQuantidade(), Integer::sum)));
        return soma;
    }

    /** Itens do {@code DiagnosticoConcluido}: uma entrada por prestacao ativa, com as pecas ativas dela. */
    public List<ItemDiagnostico> itensDoDiagnostico() {
        return prestacoesAtivas().stream()
                .map(p -> new ItemDiagnostico(p.getServicoId(), p.getNomeServico(), p.getPrecoMaoDeObra(),
                        p.getAlocacoes().stream().filter(Alocacao::isAtivo)
                                .map(a -> new PecaDiagnostico(a.getPecaId(), a.getDescricao(), a.getQuantidade(),
                                        a.getValorUnitario()))
                                .toList()))
                .toList();
    }

    public Optional<ComandoAtendido> comandoAtendido(String tipo) {
        return Optional.ofNullable(comandosAtendidos.get(tipo));
    }

    /** Guarda (ou substitui, numa nova rodada) o ultimo comando atendido do tipo (F5-h). */
    public void registrarComando(String tipo, ComandoAtendido comando) {
        comandosAtendidos.put(tipo, comando);
    }

    // ---- diagnostico -------------------------------------------------------------------------------

    public Prestacao incluirPrestacao(UUID servicoId, String nomeServico, BigDecimal precoMaoDeObra) {
        exigir(StatusOrdem.EM_DIAGNOSTICO, "incluir prestação");
        boolean jaAdicionado = prestacoes.stream().anyMatch(p -> p.isAtivo() && p.getServicoId().equals(servicoId));
        if (jaAdicionado) {
            throw new RegraNegocioException("Serviço já adicionado na OS");
        }
        Prestacao prestacao = new Prestacao(servicoId, nomeServico, precoMaoDeObra);
        prestacoes.add(prestacao);
        return prestacao;
    }

    public Prestacao inativarPrestacao(UUID prestacaoId) {
        exigir(StatusOrdem.EM_DIAGNOSTICO, "inativar prestação");
        Prestacao prestacao = buscarPrestacao(prestacaoId);
        prestacao.inativarComAlocacoes();
        return prestacao;
    }

    public Alocacao alocarPeca(UUID prestacaoId, Peca peca, int quantidade) {
        exigir(StatusOrdem.EM_DIAGNOSTICO, "alocar peça");
        Prestacao prestacao = buscarPrestacao(prestacaoId);
        prestacao.validarPodeAlterar();
        if (peca.isInativo()) {
            throw new RegraNegocioException("A peça " + peca.getNome() + " está inativa");
        }
        return prestacao.alocar(peca.getId(), peca.getNome(), quantidade, peca.getValorUnitario());
    }

    public Alocacao inativarAlocacao(UUID alocacaoId) {
        exigir(StatusOrdem.EM_DIAGNOSTICO, "inativar alocação");
        for (Prestacao prestacao : prestacoes) {
            for (Alocacao alocacao : prestacao.getAlocacoes()) {
                if (alocacao.getId().equals(alocacaoId)) {
                    if (alocacao.isInativo()) {
                        throw new RegraNegocioException("Alocação de peça já está inativa");
                    }
                    prestacao.validarPodeAlterar();
                    alocacao.inativar();
                    return alocacao;
                }
            }
        }
        throw new RecursoNaoEncontradoException("Alocação de peça não encontrada");
    }

    public void concluirDiagnostico(Clock clock) {
        exigir(StatusOrdem.EM_DIAGNOSTICO, "concluir o diagnóstico");
        if (prestacoesAtivas().isEmpty()) {
            throw new RegraNegocioException("O diagnóstico precisa de pelo menos uma prestação de serviço ativa");
        }
        this.status = StatusOrdem.DIAGNOSTICO_CONCLUIDO;
        this.diagnosticoConcluidoEm = clock.instant();
        this.motivo = null;
    }

    /** Reabre o diagnostico (F5-k); so sem reserva ativa, porque a Saga do OS ainda conta com ela. */
    public void reabrirDiagnostico(boolean reservaAtiva) {
        exigir(StatusOrdem.DIAGNOSTICO_CONCLUIDO, "reabrir o diagnóstico");
        if (reservaAtiva) {
            throw new RegraNegocioException("Há uma reserva de peças ativa; ela precisa ser liberada antes");
        }
        this.status = StatusOrdem.EM_DIAGNOSTICO;
    }

    // ---- Saga: reserva -----------------------------------------------------------------------------

    /** {@code ReservarPecas}: so em {@code DIAGNOSTICO_CONCLUIDO}; o estado nao muda (as linhas de reserva e o estoque sim). */
    public void reservarPecas() {
        exigir(StatusOrdem.DIAGNOSTICO_CONCLUIDO, "reservar peças");
    }

    /** Reserva recusada por falta de estoque: volta a {@code EM_DIAGNOSTICO} com o motivo (as faltantes). */
    public void recusarReserva(String motivoRecusa) {
        exigir(StatusOrdem.DIAGNOSTICO_CONCLUIDO, "recusar a reserva");
        this.status = StatusOrdem.EM_DIAGNOSTICO;
        this.motivo = motivoRecusa;
    }

    /**
     * {@code LiberarReservaPecas}: {@code REVISAO} e {@code ORCAMENTO_FALHOU} reabrem o diagnostico; os outros motivos
     * cancelam a ordem. Nao vale depois do inicio da execucao. Ordem ja cancelada fica como esta.
     */
    public void liberarReserva(MotivoLiberacao motivoLiberacao) {
        if (status == StatusOrdem.EM_EXECUCAO || status == StatusOrdem.FINALIZADA) {
            throw new RegraNegocioException("Não é possível liberar a reserva de uma ordem " + status);
        }
        if (status == StatusOrdem.CANCELADA) {
            return;
        }
        if (motivoLiberacao.cancelaOrdem()) {
            this.status = StatusOrdem.CANCELADA;
            this.motivo = motivoLiberacao.name();
        } else {
            this.status = StatusOrdem.EM_DIAGNOSTICO;
        }
    }

    /**
     * {@code OrdemServicoCancelada}: cancela uma ordem em diagnostico. Nao libera a reserva (o OS manda o
     * {@code LiberarReservaPecas} a parte). Ordem ja cancelada fica como esta.
     */
    public void cancelar() {
        if (status == StatusOrdem.EM_EXECUCAO || status == StatusOrdem.FINALIZADA) {
            throw new RegraNegocioException("Não é possível cancelar uma ordem " + status);
        }
        if (status != StatusOrdem.CANCELADA) {
            this.status = StatusOrdem.CANCELADA;
            this.motivo = "OrdemServicoCancelada";
        }
    }

    // ---- execucao ----------------------------------------------------------------------------------

    /** {@code IniciarExecucao}: inicia as prestacoes ativas e sobe para a prioridade 1. */
    public void iniciarExecucao(Clock clock) {
        exigir(StatusOrdem.DIAGNOSTICO_CONCLUIDO, "iniciar a execução");
        Instant agora = clock.instant();
        prestacoesAtivas().forEach(p -> p.iniciar(agora));
        this.status = StatusOrdem.EM_EXECUCAO;
        this.iniciadaEm = agora;
    }

    /**
     * Finaliza uma prestacao; a ultima prestacao ativa finaliza a ordem.
     *
     * @return {@code true} se a ordem foi finalizada
     */
    public boolean finalizarPrestacao(UUID prestacaoId, Clock clock) {
        exigir(StatusOrdem.EM_EXECUCAO, "finalizar prestação");
        Instant agora = clock.instant();
        buscarPrestacao(prestacaoId).finalizar(agora);
        boolean todasFinalizadas = prestacoesAtivas().stream().allMatch(Prestacao::isFinalizada);
        if (todasFinalizadas) {
            this.status = StatusOrdem.FINALIZADA;
            this.finalizadaEm = agora;
        }
        return todasFinalizadas;
    }

    /** Tempo gasto em cada prestacao ativa, para o {@code ExecucaoFinalizada}. */
    public List<TempoServico> tempos() {
        return prestacoesAtivas().stream().map(p -> new TempoServico(p.getServicoId(), p.minutos())).toList();
    }

    private void exigir(StatusOrdem esperado, String acao) {
        if (status != esperado) {
            throw new RegraNegocioException(
                    "Não é possível " + acao + " com a ordem " + status + " (exige " + esperado + ")");
        }
    }

    public record ItemDiagnostico(UUID servicoId, String descricao, BigDecimal precoMaoDeObra,
                                  List<PecaDiagnostico> pecas) {
    }

    public record PecaDiagnostico(UUID pecaId, String descricao, int quantidade, BigDecimal valorUnitario) {
    }

    public record TempoServico(UUID servicoId, long minutos) {
    }
}
