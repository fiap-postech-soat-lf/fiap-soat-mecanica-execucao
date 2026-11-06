package br.com.fiap.soat.mecanica.execucao.application.saga;

import br.com.fiap.soat.mecanica.execucao.application.mensageria.MensagemEnvelope;
import br.com.fiap.soat.mecanica.execucao.domain.ordem.OrdemExecucao;
import br.com.fiap.soat.mecanica.execucao.domain.ordem.Prestacao;
import br.com.fiap.soat.mecanica.execucao.domain.ordem.StatusOrdem;
import br.com.fiap.soat.mecanica.execucao.domain.peca.Peca;
import br.com.fiap.soat.mecanica.execucao.support.SagaIntegracaoTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** A reserva de varias pecas e tudo ou nada (F5-e): se uma falta, nenhuma e reservada. */
class ReservaAtomicaIT extends SagaIntegracaoTest {

    @Test
    void umaPecaComEstoqueEOutraSemNenhumaEReservada() {
        Peca comEstoque = criarPeca(5);
        Peca semEstoque = criarPeca(0);
        OrdemExecucao ordem = ordemComPecas(Map.of(comEstoque, 2, semEstoque, 1));

        processador.processar(reservarVarias(ordem, Map.of(comEstoque, 2, semEstoque, 1)));

        assertThat(tiposRespondidos(ordem.getOsId())).containsExactly("ReservaPecasRecusada");
        JsonNode faltantes = respostas(ordem.getOsId()).get(0).at("/payload/faltantes");
        assertThat(faltantes).hasSize(1);
        assertThat(faltantes.get(0).path("pecaId").asText()).isEqualTo(semEstoque.getId().toString());
        assertThat(faltantes.get(0).path("solicitada").asInt()).isEqualTo(1);
        assertThat(faltantes.get(0).path("disponivel").asInt()).isZero();
        assertThat(peca(comEstoque.getId()).getQtdDisponivel()).isEqualTo(5);
        assertThat(peca(comEstoque.getId()).getQtdReservada()).isZero();
        assertThat(peca(semEstoque.getId()).getQtdReservada()).isZero();
        assertThat(reservas.buscarPorOs(ordem.getOsId())).isEmpty();
        OrdemExecucao depois = recarregar(ordem.getOsId());
        assertThat(depois.getStatus()).isEqualTo(StatusOrdem.EM_DIAGNOSTICO);
        assertThat(depois.getMotivo()).contains(semEstoque.getId().toString());
    }

    @Test
    void todasComEstoqueSaoReservadasJuntas() {
        Peca a = criarPeca(5);
        Peca b = criarPeca(3);
        OrdemExecucao ordem = ordemComPecas(Map.of(a, 2, b, 3));

        processador.processar(reservarVarias(ordem, Map.of(a, 2, b, 3)));

        assertThat(tiposRespondidos(ordem.getOsId())).containsExactly("PecasReservadas");
        assertThat(peca(a.getId()).getQtdDisponivel()).isEqualTo(3);
        assertThat(peca(a.getId()).getQtdReservada()).isEqualTo(2);
        assertThat(peca(b.getId()).getQtdDisponivel()).isZero();
        assertThat(peca(b.getId()).getQtdReservada()).isEqualTo(3);
        assertThat(reservas.buscarPorOs(ordem.getOsId())).hasSize(2).allMatch(r -> r.ativa());
        assertThat(recarregar(ordem.getOsId()).getStatus()).isEqualTo(StatusOrdem.DIAGNOSTICO_CONCLUIDO);
    }

    @Test
    void aMesmaPecaEmDuasLinhasDoComandoEhSomadaAntesDaTransacao() {
        Peca peca = criarPeca(4);
        OrdemExecucao ordem = ordemComPecas(Map.of(peca, 1));
        MensagemEnvelope duasLinhas = comando("ReservarPecas", ordem.getOsId(), ordem.getSagaId(),
                Map.of("osId", ordem.getOsId(), "pecas", List.of(
                        Map.of("pecaId", peca.getId(), "quantidade", 1),
                        Map.of("pecaId", peca.getId(), "quantidade", 3))));

        processador.processar(duasLinhas);

        assertThat(tiposRespondidos(ordem.getOsId())).containsExactly("PecasReservadas");
        assertThat(peca(peca.getId()).getQtdReservada()).isEqualTo(4);
        assertThat(reservas.buscarPorOs(ordem.getOsId())).singleElement()
                .satisfies(r -> assertThat(r.quantidade()).isEqualTo(4));
    }

