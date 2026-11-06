package br.com.fiap.soat.mecanica.execucao.adapters.out.dynamo.ordem;

import br.com.fiap.soat.mecanica.execucao.application.transacao.ConflitoConcorrenciaException;
import br.com.fiap.soat.mecanica.execucao.application.transacao.TransacaoPort;
import br.com.fiap.soat.mecanica.execucao.domain.ordem.ComandoAtendido;
import br.com.fiap.soat.mecanica.execucao.domain.ordem.OrdemExecucao;
import br.com.fiap.soat.mecanica.execucao.domain.ordem.OrdemRepository;
import br.com.fiap.soat.mecanica.execucao.domain.ordem.Prestacao;
import br.com.fiap.soat.mecanica.execucao.domain.ordem.StatusOrdem;
import br.com.fiap.soat.mecanica.execucao.domain.peca.Peca;
import br.com.fiap.soat.mecanica.execucao.support.IntegracaoTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrdemDynamoRepositoryIT extends IntegracaoTest {

    private static final Clock RELOGIO = Clock.fixed(Instant.parse("2026-11-06T12:00:05.120Z"), ZoneOffset.UTC);

    @Autowired
    private OrdemRepository repository;
    @Autowired
    private TransacaoPort transacao;
    @Autowired
    private br.com.fiap.soat.mecanica.execucao.adapters.out.dynamo.TabelasDynamo tabelas;

    private OrdemExecucao nova() {
        return OrdemExecucao.abrir(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "ABC1D23", RELOGIO);
    }

    private void salvar(OrdemExecucao ordem) {
        transacao.executar(() -> repository.salvar(ordem));
    }

    @Test
    void ordemDesconhecidaNaoExiste() {
        assertThat(repository.buscarPorId(UUID.randomUUID())).isEmpty();
    }

    @Test
    void gravaELeAOrdemInteiraComPrestacoesAlocacoesEComandos() {
        OrdemExecucao ordem = nova();
        Prestacao prestacao = ordem.incluirPrestacao(UUID.randomUUID(), "Troca de oleo", new BigDecimal("80.00"));
        Peca peca = new Peca("Filtro", "Bosch", new BigDecimal("39.90"), 5);
        var alocacao = ordem.alocarPeca(prestacao.getId(), peca, 2);
        var removida = ordem.alocarPeca(prestacao.getId(), new Peca("Oleo", "Shell", BigDecimal.ONE, 5), 1);
        ordem.inativarAlocacao(removida.getId());
        ordem.concluirDiagnostico(RELOGIO);
        ordem.registrarComando("ReservarPecas", new ComandoAtendido(
                List.of(UUID.randomUUID(), UUID.randomUUID()), "PecasReservadas", "{\"osId\":\"x\"}"));

        salvar(ordem);
        OrdemExecucao lida = repository.buscarPorId(ordem.getOsId()).orElseThrow();

        assertThat(lida.getVersao()).isEqualTo(1);
        assertThat(lida.getStatus()).isEqualTo(StatusOrdem.DIAGNOSTICO_CONCLUIDO);
        assertThat(lida.getSagaId()).isEqualTo(ordem.getSagaId());
        assertThat(lida.getPlaca()).isEqualTo("ABC1D23");
        assertThat(lida.getAbertaEm()).isEqualTo(Instant.parse("2026-11-06T12:00:05.120Z"));
        assertThat(lida.getDiagnosticoConcluidoEm()).isNotNull();
        assertThat(lida.getIniciadaEm()).isNull();
        Prestacao p = lida.getPrestacoes().get(0);
        assertThat(p.getNomeServico()).isEqualTo("Troca de oleo");
        assertThat(p.getPrecoMaoDeObra()).isEqualByComparingTo("80.00");
        assertThat(p.getAlocacoes()).hasSize(2);
        assertThat(p.getAlocacoes().get(0).getId()).isEqualTo(alocacao.getId());
        assertThat(p.getAlocacoes().get(0).getValorUnitario()).isEqualByComparingTo("39.90");
        assertThat(p.getAlocacoes().get(1).isInativo()).isTrue();
        assertThat(lida.getComandosAtendidos().get("ReservarPecas").ids()).hasSize(2);
        assertThat(lida.getComandosAtendidos().get("ReservarPecas").payloadResposta()).isEqualTo("{\"osId\":\"x\"}");
    }

    @Test
    void ordemNaFilaGravaOsAtributosDoIndiceEquemCancelaOsRemove() {
        OrdemExecucao ordem = nova();
        salvar(ordem);

        var item = item(tabelas.ordens(), "osId", ordem.getOsId().toString());
        assertThat(item.get("fila").s()).isEqualTo("ATIVA");
        assertThat(item.get("filaOrdem").s()).isEqualTo("3#2026-11-06T12:00:05.120Z");

        OrdemExecucao lida = repository.buscarPorId(ordem.getOsId()).orElseThrow();
        lida.cancelar();
        salvar(lida);

        assertThat(item(tabelas.ordens(), "osId", ordem.getOsId().toString())).doesNotContainKeys("fila", "filaOrdem");
    }

    @Test
    void cadaGravacaoIncrementaAVersao() {
        OrdemExecucao ordem = nova();
        salvar(ordem);
        OrdemExecucao v1 = repository.buscarPorId(ordem.getOsId()).orElseThrow();
        salvar(v1);

        assertThat(repository.buscarPorId(ordem.getOsId()).orElseThrow().getVersao()).isEqualTo(2);
    }

    @Test
    void gravarComVersaoVelhaFalhaComConflito() {
        OrdemExecucao ordem = nova();
        salvar(ordem);
        OrdemExecucao a = repository.buscarPorId(ordem.getOsId()).orElseThrow();
        OrdemExecucao b = repository.buscarPorId(ordem.getOsId()).orElseThrow();
        salvar(a);

        assertThatThrownBy(() -> salvar(b)).isInstanceOf(ConflitoConcorrenciaException.class);
    }

    @Test
    void criarDuasVezesAMesmaOrdemFalhaComConflito() {
        OrdemExecucao ordem = nova();
        salvar(ordem);

        assertThatThrownBy(() -> salvar(ordem)).isInstanceOf(ConflitoConcorrenciaException.class);
    }

    @Test
    void lapideGravaSemSagaESemFila() {
        OrdemExecucao lapide = OrdemExecucao.lapide(UUID.randomUUID(), RELOGIO);
        salvar(lapide);

        OrdemExecucao lida = repository.buscarPorId(lapide.getOsId()).orElseThrow();
        assertThat(lida.getStatus()).isEqualTo(StatusOrdem.CANCELADA);
        assertThat(lida.getSagaId()).isNull();
        assertThat(item(tabelas.ordens(), "osId", lapide.getOsId().toString())).doesNotContainKey("fila");
    }
}
