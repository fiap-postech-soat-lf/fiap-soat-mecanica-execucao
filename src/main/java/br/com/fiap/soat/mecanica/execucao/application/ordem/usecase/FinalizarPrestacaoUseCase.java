package br.com.fiap.soat.mecanica.execucao.application.ordem.usecase;

import br.com.fiap.soat.mecanica.execucao.application.mensageria.MensagemFactory;
import br.com.fiap.soat.mecanica.execucao.application.mensageria.OutboxPort;
import br.com.fiap.soat.mecanica.execucao.application.ordem.dto.ExecucaoFinalizadaPayload;
import br.com.fiap.soat.mecanica.execucao.application.transacao.TransacaoPort;
import br.com.fiap.soat.mecanica.execucao.config.observability.BusinessEventLogger;
import br.com.fiap.soat.mecanica.execucao.domain.ordem.OrdemExecucao;
import br.com.fiap.soat.mecanica.execucao.domain.ordem.OrdemRepository;
import br.com.fiap.soat.mecanica.execucao.domain.ordem.Prestacao;
import br.com.fiap.soat.mecanica.execucao.domain.ordem.StatusOrdem;
import br.com.fiap.soat.mecanica.execucao.domain.ordem.TempoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.UUID;

/**
 * Finaliza uma prestacao (F5-d): grava a amostra de tempo do servico e, se for a ultima prestacao ativa, finaliza a
 * ordem e registra o {@code ExecucaoFinalizada} na outbox. Tudo na mesma transacao; a versao da ordem impede que uma
 * repeticao do PATCH conte a amostra duas vezes. No REST nao ha {@code sagaId} no MDC, entao o envelope usa o da ordem.
 */
@Service
@RequiredArgsConstructor
public class FinalizarPrestacaoUseCase {

    static final String DESTINO = "execucao-eventos";
    static final String TIPO = "ExecucaoFinalizada";

    private final OrdemSupport ordens;
    private final OrdemRepository repository;
    private final TempoRepository tempos;
    private final OutboxPort outbox;
    private final MensagemFactory mensagens;
    private final TransacaoPort transacao;
    private final Clock clock;

    public OrdemExecucao executar(UUID osId, UUID prestacaoId) {
        return transacao.executar(() -> {
            OrdemExecucao ordem = ordens.buscar(osId);
            boolean finalizada = ordem.finalizarPrestacao(prestacaoId, clock);
            Prestacao prestacao = ordem.buscarPrestacao(prestacaoId);
            tempos.acumular(prestacao.getServicoId(), prestacao.getNomeServico(), prestacao.minutos());
            repository.salvar(ordem);
            if (finalizada) {
                outbox.registrar(DESTINO, mensagens.criar(TIPO, 1, ordem.getSagaId(), osId,
                        ExecucaoFinalizadaPayload.de(ordem)));
                BusinessEventLogger.ordemStatus(osId, StatusOrdem.EM_EXECUCAO, StatusOrdem.FINALIZADA, null);
            }
            return ordem;
        });
    }
}