    @Test
    void asLinhasSomadasAcimaDoEstoqueRecusamComAQuantidadeTotal() {
        Peca peca = criarPeca(3);
        OrdemExecucao ordem = ordemComPecas(Map.of(peca, 1));
        MensagemEnvelope duasLinhas = comando("ReservarPecas", ordem.getOsId(), ordem.getSagaId(),
                Map.of("osId", ordem.getOsId(), "pecas", List.of(
                        Map.of("pecaId", peca.getId(), "quantidade", 2),
                        Map.of("pecaId", peca.getId(), "quantidade", 2))));

        processador.processar(duasLinhas);

        JsonNode faltante = respostas(ordem.getOsId()).get(0).at("/payload/faltantes/0");
        assertThat(faltante.path("solicitada").asInt()).isEqualTo(4);
        assertThat(faltante.path("disponivel").asInt()).isEqualTo(3);
        assertThat(peca(peca.getId()).getQtdReservada()).isZero();
    }

    @Test
    void pecaInativaOuInexistenteEhFaltanteComDisponivelZero() {
        Peca inativa = criarPeca(9);
        transacao.executar(() -> {
            Peca lida = peca(inativa.getId());
            lida.inativar();
            pecas.salvar(lida);
        });
        UUID inexistente = UUID.randomUUID();
        OrdemExecucao ordem = ordemComPecas(Map.of(inativa, 1));
        MensagemEnvelope comando = comando("ReservarPecas", ordem.getOsId(), ordem.getSagaId(),
                Map.of("osId", ordem.getOsId(), "pecas", List.of(
                        Map.of("pecaId", inativa.getId(), "quantidade", 1),
                        Map.of("pecaId", inexistente, "quantidade", 2))));

        processador.processar(comando);

        JsonNode faltantes = respostas(ordem.getOsId()).get(0).at("/payload/faltantes");
        assertThat(faltantes).hasSize(2);
        faltantes.forEach(f -> assertThat(f.path("disponivel").asInt()).isZero());
        assertThat(peca(inativa.getId()).getQtdReservada()).isZero();
    }

    @Test
    void reservarNumaOrdemCanceladaRecusaComTodasAsPecasFaltantes() {
        Peca peca = criarPeca(5);
        OrdemExecucao ordem = ordemComPecas(Map.of(peca, 2));
        OrdemExecucao cancelada = recarregar(ordem.getOsId());
        cancelada.cancelar();
        transacao.executar(() -> ordens.salvar(cancelada));

        processador.processar(reservar(ordem, peca, 2));

        JsonNode faltante = respostas(ordem.getOsId()).get(0).at("/payload/faltantes/0");
        assertThat(respostas(ordem.getOsId()).get(0).path("type").asText()).isEqualTo("ReservaPecasRecusada");
        assertThat(faltante.path("solicitada").asInt()).isEqualTo(2);
        assertThat(faltante.path("disponivel").asInt()).isZero();
        assertThat(peca(peca.getId()).getQtdReservada()).isZero();
        assertThat(recarregar(ordem.getOsId()).getStatus()).isEqualTo(StatusOrdem.CANCELADA);
    }

    @Test
    void reservarForaDeDiagnosticoConcluidoNaoRespondeENaoReserva() {
        Peca peca = criarPeca(5);
        OrdemExecucao ordem = ordemEmDiagnostico(peca, 1);

        processador.processar(reservar(ordem, peca, 1));

        assertThat(respostas(ordem.getOsId())).isEmpty();
        assertThat(peca(peca.getId()).getQtdReservada()).isZero();
    }

    private OrdemExecucao ordemComPecas(Map<Peca, Integer> alocacoes) {
        OrdemExecucao ordem = OrdemExecucao.abrir(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "ABC1D23", clock);
        Prestacao prestacao = ordem.incluirPrestacao(UUID.randomUUID(), "Servico", new BigDecimal("50.00"));
        alocacoes.forEach((peca, quantidade) -> ordem.alocarPeca(prestacao.getId(), peca, quantidade));
        ordem.concluirDiagnostico(clock);
        transacao.executar(() -> ordens.salvar(ordem));
        return recarregar(ordem.getOsId());
    }

    private MensagemEnvelope reservarVarias(OrdemExecucao ordem, Map<Peca, Integer> pedidas) {
        List<Map<String, Object>> pecasPedidas = pedidas.entrySet().stream()
                .<Map<String, Object>>map(e -> Map.of("pecaId", e.getKey().getId(), "quantidade", e.getValue()))
                .toList();
        return comando("ReservarPecas", ordem.getOsId(), ordem.getSagaId(),
                Map.of("osId", ordem.getOsId(), "pecas", pecasPedidas));
    }
}
