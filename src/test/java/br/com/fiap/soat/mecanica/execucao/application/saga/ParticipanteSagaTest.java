package br.com.fiap.soat.mecanica.execucao.application.saga;

import br.com.fiap.soat.mecanica.execucao.application.mensageria.MensagemEnvelope;
import br.com.fiap.soat.mecanica.execucao.application.mensageria.MensagemFactory;
import br.com.fiap.soat.mecanica.execucao.application.mensageria.OutboxPort;
import br.com.fiap.soat.mecanica.execucao.application.saga.dto.AbrirDiagnosticoPayload;
import br.com.fiap.soat.mecanica.execucao.application.saga.dto.IniciarExecucaoPayload;
import br.com.fiap.soat.mecanica.execucao.application.saga.dto.LiberarReservaPecasPayload;
import br.com.fiap.soat.mecanica.execucao.application.saga.dto.OrdemServicoCanceladaPayload;
import br.com.fiap.soat.mecanica.execucao.application.saga.dto.ReservarPecasPayload;
import br.com.fiap.soat.mecanica.execucao.application.saga.dto.ReservarPecasPayload.PecaQuantidade;
import br.com.fiap.soat.mecanica.execucao.config.observability.Mdc;
import br.com.fiap.soat.mecanica.execucao.domain.exception.RecursoNaoEncontradoException;
import br.com.fiap.soat.mecanica.execucao.domain.exception.RegraNegocioException;
import br.com.fiap.soat.mecanica.execucao.domain.ordem.ComandoAtendido;
import br.com.fiap.soat.mecanica.execucao.domain.ordem.MotivoLiberacao;
import br.com.fiap.soat.mecanica.execucao.domain.ordem.OrdemExecucao;
import br.com.fiap.soat.mecanica.execucao.domain.ordem.OrdemRepository;
import br.com.fiap.soat.mecanica.execucao.domain.ordem.Prestacao;
import br.com.fiap.soat.mecanica.execucao.domain.ordem.Reserva;
import br.com.fiap.soat.mecanica.execucao.domain.ordem.ReservaRepository;
import br.com.fiap.soat.mecanica.execucao.domain.ordem.StatusOrdem;
import br.com.fiap.soat.mecanica.execucao.domain.ordem.StatusReserva;
import br.com.fiap.soat.mecanica.execucao.domain.peca.Peca;
import br.com.fiap.soat.mecanica.execucao.domain.peca.PecaRepository;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Uma linha da tabela 7.1 da F5 por teste, mais as regras de borda e a idempotencia por chave de negocio (F5-h). */
@ExtendWith(MockitoExtension.class)
class ParticipanteSagaTest {

