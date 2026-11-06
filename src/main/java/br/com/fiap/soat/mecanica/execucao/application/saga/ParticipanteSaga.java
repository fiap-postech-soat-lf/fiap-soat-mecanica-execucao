package br.com.fiap.soat.mecanica.execucao.application.saga;

import br.com.fiap.soat.mecanica.execucao.application.mensageria.MensagemEnvelope;
import br.com.fiap.soat.mecanica.execucao.application.mensageria.OutboxPort;
import br.com.fiap.soat.mecanica.execucao.application.saga.RespostasSaga.Faltante;
import br.com.fiap.soat.mecanica.execucao.application.saga.RespostasSaga.Resposta;
import br.com.fiap.soat.mecanica.execucao.application.saga.dto.AbrirDiagnosticoPayload;
import br.com.fiap.soat.mecanica.execucao.application.saga.dto.IniciarExecucaoPayload;
import br.com.fiap.soat.mecanica.execucao.application.saga.dto.LiberarReservaPecasPayload;
import br.com.fiap.soat.mecanica.execucao.application.saga.dto.OrdemServicoCanceladaPayload;
import br.com.fiap.soat.mecanica.execucao.application.saga.dto.ReservarPecasPayload;
import br.com.fiap.soat.mecanica.execucao.application.saga.dto.ReservarPecasPayload.PecaQuantidade;
import br.com.fiap.soat.mecanica.execucao.config.observability.BusinessEventLogger;
import br.com.fiap.soat.mecanica.execucao.config.observability.Mdc;
import br.com.fiap.soat.mecanica.execucao.domain.exception.RecursoNaoEncontradoException;
import br.com.fiap.soat.mecanica.execucao.domain.exception.RegraNegocioException;
import br.com.fiap.soat.mecanica.execucao.domain.ordem.ComandoAtendido;
import br.com.fiap.soat.mecanica.execucao.domain.ordem.OrdemExecucao;
import br.com.fiap.soat.mecanica.execucao.domain.ordem.OrdemRepository;
import br.com.fiap.soat.mecanica.execucao.domain.ordem.Reserva;
import br.com.fiap.soat.mecanica.execucao.domain.ordem.ReservaRepository;
import br.com.fiap.soat.mecanica.execucao.domain.ordem.StatusOrdem;
import br.com.fiap.soat.mecanica.execucao.domain.ordem.StatusReserva;
import br.com.fiap.soat.mecanica.execucao.domain.peca.Peca;
import br.com.fiap.soat.mecanica.execucao.domain.peca.PecaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Lado do Execucao na Saga do OS: uma operacao por gatilho de mensagem (tabela 7.1 da F5). Roda dentro da transacao
 * do {@code ProcessadorMensagemDynamo}, entao a ordem, o estoque, as reservas, a resposta na outbox e a idempotencia
 * sao confirmados juntos, ou nenhum.
 * <p>
 * Antes de aplicar um comando, consulta a cadeia de ids da ordem (F5-h): um reenvio reemite a resposta guardada, sem
 * efeito de negocio. Cada operacao abre o MDC com os ids do envelope, para o {@code MensagemFactory} herdar
 * {@code causationId}, {@code sagaId} e {@code osId} tambem quando nao ha o {@code SqsConsumidor} na frente.
 */
@Service
@RequiredArgsConstructor
public class ParticipanteSaga {

    public static final String ABRIR_DIAGNOSTICO = "AbrirDiagnostico";
    public static final String RESERVAR_PECAS = "ReservarPecas";
    public static final String LIBERAR_RESERVA_PECAS = "LiberarReservaPecas";
    public static final String INICIAR_EXECUCAO = "IniciarExecucao";

    private final OrdemRepository ordens;
    private final PecaRepository pecas;
    private final ReservaRepository reservas;
    private final OutboxPort outbox;
    private final RespostasSaga respostas;
    private final ComandosAtendidos comandos;
    private final Clock clock;

