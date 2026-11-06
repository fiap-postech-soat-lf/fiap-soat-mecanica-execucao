package br.com.fiap.soat.mecanica.execucao.application.saga;

import br.com.fiap.soat.mecanica.execucao.application.mensageria.MensagemEnvelope;
import br.com.fiap.soat.mecanica.execucao.domain.ordem.OrdemExecucao;
import br.com.fiap.soat.mecanica.execucao.domain.ordem.StatusOrdem;
import br.com.fiap.soat.mecanica.execucao.domain.peca.Peca;
import br.com.fiap.soat.mecanica.execucao.support.SagaIntegracaoTest;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DoD da F5 no plano mestre: com uma unidade em estoque, duas ordens pedem essa unidade ao mesmo tempo. Exatamente
 * uma reserva e confirmada e a outra e recusada, e o estoque fecha (F5-e, F5-g). Roda em varias rodadas, cada uma com
 * ids novos, porque a corrida so aparece em parte das execucoes.
 */
class ReservaConcorrenteIT extends SagaIntegracaoTest {

    private static final String RESERVAR = "ReservarPecas";

    @RepeatedTest(10)
    void duasOrdensDisputamAUltimaUnidadeEUmaVenceExatamente() throws Exception {
        Peca peca = criarPeca(1);
        OrdemExecucao a = ordemComDiagnostico(peca, 1);
        OrdemExecucao b = ordemComDiagnostico(peca, 1);

        processarAoMesmoTempo(reservar(a, peca, 1), reservar(b, peca, 1));

        List<String> respostas = List.of(resposta(a), resposta(b));
        assertThat(respostas).containsExactlyInAnyOrder("PecasReservadas", "ReservaPecasRecusada");
        assertThat(publicadas("PecasReservadas", a, b)).isEqualTo(1);
        assertThat(publicadas("ReservaPecasRecusada", a, b)).isEqualTo(1);

        Peca depois = peca(peca.getId());
        assertThat(depois.getQtdDisponivel()).isZero();
        assertThat(depois.getQtdReservada()).isEqualTo(1);
        assertThat(depois.getQtdEstoque()).isEqualTo(1);

        OrdemExecucao vencedora = resposta(a).equals("PecasReservadas") ? a : b;
        OrdemExecucao perdedora = vencedora == a ? b : a;
        assertThat(reservas.buscarPorOs(vencedora.getOsId())).singleElement()
                .satisfies(r -> assertThat(r.ativa()).isTrue());
        assertThat(reservas.buscarPorOs(perdedora.getOsId())).isEmpty();
        assertThat(status(vencedora)).isEqualTo(StatusOrdem.DIAGNOSTICO_CONCLUIDO);
        assertThat(status(perdedora)).isEqualTo(StatusOrdem.EM_DIAGNOSTICO);
    }

    @Test
    void comEstoqueParaAsDuasAsDuasReservasPassam() throws Exception {
        Peca peca = criarPeca(2);
        OrdemExecucao a = ordemComDiagnostico(peca, 1);
        OrdemExecucao b = ordemComDiagnostico(peca, 1);

        processarAoMesmoTempo(reservar(a, peca, 1), reservar(b, peca, 1));

        assertThat(List.of(resposta(a), resposta(b))).containsOnly("PecasReservadas");
        Peca depois = peca(peca.getId());
        assertThat(depois.getQtdDisponivel()).isZero();
        assertThat(depois.getQtdReservada()).isEqualTo(2);
        assertThat(depois.getQtdEstoque()).isEqualTo(2);
    }

    // ---- preparacao --------------------------------------------------------------------------------

    /** As duas threads largam juntas, no mesmo {@link CountDownLatch}, e chamam o processador como o consumidor SQS. */
    private void processarAoMesmoTempo(MensagemEnvelope primeiro, MensagemEnvelope segundo)
            throws InterruptedException, ExecutionException {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch pronto = new CountDownLatch(2);
            CountDownLatch largada = new CountDownLatch(1);
            List<Future<?>> tarefas = new ArrayList<>();
            for (MensagemEnvelope envelope : List.of(primeiro, segundo)) {
                tarefas.add(executor.submit(() -> {
                    pronto.countDown();
                    largada.await();
                    processador.processar(envelope);
                    return null;
                }));
            }
            assertThat(pronto.await(10, TimeUnit.SECONDS)).isTrue();
            largada.countDown();
            for (Future<?> tarefa : tarefas) {
                tarefa.get(30, TimeUnit.SECONDS);
            }
        } catch (java.util.concurrent.TimeoutException e) {
            throw new AssertionError("O processamento nao terminou em 30 s", e);
        } finally {
            executor.shutdownNow();
        }
    }

    // ---- verificacao -------------------------------------------------------------------------------

    private StatusOrdem status(OrdemExecucao ordem) {
        return recarregar(ordem.getOsId()).getStatus();
    }

    /** Tipo da resposta guardada na ordem (F5-h): e a que foi para a outbox na mesma transacao. */
    private String resposta(OrdemExecucao ordem) {
        return recarregar(ordem.getOsId()).comandoAtendido(RESERVAR).orElseThrow().tipoResposta();
    }

    /** Respostas do tipo na outbox, somadas entre as ordens em disputa (cada resposta e registrada uma vez). */
    private long publicadas(String tipo, OrdemExecucao... emDisputa) {
        return Stream.of(emDisputa).flatMap(o -> tiposRespondidos(o.getOsId()).stream())
                .filter(tipo::equals).count();
    }
}