    private static final Instant AGORA = Instant.parse("2026-11-06T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(AGORA, ZoneOffset.UTC);

    @Mock
    private OrdemRepository ordens;
    @Mock
    private PecaRepository pecas;
    @Mock
    private ReservaRepository reservas;
    @Mock
    private OutboxPort outbox;

    private ParticipanteSaga participante;
    private final UUID osId = UUID.randomUUID();
    private final UUID sagaId = UUID.randomUUID();

    @BeforeEach
    void montar() {
        ObjectMapper mapper = new ObjectMapper();
        participante = new ParticipanteSaga(ordens, pecas, reservas, outbox,
                new RespostasSaga(new MensagemFactory(mapper), mapper), new ComandosAtendidos(mapper), CLOCK);
    }

    // ---- AbrirDiagnostico -------------------------------------------------------------------------

    @Test
    void abrirDiagnosticoCriaAOrdemNaFilaERegistraDiagnosticoAberto() {
        when(ordens.buscarPorId(osId)).thenReturn(Optional.empty());
        UUID veiculo = UUID.randomUUID();
        MensagemEnvelope envelope = envelope("AbrirDiagnostico", null);

        participante.abrirDiagnostico(envelope, new AbrirDiagnosticoPayload(osId, veiculo, "ABC1D23"));

        OrdemExecucao salva = ordemSalva();
        assertThat(salva.getStatus()).isEqualTo(StatusOrdem.EM_DIAGNOSTICO);
        assertThat(salva.prioridadeFila()).isEqualTo(3);
        assertThat(salva.getSagaId()).isEqualTo(sagaId);
        assertThat(salva.getVeiculoId()).isEqualTo(veiculo);
        assertThat(salva.getPlaca()).isEqualTo("ABC1D23");
        assertThat(salva.comandoAtendido("AbrirDiagnostico").orElseThrow().ids()).containsExactly(envelope.messageId());
        MensagemEnvelope resposta = respostaNaOutbox();
        assertThat(resposta.type()).isEqualTo("DiagnosticoAberto");
        assertThat(resposta.causationId()).isEqualTo(envelope.messageId());
        assertThat(resposta.payload().path("abertoEm").asText()).isEqualTo("2026-11-06T12:00:00Z");
    }

    @Test
    void abrirDiagnosticoDeOrdemExistenteReemiteForaDaCadeia() {
        OrdemExecucao existente = abertaComResposta();
        when(ordens.buscarPorId(osId)).thenReturn(Optional.of(existente));
        MensagemEnvelope reenvio = envelope("AbrirDiagnostico", UUID.randomUUID());

        participante.abrirDiagnostico(reenvio, new AbrirDiagnosticoPayload(osId, UUID.randomUUID(), "ABC1D23"));

        MensagemEnvelope resposta = respostaNaOutbox();
        assertThat(resposta.type()).isEqualTo("DiagnosticoAberto");
        assertThat(resposta.messageId()).isNotEqualTo(reenvio.messageId());
        assertThat(resposta.causationId()).isEqualTo(reenvio.messageId());
        assertThat(ordemSalva().comandoAtendido("AbrirDiagnostico").orElseThrow().ids()).contains(reenvio.messageId());
    }

    @Test
    void aberturaAtrasadaParaLapideNaoRespondeENaoReabre() {
        when(ordens.buscarPorId(osId)).thenReturn(Optional.of(OrdemExecucao.lapide(osId, CLOCK)));

        participante.abrirDiagnostico(envelope("AbrirDiagnostico", null),
                new AbrirDiagnosticoPayload(osId, UUID.randomUUID(), "ABC1D23"));

        verify(ordens, never()).salvar(any());
        verifyNoInteractions(outbox);
    }

    // ---- ReservarPecas ----------------------------------------------------------------------------

    @Test
    void reservarComEstoqueRegistraUpdatesLinhasEPecasReservadasSomandoPorPeca() {
        Peca peca = peca(10);
        OrdemExecucao ordem = ordemConcluida(peca);
        when(ordens.buscarPorId(osId)).thenReturn(Optional.of(ordem));
        when(pecas.buscarPorId(peca.getId())).thenReturn(Optional.of(peca));

        participante.reservarPecas(envelope("ReservarPecas", null), new ReservarPecasPayload(osId,
                List.of(new PecaQuantidade(peca.getId(), 2), new PecaQuantidade(peca.getId(), 3))));

        verify(pecas).reservar(peca.getId(), 5);
        ArgumentCaptor<Reserva> reserva = ArgumentCaptor.forClass(Reserva.class);
        verify(reservas).registrar(reserva.capture());
        assertThat(reserva.getValue().quantidade()).isEqualTo(5);
        assertThat(reserva.getValue().status()).isEqualTo(StatusReserva.ATIVA);
        assertThat(reserva.getValue().reservadaEm()).isEqualTo(AGORA);
        MensagemEnvelope resposta = respostaNaOutbox();
        assertThat(resposta.type()).isEqualTo("PecasReservadas");
        assertThat(resposta.payload().at("/pecas/0/quantidade").asInt()).isEqualTo(5);
        assertThat(ordemSalva().getStatus()).isEqualTo(StatusOrdem.DIAGNOSTICO_CONCLUIDO);
    }

    @Test
    void reservarSemEstoqueNaoToqueNoEstoqueEVoltaParaEmDiagnostico() {
        Peca peca = peca(1);
        OrdemExecucao ordem = ordemConcluida(peca);
        when(ordens.buscarPorId(osId)).thenReturn(Optional.of(ordem));
        when(pecas.buscarPorId(peca.getId())).thenReturn(Optional.of(peca));

        participante.reservarPecas(envelope("ReservarPecas", null),
                new ReservarPecasPayload(osId, List.of(new PecaQuantidade(peca.getId(), 2))));

        verify(pecas, never()).reservar(any(), anyInt());
        verify(reservas, never()).registrar(any());
        MensagemEnvelope resposta = respostaNaOutbox();
        assertThat(resposta.type()).isEqualTo("ReservaPecasRecusada");
        assertThat(resposta.payload().at("/faltantes/0/solicitada").asInt()).isEqualTo(2);
        assertThat(resposta.payload().at("/faltantes/0/disponivel").asInt()).isEqualTo(1);
        OrdemExecucao salva = ordemSalva();
        assertThat(salva.getStatus()).isEqualTo(StatusOrdem.EM_DIAGNOSTICO);
        assertThat(salva.getMotivo()).contains(peca.getId().toString());
    }

    @Test
    void reservarPecaInexistenteEFaltanteComDisponivelZero() {
        Peca peca = peca(5);
        OrdemExecucao ordem = ordemConcluida(peca);
        when(ordens.buscarPorId(osId)).thenReturn(Optional.of(ordem));
        when(pecas.buscarPorId(peca.getId())).thenReturn(Optional.empty());

        participante.reservarPecas(envelope("ReservarPecas", null),
                new ReservarPecasPayload(osId, List.of(new PecaQuantidade(peca.getId(), 1))));

        assertThat(respostaNaOutbox().payload().at("/faltantes/0/disponivel").asInt()).isZero();
    }

    @Test
    void reservarNumaOrdemCanceladaRecusaComTodasFaltantes() {
        OrdemExecucao ordem = OrdemExecucao.lapide(osId, CLOCK);
        when(ordens.buscarPorId(osId)).thenReturn(Optional.of(ordem));
        UUID pecaId = UUID.randomUUID();

        participante.reservarPecas(envelope("ReservarPecas", null),
                new ReservarPecasPayload(osId, List.of(new PecaQuantidade(pecaId, 4))));

        MensagemEnvelope resposta = respostaNaOutbox();
        assertThat(resposta.type()).isEqualTo("ReservaPecasRecusada");
        assertThat(resposta.payload().at("/faltantes/0/solicitada").asInt()).isEqualTo(4);
        assertThat(resposta.payload().at("/faltantes/0/disponivel").asInt()).isZero();
        assertThat(ordemSalva().getStatus()).isEqualTo(StatusOrdem.CANCELADA);
        verifyNoInteractions(pecas);
    }

    @Test
    void reservarForaDeDiagnosticoConcluidoEIgnorado() {
        OrdemExecucao ordem = OrdemExecucao.abrir(osId, sagaId, UUID.randomUUID(), "ABC1D23", CLOCK);
        when(ordens.buscarPorId(osId)).thenReturn(Optional.of(ordem));

        participante.reservarPecas(envelope("ReservarPecas", null),
                new ReservarPecasPayload(osId, List.of(new PecaQuantidade(UUID.randomUUID(), 1))));

        verify(ordens, never()).salvar(any());
        verifyNoInteractions(outbox);
    }

    @Test
    void reservarSemPecasOuOrdemDesconhecidaFalha() {
        when(ordens.buscarPorId(osId)).thenReturn(Optional.of(
                OrdemExecucao.abrir(osId, sagaId, UUID.randomUUID(), "ABC1D23", CLOCK)));
        assertThatThrownBy(() -> participante.reservarPecas(envelope("ReservarPecas", null),
                new ReservarPecasPayload(osId, List.of()))).isInstanceOf(RegraNegocioException.class);

        UUID outra = UUID.randomUUID();
        when(ordens.buscarPorId(outra)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> participante.reservarPecas(envelope("ReservarPecas", null),
                new ReservarPecasPayload(outra, List.of(new PecaQuantidade(UUID.randomUUID(), 1)))))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void reenvioDoReservarReemiteARespostaGuardadaSemMexerNoEstoque() {
        Peca peca = peca(10);
        OrdemExecucao ordem = ordemConcluida(peca);
        UUID original = UUID.randomUUID();
        ordem.registrarComando("ReservarPecas", new ComandoAtendido(List.of(original), "PecasReservadas",
                "{\"osId\":\"%s\",\"pecas\":[{\"pecaId\":\"%s\",\"quantidade\":2}]}".formatted(osId, peca.getId())));
        when(ordens.buscarPorId(osId)).thenReturn(Optional.of(ordem));
        MensagemEnvelope reenvio = envelope("ReservarPecas", original);

        participante.reservarPecas(reenvio,
                new ReservarPecasPayload(osId, List.of(new PecaQuantidade(peca.getId(), 2))));

        verifyNoInteractions(pecas, reservas);
        MensagemEnvelope resposta = respostaNaOutbox();
        assertThat(resposta.type()).isEqualTo("PecasReservadas");
        assertThat(resposta.causationId()).isEqualTo(reenvio.messageId());
        assertThat(ordemSalva().comandoAtendido("ReservarPecas").orElseThrow().ids())
                .containsExactly(original, reenvio.messageId());
    }

    @Test
    void reenvioApontandoParaOPrimeiroReenvioTambemEReconhecido() {
        Peca peca = peca(10);
        OrdemExecucao ordem = ordemConcluida(peca);
        UUID original = UUID.randomUUID();
        UUID primeiroReenvio = UUID.randomUUID();
        ordem.registrarComando("ReservarPecas", new ComandoAtendido(List.of(original, primeiroReenvio),
                "PecasReservadas", "{\"osId\":\"%s\",\"pecas\":[]}".formatted(osId)));
        when(ordens.buscarPorId(osId)).thenReturn(Optional.of(ordem));

        participante.reservarPecas(envelope("ReservarPecas", primeiroReenvio),
                new ReservarPecasPayload(osId, List.of(new PecaQuantidade(peca.getId(), 2))));

        verifyNoInteractions(pecas, reservas);
        assertThat(respostaNaOutbox().type()).isEqualTo("PecasReservadas");
    }

    @Test
    void comandoForaDaCadeiaEUmaNovaRodadaQueSubstituiAEntrada() {
        Peca peca = peca(10);
        OrdemExecucao ordem = ordemConcluida(peca);
        UUID antigo = UUID.randomUUID();
        ordem.registrarComando("ReservarPecas", new ComandoAtendido(List.of(antigo), "PecasReservadas", "{}"));
        when(ordens.buscarPorId(osId)).thenReturn(Optional.of(ordem));
        when(pecas.buscarPorId(peca.getId())).thenReturn(Optional.of(peca));
        MensagemEnvelope novo = envelope("ReservarPecas", null);

        participante.reservarPecas(novo, new ReservarPecasPayload(osId, List.of(new PecaQuantidade(peca.getId(), 1))));

        verify(pecas).reservar(peca.getId(), 1);
        assertThat(ordemSalva().comandoAtendido("ReservarPecas").orElseThrow().ids()).containsExactly(novo.messageId());
    }

    // ---- LiberarReservaPecas ----------------------------------------------------------------------

    @Test
    void liberarComMotivoQueReabreDevolveEstoqueEVoltaParaEmDiagnostico() {
        Peca peca = peca(10);
        OrdemExecucao ordem = ordemConcluida(peca);
        when(ordens.buscarPorId(osId)).thenReturn(Optional.of(ordem));
        when(reservas.buscarPorOs(osId)).thenReturn(List.of(Reserva.nova(osId, peca.getId(), 2, AGORA)));

        participante.liberarReserva(envelope("LiberarReservaPecas", null),
                new LiberarReservaPecasPayload(osId, MotivoLiberacao.REVISAO));

        verify(pecas).liberar(peca.getId(), 2);
        verify(reservas).atualizarStatus(osId, peca.getId(), StatusReserva.LIBERADA, AGORA);
        assertThat(ordemSalva().getStatus()).isEqualTo(StatusOrdem.EM_DIAGNOSTICO);
        assertThat(respostaNaOutbox().type()).isEqualTo("ReservaPecasLiberada");
    }

    @Test
    void liberarComMotivoQueCancelaTiraDaFila() {
        OrdemExecucao ordem = ordemConcluida(peca(10));
        when(ordens.buscarPorId(osId)).thenReturn(Optional.of(ordem));
        when(reservas.buscarPorOs(osId)).thenReturn(List.of());

        participante.liberarReserva(envelope("LiberarReservaPecas", null),
                new LiberarReservaPecasPayload(osId, MotivoLiberacao.ORCAMENTO_RECUSADO));

        OrdemExecucao salva = ordemSalva();
        assertThat(salva.getStatus()).isEqualTo(StatusOrdem.CANCELADA);
        assertThat(salva.naFila()).isFalse();
    }

    @Test
    void liberarSemReservaAtivaRespondeMesmoAssimEIgnoraLinhasJaBaixadasOuLiberadas() {
        OrdemExecucao ordem = ordemConcluida(peca(10));
        when(ordens.buscarPorId(osId)).thenReturn(Optional.of(ordem));
        when(reservas.buscarPorOs(osId)).thenReturn(List.of(
                new Reserva(osId, UUID.randomUUID(), 1, StatusReserva.LIBERADA, AGORA, AGORA)));

        participante.liberarReserva(envelope("LiberarReservaPecas", null),
                new LiberarReservaPecasPayload(osId, MotivoLiberacao.ORCAMENTO_FALHOU));

        verify(pecas, never()).liberar(any(), anyInt());
        assertThat(respostaNaOutbox().type()).isEqualTo("ReservaPecasLiberada");
    }

    @Test
    void liberarDepoisDoInicioDaExecucaoEIgnorado() {
        OrdemExecucao ordem = ordemConcluida(peca(10));
        ordem.iniciarExecucao(CLOCK);
        when(ordens.buscarPorId(osId)).thenReturn(Optional.of(ordem));

        participante.liberarReserva(envelope("LiberarReservaPecas", null),
                new LiberarReservaPecasPayload(osId, MotivoLiberacao.DESISTENCIA));

        verify(ordens, never()).salvar(any());
        verifyNoInteractions(outbox, pecas);
    }

    @Test
    void reenvioDoLiberarReemiteALiberada() {
        OrdemExecucao ordem = ordemConcluida(peca(10));
        UUID original = UUID.randomUUID();
        ordem.registrarComando("LiberarReservaPecas", new ComandoAtendido(List.of(original), "ReservaPecasLiberada",
                "{\"osId\":\"%s\"}".formatted(osId)));
        when(ordens.buscarPorId(osId)).thenReturn(Optional.of(ordem));

        participante.liberarReserva(envelope("LiberarReservaPecas", original),
                new LiberarReservaPecasPayload(osId, MotivoLiberacao.REVISAO));

        verifyNoInteractions(pecas, reservas);
        assertThat(respostaNaOutbox().type()).isEqualTo("ReservaPecasLiberada");
    }

    // ---- IniciarExecucao --------------------------------------------------------------------------

    @Test
    void iniciarExecucaoBaixaAsReservasAtivasEResponde() {
        Peca peca = peca(10);
        OrdemExecucao ordem = ordemConcluida(peca);
        when(ordens.buscarPorId(osId)).thenReturn(Optional.of(ordem));
        when(reservas.buscarPorOs(osId)).thenReturn(List.of(Reserva.nova(osId, peca.getId(), 2, AGORA)));

        participante.iniciarExecucao(envelope("IniciarExecucao", null), new IniciarExecucaoPayload(osId));

        verify(pecas).baixar(peca.getId(), 2);
        verify(reservas).atualizarStatus(osId, peca.getId(), StatusReserva.BAIXADA, AGORA);
        OrdemExecucao salva = ordemSalva();
        assertThat(salva.getStatus()).isEqualTo(StatusOrdem.EM_EXECUCAO);
        assertThat(salva.prioridadeFila()).isEqualTo(1);
        MensagemEnvelope resposta = respostaNaOutbox();
        assertThat(resposta.type()).isEqualTo("ExecucaoIniciada");
        assertThat(resposta.payload().path("iniciadaEm").asText()).isEqualTo("2026-11-06T12:00:00Z");
    }

    @Test
    void iniciarForaDeDiagnosticoConcluidoEIgnoradoSemResposta() {
        when(ordens.buscarPorId(osId)).thenReturn(Optional.of(
                OrdemExecucao.abrir(osId, sagaId, UUID.randomUUID(), "ABC1D23", CLOCK)));

        participante.iniciarExecucao(envelope("IniciarExecucao", null), new IniciarExecucaoPayload(osId));

        verify(ordens, never()).salvar(any());
        verifyNoInteractions(outbox, pecas);
    }

    @Test
    void reenvioDoIniciarReemiteSemNovaBaixa() {
        OrdemExecucao ordem = ordemConcluida(peca(10));
        ordem.iniciarExecucao(CLOCK);
        UUID original = UUID.randomUUID();
        ordem.registrarComando("IniciarExecucao", new ComandoAtendido(List.of(original), "ExecucaoIniciada",
                "{\"osId\":\"%s\",\"iniciadaEm\":\"2026-11-06T12:00:00Z\"}".formatted(osId)));
        when(ordens.buscarPorId(osId)).thenReturn(Optional.of(ordem));

        participante.iniciarExecucao(envelope("IniciarExecucao", original), new IniciarExecucaoPayload(osId));

        verifyNoInteractions(pecas, reservas);
        assertThat(respostaNaOutbox().type()).isEqualTo("ExecucaoIniciada");
    }

    // ---- OrdemServicoCancelada --------------------------------------------------------------------

    @Test
    void ordemCanceladaCancelaSemLiberarReservaESemResponder() {
        OrdemExecucao ordem = ordemConcluida(peca(10));
        when(ordens.buscarPorId(osId)).thenReturn(Optional.of(ordem));

        participante.ordemCancelada(envelope("OrdemServicoCancelada", null), cancelada());

        assertThat(ordemSalva().getStatus()).isEqualTo(StatusOrdem.CANCELADA);
        verifyNoInteractions(outbox, pecas, reservas);
    }

    @Test
    void ordemCanceladaDesconhecidaGravaLapide() {
        when(ordens.buscarPorId(osId)).thenReturn(Optional.empty());

        participante.ordemCancelada(envelope("OrdemServicoCancelada", null), cancelada());

        OrdemExecucao lapide = ordemSalva();
        assertThat(lapide.getStatus()).isEqualTo(StatusOrdem.CANCELADA);
        assertThat(lapide.comandoAtendido("AbrirDiagnostico")).isEmpty();
    }

    @Test
    void ordemCanceladaJaCanceladaOuEmExecucaoNaoGrava() {
        when(ordens.buscarPorId(osId)).thenReturn(Optional.of(OrdemExecucao.lapide(osId, CLOCK)));
        participante.ordemCancelada(envelope("OrdemServicoCancelada", null), cancelada());

        OrdemExecucao emExecucao = ordemConcluida(peca(10));
        emExecucao.iniciarExecucao(CLOCK);
        when(ordens.buscarPorId(osId)).thenReturn(Optional.of(emExecucao));
        participante.ordemCancelada(envelope("OrdemServicoCancelada", null), cancelada());

        verify(ordens, never()).salvar(any());
    }

    // ---- apoio -------------------------------------------------------------------------------------

    @Test
    void oMdcFicaRestauradoDepoisDeCadaOperacao() {
        when(ordens.buscarPorId(osId)).thenReturn(Optional.empty());

        participante.ordemCancelada(envelope("OrdemServicoCancelada", null), cancelada());

        assertThat(Mdc.correlationId()).isNull();
    }

    private OrdemServicoCanceladaPayload cancelada() {
        return new OrdemServicoCanceladaPayload(osId, UUID.randomUUID(), "motivo", AGORA);
    }

    private MensagemEnvelope envelope(String tipo, UUID causationId) {
        return new MensagemEnvelope(UUID.randomUUID(), tipo, 1, sagaId, osId, "corr", causationId, AGORA, null, null);
    }

    private Peca peca(int estoque) {
        return new Peca("Peca", "Marca", new BigDecimal("10.00"), estoque);
    }

    private OrdemExecucao ordemConcluida(Peca peca) {
        OrdemExecucao ordem = OrdemExecucao.abrir(osId, sagaId, UUID.randomUUID(), "ABC1D23", CLOCK);
        Prestacao prestacao = ordem.incluirPrestacao(UUID.randomUUID(), "Servico", new BigDecimal("50.00"));
        ordem.alocarPeca(prestacao.getId(), peca, 1);
        ordem.concluirDiagnostico(CLOCK);
        return ordem;
    }

    private OrdemExecucao abertaComResposta() {
        OrdemExecucao ordem = OrdemExecucao.abrir(osId, sagaId, UUID.randomUUID(), "ABC1D23", CLOCK);
        ordem.registrarComando("AbrirDiagnostico", new ComandoAtendido(List.of(UUID.randomUUID()), "DiagnosticoAberto",
                "{\"osId\":\"%s\",\"abertoEm\":\"2026-11-06T12:00:00Z\"}".formatted(osId)));
        return ordem;
    }

    private OrdemExecucao ordemSalva() {
        ArgumentCaptor<OrdemExecucao> captor = ArgumentCaptor.forClass(OrdemExecucao.class);
        verify(ordens).salvar(captor.capture());
        return captor.getValue();
    }

    private MensagemEnvelope respostaNaOutbox() {
        ArgumentCaptor<MensagemEnvelope> captor = ArgumentCaptor.forClass(MensagemEnvelope.class);
        verify(outbox).registrar(eq("execucao-eventos"), captor.capture());
        return captor.getValue();
    }
}