    /** {@code AbrirDiagnostico}: cria a ordem e responde {@code DiagnosticoAberto}. */
    public void abrirDiagnostico(MensagemEnvelope envelope, AbrirDiagnosticoPayload comando) {
        try (var ignored = contexto(envelope)) {
            Optional<OrdemExecucao> existente = ordens.buscarPorId(comando.osId());
            if (existente.isEmpty()) {
                OrdemExecucao ordem = OrdemExecucao.abrir(comando.osId(), envelope.sagaId(), comando.veiculoId(),
                        comando.placa(), clock);
                concluir(ordem, ABRIR_DIAGNOSTICO, envelope,
                        respostas.diagnosticoAberto(ordem.getOsId(), ordem.getAbertaEm()));
                BusinessEventLogger.ordemStatus(ordem.getOsId(), null, ordem.getStatus(), null);
                return;
            }
            OrdemExecucao ordem = existente.get();
            Optional<ComandoAtendido> guardado = ordem.comandoAtendido(ABRIR_DIAGNOSTICO);
            if (guardado.isEmpty()) {
                // Lapide: o cancelamento chegou antes. A Saga do OS ja esta em FIM, nao ha o que responder.
                BusinessEventLogger.comandoForaDeEstado(ordem.getOsId(), ABRIR_DIAGNOSTICO, ordem.getStatus());
                return;
            }
            // A ordem ja existe: reemite mesmo fora da cadeia (o OS pode ter reenviado depois de perder a resposta)
            reemitir(ordem, ABRIR_DIAGNOSTICO, envelope, guardado.get());
        }
    }

    /**
     * {@code ReservarPecas}: reserva todas as pecas ou nenhuma (F5-e). Sem estoque, a ordem volta a
     * {@code EM_DIAGNOSTICO} e a resposta e {@code ReservaPecasRecusada} com as faltantes.
     */
    public void reservarPecas(MensagemEnvelope envelope, ReservarPecasPayload comando) {
        try (var ignored = contexto(envelope)) {
            OrdemExecucao ordem = buscar(comando.osId());
            if (reenviado(ordem, RESERVAR_PECAS, envelope)) {
                return;
            }
            Map<UUID, Integer> pedidas = somarPorPeca(comando.pecas());
            if (ordem.getStatus() == StatusOrdem.CANCELADA) {
                List<Faltante> todas = pedidas.entrySet().stream()
                        .map(e -> new Faltante(e.getKey(), e.getValue(), 0)).toList();
                concluir(ordem, RESERVAR_PECAS, envelope, respostas.reservaPecasRecusada(ordem.getOsId(), todas));
                BusinessEventLogger.reservaPecas(ordem.getOsId(), "RECUSADA", pedidas.size(), "ordem cancelada");
                return;
            }
            if (ordem.getStatus() != StatusOrdem.DIAGNOSTICO_CONCLUIDO) {
                BusinessEventLogger.comandoForaDeEstado(ordem.getOsId(), RESERVAR_PECAS, ordem.getStatus());
                return;
            }
            List<Faltante> faltantes = faltantes(pedidas);
            if (!faltantes.isEmpty()) {
                recusar(ordem, envelope, faltantes);
                return;
            }
            Instant agora = clock.instant();
            pedidas.forEach((pecaId, quantidade) -> {
                pecas.reservar(pecaId, quantidade);
                reservas.registrar(Reserva.nova(ordem.getOsId(), pecaId, quantidade, agora));
            });
            ordem.reservarPecas();
            List<PecaQuantidade> reservadas = pedidas.entrySet().stream()
                    .map(e -> new PecaQuantidade(e.getKey(), e.getValue())).toList();
            concluir(ordem, RESERVAR_PECAS, envelope, respostas.pecasReservadas(ordem.getOsId(), reservadas));
            BusinessEventLogger.reservaPecas(ordem.getOsId(), "RESERVADA", pedidas.size(), null);
        }
    }

