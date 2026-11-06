package br.com.fiap.soat.mecanica.execucao.application.saga;

import br.com.fiap.soat.mecanica.execucao.application.mensageria.MensagemEnvelope;
import br.com.fiap.soat.mecanica.execucao.application.saga.RespostasSaga.Resposta;
import br.com.fiap.soat.mecanica.execucao.domain.ordem.ComandoAtendido;
import br.com.fiap.soat.mecanica.execucao.domain.ordem.OrdemExecucao;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Idempotencia por chave de negocio (F5-h). O OS reenvia o comando com {@code messageId} novo e atualiza o
 * {@code ultimoComando} a cada reenvio, entao o {@code causationId} de uma resposta pode apontar para o original ou
 * para qualquer reenvio: a ordem guarda a cadeia de ids e a resposta, por tipo de comando.
 */
@Component
@RequiredArgsConstructor
public class ComandosAtendidos {

    private final ObjectMapper objectMapper;

    /** O comando atendido do tipo, se o {@code messageId} ou o {@code causationId} do envelope estiver na cadeia dele. */
    public Optional<ComandoAtendido> reenvio(OrdemExecucao ordem, String tipoComando, MensagemEnvelope envelope) {
        return ordem.comandoAtendido(tipoComando)
                .filter(c -> c.contem(envelope.messageId(), envelope.causationId()));
    }

    /** Acrescenta o id do reenvio a cadeia e devolve o comando atualizado. */
    public ComandoAtendido registrarReenvio(OrdemExecucao ordem, String tipoComando, MensagemEnvelope envelope,
                                            ComandoAtendido comando) {
        ComandoAtendido atualizado = comando.comReenvio(envelope.messageId());
        ordem.registrarComando(tipoComando, atualizado);
        return atualizado;
    }

    /** Nova rodada: substitui a entrada do tipo por uma cadeia que comeca neste comando. */
    public void registrarNovaRodada(OrdemExecucao ordem, String tipoComando, MensagemEnvelope envelope,
                                    Resposta resposta) {
        ordem.registrarComando(tipoComando,
                new ComandoAtendido(List.of(envelope.messageId()), resposta.tipo(), resposta.payload().toString()));
    }

    /** A resposta guardada, pronta para virar um envelope novo. */
    public Resposta respostaGuardada(ComandoAtendido comando) {
        try {
            return new Resposta(comando.tipoResposta(), objectMapper.readTree(comando.payloadResposta()));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Resposta guardada invalida para " + comando.tipoResposta(), e);
        }
    }
}
