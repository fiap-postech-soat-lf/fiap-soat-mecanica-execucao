package br.com.fiap.soat.mecanica.execucao.application.saga;

import br.com.fiap.soat.mecanica.execucao.application.mensageria.MensagemEnvelope;
import br.com.fiap.soat.mecanica.execucao.application.mensageria.MensagemFactory;
import br.com.fiap.soat.mecanica.execucao.application.saga.dto.ReservarPecasPayload.PecaQuantidade;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Monta as respostas do Execucao aos comandos da Saga (contratos em {@code docs/contratos}). Cada resposta e um
 * {@link Resposta}: o tipo e o payload, que o {@link ParticipanteSaga} guarda na ordem (F5-h) e registra na outbox.
 * O envelope herda {@code sagaId}, {@code osId} e {@code causationId} do MDC da mensagem em processamento.
 */
@Component
@RequiredArgsConstructor
public class RespostasSaga {

    public static final String DESTINO = "execucao-eventos";
    public static final String DIAGNOSTICO_ABERTO = "DiagnosticoAberto";
    public static final String PECAS_RESERVADAS = "PecasReservadas";
    public static final String RESERVA_PECAS_RECUSADA = "ReservaPecasRecusada";
    public static final String RESERVA_PECAS_LIBERADA = "ReservaPecasLiberada";
    public static final String EXECUCAO_INICIADA = "ExecucaoIniciada";

    private final MensagemFactory mensagens;
    private final ObjectMapper objectMapper;

    /** Tipo e payload de uma resposta, ja prontos para guardar e para virar envelope. */
    public record Resposta(String tipo, JsonNode payload) {
    }

    /** Peca que nao pode ser reservada: o que foi pedido e o que ha disponivel (0 se nao existe ou esta inativa). */
    public record Faltante(UUID pecaId, int solicitada, int disponivel) {
    }

    public Resposta diagnosticoAberto(UUID osId, Instant abertoEm) {
        return resposta(DIAGNOSTICO_ABERTO, Map.of("osId", osId, "abertoEm", abertoEm.toString()));
    }

    public Resposta pecasReservadas(UUID osId, List<PecaQuantidade> pecas) {
        return resposta(PECAS_RESERVADAS, Map.of("osId", osId, "pecas", pecas));
    }

    public Resposta reservaPecasRecusada(UUID osId, List<Faltante> faltantes) {
        return resposta(RESERVA_PECAS_RECUSADA, Map.of("osId", osId, "faltantes", faltantes));
    }

    public Resposta reservaPecasLiberada(UUID osId) {
        return resposta(RESERVA_PECAS_LIBERADA, Map.of("osId", osId));
    }

    public Resposta execucaoIniciada(UUID osId, Instant iniciadaEm) {
        return resposta(EXECUCAO_INICIADA, Map.of("osId", osId, "iniciadaEm", iniciadaEm.toString()));
    }

    /** Envelope novo (messageId novo) para a resposta; usado tanto na primeira emissao quanto na reemissao. */
    public MensagemEnvelope envelope(Resposta resposta, UUID sagaId, UUID osId) {
        return mensagens.criar(resposta.tipo(), 1, sagaId, osId, resposta.payload());
    }

    private Resposta resposta(String tipo, Object payload) {
        return new Resposta(tipo, objectMapper.valueToTree(payload));
    }
}
