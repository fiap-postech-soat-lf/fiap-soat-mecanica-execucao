package br.com.fiap.soat.mecanica.execucao.application.saga;

import br.com.fiap.soat.mecanica.execucao.application.mensageria.MensagemEnvelope;
import br.com.fiap.soat.mecanica.execucao.domain.exception.RecursoNaoEncontradoException;
import br.com.fiap.soat.mecanica.execucao.domain.ordem.OrdemExecucao;
import br.com.fiap.soat.mecanica.execucao.domain.ordem.StatusOrdem;
import br.com.fiap.soat.mecanica.execucao.domain.peca.Peca;
import br.com.fiap.soat.mecanica.execucao.support.SagaIntegracaoTest;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Cancelamento (F5-b) e compensacao: o estoque volta uma vez so, em qualquer ordem de chegada das mensagens. */
class CancelamentoIT extends SagaIntegracaoTest {

    @Test
    void ordemServicoCanceladaCancelaESaiDaFila() {
        OrdemExecucao ordem = ordemEmDiagnostico(criarPeca(5), 1);
        assertThat(item(tabelas.ordens(), "osId", ordem.getOsId().toString())).containsKey("fila");

        processador.processar(cancelada(ordem.getOsId()));

        OrdemExecucao depois = recarregar(ordem.getOsId());
        assertThat(depois.getStatus()).isEqualTo(StatusOrdem.CANCELADA);
        assertThat(item(tabelas.ordens(), "osId", ordem.getOsId().toString())).doesNotContainKey("fila");
        assertThat(respostas(ordem.getOsId())).isEmpty();
    }

    @Test
    void liberarComDesistenciaSemReservaRespondeLiberadaMesmoAssim() {
        OrdemExecucao ordem = ordemEmDiagnostico(criarPeca(5), 1);

        processador.processar(liberar(ordem, "DESISTENCIA"));

        assertThat(tiposRespondidos(ordem.getOsId())).containsExactly("ReservaPecasLiberada");
        assertThat(recarregar(ordem.getOsId()).getStatus()).isEqualTo(StatusOrdem.CANCELADA);
    }

    @Test
    void liberarSemReservaNoTimeoutRespondeLiberadaEReabreODiagnostico() {
        OrdemExecucao ordem = ordemComDiagnostico(criarPeca(5), 1);

        processador.processar(liberar(ordem, "ORCAMENTO_FALHOU"));

        assertThat(tiposRespondidos(ordem.getOsId())).containsExactly("ReservaPecasLiberada");
        assertThat(recarregar(ordem.getOsId()).getStatus()).isEqualTo(StatusOrdem.EM_DIAGNOSTICO);
    }

    @Test
    void cancelamentoAntesDaLiberacaoDevolveOEstoqueUmaVezSo() {
        Peca peca = criarPeca(5);
        OrdemExecucao ordem = ordemComReserva(peca, 2);
        assertThat(peca(peca.getId()).getQtdDisponivel()).isEqualTo(3);

        processador.processar(cancelada(ordem.getOsId()));
        assertThat(peca(peca.getId()).getQtdDisponivel()).as("o cancelamento nao libera a reserva").isEqualTo(3);
        processador.processar(liberar(ordem, "DESISTENCIA"));

        assertDevolvido(peca, ordem);
    }

    @Test
    void liberacaoAntesDoCancelamentoDevolveOEstoqueUmaVezSo() {
        Peca peca = criarPeca(5);
        OrdemExecucao ordem = ordemComReserva(peca, 2);

        processador.processar(liberar(ordem, "DESISTENCIA"));
        processador.processar(cancelada(ordem.getOsId()));

        assertDevolvido(peca, ordem);
    }

    @Test
    void liberarComUmMotivoQueReabreDevolveEVoltaParaEmDiagnostico() {
        Peca peca = criarPeca(5);
        OrdemExecucao ordem = ordemComReserva(peca, 2);

        processador.processar(liberar(ordem, "REVISAO"));

        assertThat(peca(peca.getId()).getQtdDisponivel()).isEqualTo(5);
        assertThat(recarregar(ordem.getOsId()).getStatus()).isEqualTo(StatusOrdem.EM_DIAGNOSTICO);
        assertThat(tiposRespondidos(ordem.getOsId())).containsExactly("PecasReservadas", "ReservaPecasLiberada");
    }

    @Test
    void cancelamentoDeOrdemDesconhecidaViraLapideEAberturaAtrasadaNaoRespondeNemReabre() {
        UUID osId = UUID.randomUUID();
        processador.processar(cancelada(osId));
        assertThat(recarregar(osId).getStatus()).isEqualTo(StatusOrdem.CANCELADA);

        processador.processar(comando("AbrirDiagnostico", osId, UUID.randomUUID(),
                Map.of("osId", osId, "veiculoId", UUID.randomUUID(), "placa", "ABC1D23")));

        assertThat(recarregar(osId).getStatus()).isEqualTo(StatusOrdem.CANCELADA);
        assertThat(respostas(osId)).isEmpty();
    }

    @Test
    void iniciarExecucaoForaDeDiagnosticoConcluidoNaoRespondeNemMuda() {
        OrdemExecucao ordem = ordemEmDiagnostico(criarPeca(5), 1);

        processador.processar(iniciar(ordem));

        assertThat(respostas(ordem.getOsId())).isEmpty();
        assertThat(recarregar(ordem.getOsId()).getStatus()).isEqualTo(StatusOrdem.EM_DIAGNOSTICO);
    }

    @Test
    void liberarNumaOrdemSemRegistroFalhaParaIrParaADlq() {
        OrdemExecucao fantasma = OrdemExecucao.abrir(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "ABC1D23", clock);

        assertThatThrownBy(() -> processador.processar(liberar(fantasma, "REVISAO")))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    private OrdemExecucao ordemComReserva(Peca peca, int quantidade) {
        OrdemExecucao ordem = ordemComDiagnostico(peca, quantidade);
        processador.processar(reservar(ordem, peca, quantidade));
        assertThat(tiposRespondidos(ordem.getOsId())).containsExactly("PecasReservadas");
        return recarregar(ordem.getOsId());
    }

    private void assertDevolvido(Peca peca, OrdemExecucao ordem) {
        Peca depois = peca(peca.getId());
        assertThat(depois.getQtdDisponivel()).isEqualTo(5);
        assertThat(depois.getQtdReservada()).isZero();
        assertThat(depois.getQtdEstoque()).isEqualTo(5);
        assertThat(recarregar(ordem.getOsId()).getStatus()).isEqualTo(StatusOrdem.CANCELADA);
        assertThat(reservas.buscarPorOs(ordem.getOsId())).singleElement()
                .satisfies(r -> assertThat(r.status().name()).isEqualTo("LIBERADA"));
        assertThat(tiposRespondidos(ordem.getOsId())).containsExactly("PecasReservadas", "ReservaPecasLiberada");
    }

    private MensagemEnvelope cancelada(UUID osId) {
        return comando("OrdemServicoCancelada", osId, null, Map.of("osId", osId, "clienteId", UUID.randomUUID(),
                "motivo", "Cliente desistiu", "ocorridaEm", "2026-11-06T15:00:00Z"));
    }
}