    /**
     * {@code LiberarReservaPecas}: devolve ao estoque as reservas {@code ATIVA} da ordem e reabre o diagnostico ou
     * cancela, conforme o motivo. Sem reserva ativa responde do mesmo jeito (F5-h): o OS manda este comando "por
     * garantia" no timeout da reserva, sem saber se ela existe.
     */
    public void liberarReserva(MensagemEnvelope envelope, LiberarReservaPecasPayload comando) {
        try (var ignored = contexto(envelope)) {
            OrdemExecucao ordem = buscar(comando.osId());
            if (reenviado(ordem, LIBERAR_RESERVA_PECAS, envelope)) {
                return;
            }
            if (ordem.getStatus() == StatusOrdem.EM_EXECUCAO || ordem.getStatus() == StatusOrdem.FINALIZADA) {
                BusinessEventLogger.comandoForaDeEstado(ordem.getOsId(), LIBERAR_RESERVA_PECAS, ordem.getStatus());
                return;
            }
            Instant agora = clock.instant();
            List<Reserva> ativas = reservas.buscarPorOs(ordem.getOsId()).stream().filter(Reserva::ativa).toList();
            ativas.forEach(r -> {
                pecas.liberar(r.pecaId(), r.quantidade());
                reservas.atualizarStatus(ordem.getOsId(), r.pecaId(), StatusReserva.LIBERADA, agora);
            });
            StatusOrdem antes = ordem.getStatus();
            ordem.liberarReserva(comando.motivo());
            concluir(ordem, LIBERAR_RESERVA_PECAS, envelope, respostas.reservaPecasLiberada(ordem.getOsId()));
            BusinessEventLogger.reservaPecas(ordem.getOsId(), "LIBERADA", ativas.size(), null);
            registrarMudanca(ordem, antes, comando.motivo().name());
        }
    }

    /** {@code IniciarExecucao}: baixa as reservas do estoque, inicia as prestacoes e responde {@code ExecucaoIniciada}. */
    public void iniciarExecucao(MensagemEnvelope envelope, IniciarExecucaoPayload comando) {
        try (var ignored = contexto(envelope)) {
            OrdemExecucao ordem = buscar(comando.osId());
            if (reenviado(ordem, INICIAR_EXECUCAO, envelope)) {
                return;
            }
            if (ordem.getStatus() != StatusOrdem.DIAGNOSTICO_CONCLUIDO) {
                // Do lado do OS e depois do pivo: o timeout leva a Saga a FALHOU com alerta (tabela 6.3 da F4)
                BusinessEventLogger.comandoForaDeEstado(ordem.getOsId(), INICIAR_EXECUCAO, ordem.getStatus());
                return;
            }
            Instant agora = clock.instant();
            reservas.buscarPorOs(ordem.getOsId()).stream().filter(Reserva::ativa).forEach(r -> {
                pecas.baixar(r.pecaId(), r.quantidade());
                reservas.atualizarStatus(ordem.getOsId(), r.pecaId(), StatusReserva.BAIXADA, agora);
            });
            StatusOrdem antes = ordem.getStatus();
            ordem.iniciarExecucao(clock);
            concluir(ordem, INICIAR_EXECUCAO, envelope,
                    respostas.execucaoIniciada(ordem.getOsId(), ordem.getIniciadaEm()));
            registrarMudanca(ordem, antes, null);
        }
    }

    /**
     * {@code OrdemServicoCancelada} (F5-b): tira a ordem da fila. Nao libera a reserva, porque o OS manda o
     * {@code LiberarReservaPecas} a parte e liberar aqui devolveria o estoque duas vezes. Ordem desconhecida
     * vira lapide.
     */
    public void ordemCancelada(MensagemEnvelope envelope, OrdemServicoCanceladaPayload evento) {
        try (var ignored = contexto(envelope)) {
            Optional<OrdemExecucao> existente = ordens.buscarPorId(evento.osId());
            if (existente.isEmpty()) {
                OrdemExecucao lapide = OrdemExecucao.lapide(evento.osId(), clock);
                ordens.salvar(lapide);
                BusinessEventLogger.ordemStatus(lapide.getOsId(), null, lapide.getStatus(), lapide.getMotivo());
                return;
            }
            OrdemExecucao ordem = existente.get();
            if (ordem.getStatus() == StatusOrdem.CANCELADA) {
                return;
            }
            if (ordem.getStatus() == StatusOrdem.EM_EXECUCAO || ordem.getStatus() == StatusOrdem.FINALIZADA) {
                BusinessEventLogger.comandoForaDeEstado(ordem.getOsId(), "OrdemServicoCancelada", ordem.getStatus());
                return;
            }
            StatusOrdem antes = ordem.getStatus();
            ordem.cancelar();
            ordens.salvar(ordem);
            registrarMudanca(ordem, antes, ordem.getMotivo());
        }
    }

    // ---- apoio -------------------------------------------------------------------------------------

