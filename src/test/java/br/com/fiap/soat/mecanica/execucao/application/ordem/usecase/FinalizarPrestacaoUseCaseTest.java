package br.com.fiap.soat.mecanica.execucao.application.ordem.usecase;

import br.com.fiap.soat.mecanica.execucao.application.mensageria.MensagemEnvelope;
import br.com.fiap.soat.mecanica.execucao.application.mensageria.MensagemFactory;
import br.com.fiap.soat.mecanica.execucao.application.mensageria.OutboxPort;
import br.com.fiap.soat.mecanica.execucao.application.transacao.TransacaoPort;
import br.com.fiap.soat.mecanica.execucao.domain.exception.RecursoNaoEncontradoException;
import br.com.fiap.soat.mecanica.execucao.domain.exception.RegraNegocioException;
import br.com.fiap.soat.mecanica.execucao.domain.ordem.OrdemExecucao;
import br.com.fiap.soat.mecanica.execucao.domain.ordem.OrdemRepository;
import br.com.fiap.soat.mecanica.execucao.domain.ordem.Prestacao;
import br.com.fiap.soat.mecanica.execucao.domain.ordem.StatusOrdem;
import br.com.fiap.soat.mecanica.execucao.domain.ordem.TempoRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FinalizarPrestacaoUseCaseTest {

    private static final Instant T0 = Instant.parse("2026-11-06T12:00:00Z");

    @Mock
    private OrdemRepository repository;
    @Mock
    private TempoRepository tempos;
    @Mock
    private OutboxPort outbox;
    @Mock
    private TransacaoPort transacao;

    private final UUID servicoA = UUID.randomUUID();
    private final UUID servicoB = UUID.randomUUID();
    private FinalizarPrestacaoUseCase useCase;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void montar() {
        // A transacao so repassa a acao (o commit e testado no TransacaoDynamoIT)
        lenient().when(transacao.executar(any(Supplier.class)))
                .thenAnswer(i -> ((Supplier<?>) i.getArgument(0)).get());
        useCase = com(Clock.fixed(T0.plusSeconds(45 * 60), ZoneOffset.UTC));
    }

    private FinalizarPrestacaoUseCase com(Clock clock) {
        return new FinalizarPrestacaoUseCase(new OrdemSupport(repository), repository, tempos, outbox,
                new MensagemFactory(new ObjectMapper()), transacao, clock);
    }

    /** Ordem em execucao desde T0 com uma prestacao por servico informado. */
    private OrdemExecucao emExecucao(UUID... servicos) {
        Clock abertura = Clock.fixed(T0, ZoneOffset.UTC);
        OrdemExecucao ordem = OrdemExecucao.abrir(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "ABC1D23",
                abertura);
        for (UUID servico : servicos) {
            ordem.incluirPrestacao(servico, "Servico " + servico.toString().substring(0, 4), new BigDecimal("50.00"));
        }
        ordem.concluirDiagnostico(abertura);
        ordem.iniciarExecucao(abertura);
        when(repository.buscarPorId(ordem.getOsId())).thenReturn(Optional.of(ordem));
        return ordem;
    }

    @Test
    void finalizarAUltimaPrestacaoFechaAOrdemGravaOTempoEEmiteOEvento() {
        OrdemExecucao ordem = emExecucao(servicoA);
        Prestacao prestacao = ordem.prestacoesAtivas().get(0);

        OrdemExecucao resultado = useCase.executar(ordem.getOsId(), prestacao.getId());

        assertThat(resultado.getStatus()).isEqualTo(StatusOrdem.FINALIZADA);
        verify(tempos).acumular(eq(servicoA), any(), eq(45L));
        verify(repository).salvar(ordem);
        ArgumentCaptor<MensagemEnvelope> envelope = ArgumentCaptor.forClass(MensagemEnvelope.class);
        verify(outbox).registrar(eq("execucao-eventos"), envelope.capture());
        assertThat(envelope.getValue().type()).isEqualTo("ExecucaoFinalizada");
        assertThat(envelope.getValue().sagaId()).isEqualTo(ordem.getSagaId());
        assertThat(envelope.getValue().payload().at("/finalizadaEm").asText()).isEqualTo("2026-11-06T12:45:00Z");
        assertThat(envelope.getValue().payload().at("/tempos/0/servicoId").asText()).isEqualTo(servicoA.toString());
        assertThat(envelope.getValue().payload().at("/tempos/0/minutos").asLong()).isEqualTo(45);
    }

    @Test
    void finalizarUmaPrestacaoSemSerAUltimaSoGravaOTempo() {
        OrdemExecucao ordem = emExecucao(servicoA, servicoB);
        Prestacao primeira = ordem.prestacoesAtivas().get(0);

        OrdemExecucao resultado = useCase.executar(ordem.getOsId(), primeira.getId());

        assertThat(resultado.getStatus()).isEqualTo(StatusOrdem.EM_EXECUCAO);
        verify(tempos).acumular(eq(primeira.getServicoId()), any(), eq(45L));
        verify(repository).salvar(ordem);
        verifyNoInteractions(outbox);
    }

    @Test
    void aUltimaPrestacaoLevaOsTemposDeTodasAsAtivas() {
        OrdemExecucao ordem = emExecucao(servicoA, servicoB);
        Prestacao primeira = ordem.prestacoesAtivas().get(0);
        Prestacao segunda = ordem.prestacoesAtivas().get(1);
        useCase.executar(ordem.getOsId(), primeira.getId());

        com(Clock.fixed(T0.plusSeconds(90 * 60), ZoneOffset.UTC)).executar(ordem.getOsId(), segunda.getId());

        ArgumentCaptor<MensagemEnvelope> envelope = ArgumentCaptor.forClass(MensagemEnvelope.class);
        verify(outbox).registrar(eq("execucao-eventos"), envelope.capture());
        assertThat(envelope.getValue().payload().at("/tempos").size()).isEqualTo(2);
        assertThat(envelope.getValue().payload().at("/tempos/0/minutos").asLong()).isEqualTo(45);
        assertThat(envelope.getValue().payload().at("/tempos/1/minutos").asLong()).isEqualTo(90);
    }

    @Test
    void foraDeEmExecucaoE422() {
        OrdemExecucao ordem = OrdemExecucao.abrir(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "ABC1D23",
                Clock.fixed(T0, ZoneOffset.UTC));
        Prestacao prestacao = ordem.incluirPrestacao(servicoA, "Servico", new BigDecimal("50.00"));
        when(repository.buscarPorId(ordem.getOsId())).thenReturn(Optional.of(ordem));

        assertThatThrownBy(() -> useCase.executar(ordem.getOsId(), prestacao.getId()))
                .isInstanceOf(RegraNegocioException.class).hasMessageContaining("EM_EXECUCAO");
        verify(tempos, never()).acumular(any(), any(), anyLong());
        verifyNoInteractions(outbox);
    }

    @Test
    void prestacaoJaFinalizadaE422SemGravarOTempoDeNovo() {
        OrdemExecucao ordem = emExecucao(servicoA, servicoB);
        Prestacao primeira = ordem.prestacoesAtivas().get(0);
        useCase.executar(ordem.getOsId(), primeira.getId());

        assertThatThrownBy(() -> useCase.executar(ordem.getOsId(), primeira.getId()))
                .isInstanceOf(RegraNegocioException.class).hasMessageContaining("já finalizado");
        verify(tempos).acumular(any(), any(), anyLong());
    }

    @Test
    void prestacaoDesconhecidaE404() {
        OrdemExecucao ordem = emExecucao(servicoA);

        assertThatThrownBy(() -> useCase.executar(ordem.getOsId(), UUID.randomUUID()))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void ordemDesconhecidaE404() {
        UUID osId = UUID.randomUUID();
        when(repository.buscarPorId(osId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar(osId, UUID.randomUUID()))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }
}
