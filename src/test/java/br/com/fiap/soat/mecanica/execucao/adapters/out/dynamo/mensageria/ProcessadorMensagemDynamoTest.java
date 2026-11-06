package br.com.fiap.soat.mecanica.execucao.adapters.out.dynamo.mensageria;

import br.com.fiap.soat.mecanica.execucao.application.mensageria.MensagemEnvelope;
import br.com.fiap.soat.mecanica.execucao.application.mensageria.MensagemHandler;
import br.com.fiap.soat.mecanica.execucao.application.transacao.ConflitoConcorrenciaException;
import br.com.fiap.soat.mecanica.execucao.application.transacao.MensagemDuplicadaException;
import br.com.fiap.soat.mecanica.execucao.application.transacao.TransacaoPort;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/** Logica de repeticao e de despacho, sem DynamoDB (a transacao real fica no ProcessadorMensagemDynamoIT). */
class ProcessadorMensagemDynamoTest {

    private final TransacaoPort transacao = new TransacaoPort() {
        @Override
        public <T> T executar(Supplier<T> acao) {
            return acao.get();
        }

        @Override
        public boolean ativa() {
            return true;
        }
    };
    private final IdempotenciaDynamo idempotencia = mock(IdempotenciaDynamo.class);
    private final AtomicInteger chamadas = new AtomicInteger();

    @Test
    void tipoSemHandlerEConcluidoSemRegistrarIdempotencia() {
        processador(envelope -> { }).processar(envelope("Outro"));

        verify(idempotencia, never()).registrar(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void despachaParaOHandlerERegistraAIdempotencia() {
        MensagemEnvelope envelope = envelope("Teste");

        processador(e -> chamadas.incrementAndGet()).processar(envelope);

        assertThat(chamadas).hasValue(1);
        verify(idempotencia).registrar(envelope);
    }

    @Test
    void mensagemDuplicadaEConcluidaSemRepetir() {
        processador(e -> {
            chamadas.incrementAndGet();
            throw new MensagemDuplicadaException("duplicada", null);
        }).processar(envelope("Teste"));

        assertThat(chamadas).hasValue(1);
    }

    @Test
    void conflitoRepeteOHandlerEDepoisConclui() {
        processador(e -> {
            if (chamadas.incrementAndGet() < 3) {
                throw new ConflitoConcorrenciaException("conflito", null);
            }
        }).processar(envelope("Teste"));

        assertThat(chamadas).hasValue(3);
        verify(idempotencia, times(1)).registrar(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void conflitoQueNaoPassaAposTresTentativasDeixaAMensagemVoltarParaAFila() {
        ProcessadorMensagemDynamo processador = processador(e -> {
            chamadas.incrementAndGet();
            throw new ConflitoConcorrenciaException("conflito", null);
        });

        assertThatThrownBy(() -> processador.processar(envelope("Teste")))
                .isInstanceOf(ConflitoConcorrenciaException.class);
        assertThat(chamadas).hasValue(ProcessadorMensagemDynamo.MAXIMO_TENTATIVAS);
    }

    @Test
    void outraExcecaoDoHandlerNaoERepetida() {
        ProcessadorMensagemDynamo processador = processador(e -> {
            chamadas.incrementAndGet();
            throw new IllegalStateException("bug");
        });

        assertThatThrownBy(() -> processador.processar(envelope("Teste"))).isInstanceOf(IllegalStateException.class);
        assertThat(chamadas).hasValue(1);
    }

    @Test
    void doisHandlersParaOMesmoTipoFalhamNaCriacao() {
        MensagemHandler a = handler(e -> { });
        MensagemHandler b = handler(e -> { });

        assertThatThrownBy(() -> new ProcessadorMensagemDynamo(transacao, idempotencia, List.of(a, b)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Teste");
    }

    private ProcessadorMensagemDynamo processador(java.util.function.Consumer<MensagemEnvelope> acao) {
        return new ProcessadorMensagemDynamo(transacao, idempotencia, List.of(handler(acao)));
    }

    private static MensagemHandler handler(java.util.function.Consumer<MensagemEnvelope> acao) {
        return new MensagemHandler() {
            @Override
            public String tipo() {
                return "Teste";
            }

            @Override
            public void processar(MensagemEnvelope envelope) {
                acao.accept(envelope);
            }
        };
    }

    private static MensagemEnvelope envelope(String tipo) {
        return new MensagemEnvelope(UUID.randomUUID(), tipo, 1, UUID.randomUUID(), UUID.randomUUID(),
                "corr", null, Instant.now(), null, null);
    }
}