    private OrdemExecucao buscar(UUID osId) {
        return ordens.buscarPorId(osId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Ordem de execução não encontrada: " + osId));
    }

    /** F5-h: se o comando e um reenvio, reemite a resposta guardada e informa {@code true}. */
    private boolean reenviado(OrdemExecucao ordem, String tipoComando, MensagemEnvelope envelope) {
        Optional<ComandoAtendido> guardado = comandos.reenvio(ordem, tipoComando, envelope);
        guardado.ifPresent(comando -> reemitir(ordem, tipoComando, envelope, comando));
        return guardado.isPresent();
    }

    private void reemitir(OrdemExecucao ordem, String tipoComando, MensagemEnvelope envelope,
                          ComandoAtendido guardado) {
        ComandoAtendido atualizado = comandos.registrarReenvio(ordem, tipoComando, envelope, guardado);
        ordens.salvar(ordem);
        outbox.registrar(RespostasSaga.DESTINO,
                respostas.envelope(comandos.respostaGuardada(atualizado), ordem.getSagaId(), ordem.getOsId()));
        BusinessEventLogger.comandoReemitido(ordem.getOsId(), tipoComando, atualizado.tipoResposta());
    }

    /** Nova rodada do comando: guarda a cadeia e a resposta na ordem, grava a ordem e registra a resposta na outbox. */
    private void concluir(OrdemExecucao ordem, String tipoComando, MensagemEnvelope envelope, Resposta resposta) {
        comandos.registrarNovaRodada(ordem, tipoComando, envelope, resposta);
        ordens.salvar(ordem);
        outbox.registrar(RespostasSaga.DESTINO, respostas.envelope(resposta, ordem.getSagaId(), ordem.getOsId()));
    }

    private void recusar(OrdemExecucao ordem, MensagemEnvelope envelope, List<Faltante> faltantes) {
        StatusOrdem antes = ordem.getStatus();
        String motivo = faltantes.stream()
                .map(f -> "peça %s: solicitada %d, disponível %d".formatted(f.pecaId(), f.solicitada(), f.disponivel()))
                .collect(Collectors.joining("; ", "Sem estoque - ", ""));
        ordem.recusarReserva(motivo);
        concluir(ordem, RESERVAR_PECAS, envelope, respostas.reservaPecasRecusada(ordem.getOsId(), faltantes));
        BusinessEventLogger.reservaPecas(ordem.getOsId(), "RECUSADA", faltantes.size(), motivo);
        registrarMudanca(ordem, antes, motivo);
    }

    /** Peca inexistente, inativa ou com menos disponivel do que o pedido (a leitura e consistente). */
    private List<Faltante> faltantes(Map<UUID, Integer> pedidas) {
        List<Faltante> faltantes = new ArrayList<>();
        pedidas.forEach((pecaId, quantidade) -> {
            Optional<Peca> peca = pecas.buscarPorId(pecaId).filter(Peca::isAtivo);
            int disponivel = peca.map(Peca::getQtdDisponivel).orElse(0);
            if (disponivel < quantidade) {
                faltantes.add(new Faltante(pecaId, quantidade, disponivel));
            }
        });
        return faltantes;
    }

    /** Soma as quantidades por peca: a transacao nao pode tocar duas vezes o mesmo item (F5-e). */
    private static Map<UUID, Integer> somarPorPeca(List<PecaQuantidade> pecas) {
        if (pecas == null || pecas.isEmpty()) {
            throw new RegraNegocioException("ReservarPecas sem peças");
        }
        Map<UUID, Integer> soma = new LinkedHashMap<>();
        pecas.forEach(p -> soma.merge(p.pecaId(), p.quantidade(), Integer::sum));
        return soma;
    }

    private static void registrarMudanca(OrdemExecucao ordem, StatusOrdem antes, String motivo) {
        if (ordem.getStatus() != antes) {
            BusinessEventLogger.ordemStatus(ordem.getOsId(), antes, ordem.getStatus(), motivo);
        }
    }

    private static Mdc.Escopo contexto(MensagemEnvelope envelope) {
        Map<String, Object> valores = new LinkedHashMap<>();
        valores.put(Mdc.CORRELATION_ID, envelope.correlationId());
        valores.put(Mdc.SAGA_ID, envelope.sagaId());
        valores.put(Mdc.OS_ID, envelope.osId());
        valores.put(Mdc.MESSAGE_ID, envelope.messageId());
        return Mdc.abrir(valores);
    }
}
