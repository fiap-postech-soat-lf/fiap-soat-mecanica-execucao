package br.com.fiap.soat.mecanica.execucao.application.ordem.usecase;

import br.com.fiap.soat.mecanica.execucao.application.mensageria.MensagemFactory;
import br.com.fiap.soat.mecanica.execucao.application.mensageria.OutboxPort;
import br.com.fiap.soat.mecanica.execucao.application.ordem.dto.DiagnosticoConcluidoPayload;
import br.com.fiap.soat.mecanica.execucao.application.transacao.TransacaoPort;
import br.com.fiap.soat.mecanica.execucao.domain.ordem.OrdemExecucao;
import br.com.fiap.soat.mecanica.execucao.domain.ordem.OrdemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.UUID;

/**
 * Conclui o diagnostico e registra o {@code DiagnosticoConcluido} na outbox, na mesma transacao. No REST nao ha
 * {@code sagaId} no MDC, entao o envelope usa o guardado na ordem.
 */
@Service
@RequiredArgsConstructor
public class ConcluirDiagnosticoUseCase {

    static final String DESTINO = "execucao-eventos";
    static final String TIPO = "DiagnosticoConcluido";

    private final OrdemSupport ordens;
    private final OrdemRepository repository;
    private final OutboxPort outbox;
    private final MensagemFactory mensagens;
    private final TransacaoPort transacao;
    private final Clock clock;

    public OrdemExecucao executar(UUID osId) {
        return transacao.executar(() -> {
            OrdemExecucao ordem = ordens.buscar(osId);
            ordem.concluirDiagnostico(clock);
            repository.salvar(ordem);
            outbox.registrar(DESTINO, mensagens.criar(TIPO, 1, ordem.getSagaId(), osId,
                    DiagnosticoConcluidoPayload.de(ordem)));
            return ordem;
        });
    }
}
