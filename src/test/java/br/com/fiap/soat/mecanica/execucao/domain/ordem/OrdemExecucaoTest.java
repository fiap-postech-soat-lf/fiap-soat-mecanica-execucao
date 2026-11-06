package br.com.fiap.soat.mecanica.execucao.domain.ordem;

import br.com.fiap.soat.mecanica.execucao.domain.exception.RecursoNaoEncontradoException;
import br.com.fiap.soat.mecanica.execucao.domain.exception.RegraNegocioException;
import br.com.fiap.soat.mecanica.execucao.domain.peca.Peca;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrdemExecucaoTest {

    private static final Instant T0 = Instant.parse("2026-11-06T12:00:00Z");
    private static final UUID OS = UUID.randomUUID();
    private static final UUID SERVICO = UUID.randomUUID();

    private static Clock relogio(Instant instante) {
        return Clock.fixed(instante, ZoneOffset.UTC);
    }

    private static OrdemExecucao ordem() {
        return OrdemExecucao.abrir(OS, UUID.randomUUID(), UUID.randomUUID(), "ABC1D23", relogio(T0));
    }

    private static Peca peca(String nome, int estoque) {
        return new Peca(nome, "Bosch", new BigDecimal("39.90"), estoque);
    }

    /** Ordem em DIAGNOSTICO_CONCLUIDO com 1 prestacao e 1 peca (qtd 2). */
    private static OrdemExecucao concluida() {
        OrdemExecucao o = ordem();
        Prestacao p = o.incluirPrestacao(SERVICO, "Troca de oleo", new BigDecimal("80.00"));
        o.alocarPeca(p.getId(), peca("Filtro", 10), 2);
        o.concluirDiagnostico(relogio(T0.plusSeconds(60)));
        return o;
    }

    private static OrdemExecucao emExecucao() {
        OrdemExecucao o = concluida();
        o.iniciarExecucao(relogio(T0.plusSeconds(120)));
        return o;
    }

    // ---- abertura -------------------------------------------------------------------------------

    @Test
    void abrirCriaAOrdemEmDiagnosticoNaFilaComPrioridade3() {
        OrdemExecucao o = ordem();

        assertThat(o.getStatus()).isEqualTo(StatusOrdem.EM_DIAGNOSTICO);
        assertThat(o.getAbertaEm()).isEqualTo(T0);
        assertThat(o.naFila()).isTrue();
        assertThat(o.prioridadeFila()).isEqualTo(3);
        assertThat(o.getPrestacoes()).isEmpty();
    }

    @Test
    void abrirSemOsEInvalido() {
        assertThatThrownBy(() -> OrdemExecucao.abrir(null, null, null, null, relogio(T0)))
                .isInstanceOf(RegraNegocioException.class);
    }

    @Test
    void lapideEUmaOrdemCanceladaForaDaFila() {
        OrdemExecucao o = OrdemExecucao.lapide(OS, relogio(T0));

        assertThat(o.getStatus()).isEqualTo(StatusOrdem.CANCELADA);
        assertThat(o.naFila()).isFalse();
        assertThat(o.getSagaId()).isNull();
    }

    // ---- prestacoes e alocacoes ------------------------------------------------------------------

    @Test
    void incluirPrestacaoGuardaOSnapshotDoServico() {
        OrdemExecucao o = ordem();

        Prestacao p = o.incluirPrestacao(SERVICO, "Troca de oleo", new BigDecimal("80.00"));

        assertThat(o.getPrestacoes()).containsExactly(p);
        assertThat(p.getNomeServico()).isEqualTo("Troca de oleo");
        assertThat(p.isAtivo()).isTrue();
    }

    @Test
    void incluirPrestacaoComOMesmoServicoAtivoEInvalido() {
        OrdemExecucao o = ordem();
        o.incluirPrestacao(SERVICO, "Troca de oleo", BigDecimal.TEN);

        assertThatThrownBy(() -> o.incluirPrestacao(SERVICO, "Troca de oleo", BigDecimal.TEN))
                .isInstanceOf(RegraNegocioException.class).hasMessageContaining("já adicionado");
    }

    @Test
    void servicoDeUmaPrestacaoInativaPodeSerIncluidoDeNovo() {
        OrdemExecucao o = ordem();
        Prestacao primeira = o.incluirPrestacao(SERVICO, "Troca de oleo", BigDecimal.TEN);
        o.inativarPrestacao(primeira.getId());

        Prestacao segunda = o.incluirPrestacao(SERVICO, "Troca de oleo", BigDecimal.TEN);

        assertThat(segunda.getId()).isNotEqualTo(primeira.getId());
        assertThat(o.prestacoesAtivas()).containsExactly(segunda);
    }

    @Test
    void incluirPrestacaoComDadosInvalidosOuForaDeDiagnosticoEInvalido() {
        OrdemExecucao o = ordem();
        assertThatThrownBy(() -> o.incluirPrestacao(null, "x", BigDecimal.TEN)).isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> o.incluirPrestacao(SERVICO, "x", BigDecimal.ZERO)).isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> o.incluirPrestacao(SERVICO, "x", null)).isInstanceOf(RegraNegocioException.class);

        OrdemExecucao fechada = concluida();
        assertThatThrownBy(() -> fechada.incluirPrestacao(UUID.randomUUID(), "x", BigDecimal.TEN))
                .isInstanceOf(RegraNegocioException.class).hasMessageContaining("DIAGNOSTICO_CONCLUIDO");
    }

    @Test
    void inativarPrestacaoInativaTambemAsAlocacoes() {
        OrdemExecucao o = ordem();
        Prestacao p = o.incluirPrestacao(SERVICO, "Troca de oleo", BigDecimal.TEN);
        Alocacao a = o.alocarPeca(p.getId(), peca("Filtro", 10), 1);

        o.inativarPrestacao(p.getId());

        assertThat(p.isInativo()).isTrue();
        assertThat(a.isInativo()).isTrue();
    }

    @Test
    void inativarPrestacaoInexistenteInativaOuForaDeDiagnosticoEInvalido() {
        OrdemExecucao o = ordem();
        Prestacao p = o.incluirPrestacao(SERVICO, "Troca de oleo", BigDecimal.TEN);
        assertThatThrownBy(() -> o.inativarPrestacao(UUID.randomUUID()))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        o.inativarPrestacao(p.getId());
        assertThatThrownBy(() -> o.inativarPrestacao(p.getId())).isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("inativa");

        OrdemExecucao fechada = concluida();
        UUID id = fechada.getPrestacoes().get(0).getId();
        assertThatThrownBy(() -> fechada.inativarPrestacao(id)).isInstanceOf(RegraNegocioException.class);
    }

    @Test
    void alocarPecaGuardaOSnapshotEIgnoraOEstoque() {
        OrdemExecucao o = ordem();
        Prestacao p = o.incluirPrestacao(SERVICO, "Troca de oleo", BigDecimal.TEN);
        Peca filtro = peca("Filtro", 1);

        Alocacao a = o.alocarPeca(p.getId(), filtro, 5);

        assertThat(a.getPecaId()).isEqualTo(filtro.getId());
        assertThat(a.getDescricao()).isEqualTo("Filtro");
        assertThat(a.getValorUnitario()).isEqualByComparingTo("39.90");
        assertThat(a.getQuantidade()).isEqualTo(5);
        assertThat(filtro.getQtdReservada()).isZero();
        assertThat(filtro.getQtdDisponivel()).isEqualTo(1);
    }

    @Test
    void alocarAMesmaPecaNaMesmaPrestacaoEInvalido() {
        OrdemExecucao o = ordem();
        Prestacao p = o.incluirPrestacao(SERVICO, "Troca de oleo", BigDecimal.TEN);
        Peca filtro = peca("Filtro", 10);
        o.alocarPeca(p.getId(), filtro, 1);

        assertThatThrownBy(() -> o.alocarPeca(p.getId(), filtro, 1)).isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("já alocada");
    }

    @Test
    void alocarAMesmaPecaEmPrestacoesDiferentesEPermitido() {
        OrdemExecucao o = ordem();
        Prestacao p1 = o.incluirPrestacao(SERVICO, "A", BigDecimal.TEN);
        Prestacao p2 = o.incluirPrestacao(UUID.randomUUID(), "B", BigDecimal.TEN);
        Peca filtro = peca("Filtro", 10);

        o.alocarPeca(p1.getId(), filtro, 1);
        o.alocarPeca(p2.getId(), filtro, 3);

        assertThat(o.pecasParaReservar()).containsExactly(Map.entry(filtro.getId(), 4));
    }

    @Test
    void alocarPecaInvalida() {
        OrdemExecucao o = ordem();
        Prestacao p = o.incluirPrestacao(SERVICO, "Troca de oleo", BigDecimal.TEN);
        Peca inativa = peca("Filtro", 10);
        inativa.inativar();

        assertThatThrownBy(() -> o.alocarPeca(p.getId(), inativa, 1)).isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> o.alocarPeca(p.getId(), peca("F", 1), 0)).isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> o.alocarPeca(UUID.randomUUID(), peca("F", 1), 1))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        o.inativarPrestacao(p.getId());
        assertThatThrownBy(() -> o.alocarPeca(p.getId(), peca("F", 1), 1)).isInstanceOf(RegraNegocioException.class);
        OrdemExecucao fechada = concluida();
        UUID id = fechada.getPrestacoes().get(0).getId();
        assertThatThrownBy(() -> fechada.alocarPeca(id, peca("F", 1), 1)).isInstanceOf(RegraNegocioException.class);
    }

    @Test
    void inativarAlocacaoEDepoisDeNovoEInvalido() {
        OrdemExecucao o = ordem();
        Prestacao p = o.incluirPrestacao(SERVICO, "Troca de oleo", BigDecimal.TEN);
        Alocacao a = o.alocarPeca(p.getId(), peca("Filtro", 10), 1);

        o.inativarAlocacao(a.getId());

        assertThat(a.isInativo()).isTrue();
        assertThat(o.pecasParaReservar()).isEmpty();
        assertThatThrownBy(() -> o.inativarAlocacao(a.getId())).isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("já está inativa");
    }

    @Test
    void inativarAlocacaoInexistenteOuDePrestacaoInativaOuForaDeDiagnosticoEInvalido() {
        OrdemExecucao o = ordem();
        assertThatThrownBy(() -> o.inativarAlocacao(UUID.randomUUID()))
                .isInstanceOf(RecursoNaoEncontradoException.class);

        OrdemExecucao fechada = concluida();
        UUID alocacao = fechada.getPrestacoes().get(0).getAlocacoes().get(0).getId();
        assertThatThrownBy(() -> fechada.inativarAlocacao(alocacao)).isInstanceOf(RegraNegocioException.class);

        Prestacao p = o.incluirPrestacao(SERVICO, "Troca de oleo", BigDecimal.TEN);
        Alocacao a = o.alocarPeca(p.getId(), peca("Filtro", 10), 1);
        p.inativar();
        assertThatThrownBy(() -> o.inativarAlocacao(a.getId())).isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("Prestação de serviço está inativa");
    }

    // ---- diagnostico ------------------------------------------------------------------------------

    @Test
    void concluirDiagnosticoMudaOEstadoESobeNaFilaParaPrioridade2() {
        OrdemExecucao o = concluida();

        assertThat(o.getStatus()).isEqualTo(StatusOrdem.DIAGNOSTICO_CONCLUIDO);
        assertThat(o.getDiagnosticoConcluidoEm()).isEqualTo(T0.plusSeconds(60));
        assertThat(o.prioridadeFila()).isEqualTo(2);
    }

    @Test
    void concluirSemPrestacaoAtivaOuForaDeDiagnosticoEInvalido() {
        OrdemExecucao vazia = ordem();
        assertThatThrownBy(() -> vazia.concluirDiagnostico(relogio(T0))).isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("pelo menos uma prestação");

        Prestacao p = vazia.incluirPrestacao(SERVICO, "x", BigDecimal.TEN);
        vazia.inativarPrestacao(p.getId());
        assertThatThrownBy(() -> vazia.concluirDiagnostico(relogio(T0))).isInstanceOf(RegraNegocioException.class);

        OrdemExecucao fechada = concluida();
        assertThatThrownBy(() -> fechada.concluirDiagnostico(relogio(T0))).isInstanceOf(RegraNegocioException.class);
    }

    @Test
    void itensDoDiagnosticoTemUmaEntradaPorPrestacaoAtivaComAsPecasAtivas() {
        OrdemExecucao o = ordem();
        Prestacao p1 = o.incluirPrestacao(SERVICO, "Troca de oleo", new BigDecimal("80.00"));
        Prestacao p2 = o.incluirPrestacao(UUID.randomUUID(), "Alinhamento", new BigDecimal("50.00"));
        Peca filtro = peca("Filtro", 10);
        Alocacao ativa = o.alocarPeca(p1.getId(), filtro, 2);
        Alocacao removida = o.alocarPeca(p1.getId(), peca("Oleo", 10), 1);
        o.inativarAlocacao(removida.getId());
        o.inativarPrestacao(p2.getId());

        List<OrdemExecucao.ItemDiagnostico> itens = o.itensDoDiagnostico();

        assertThat(itens).hasSize(1);
        assertThat(itens.get(0).servicoId()).isEqualTo(SERVICO);
        assertThat(itens.get(0).descricao()).isEqualTo("Troca de oleo");
        assertThat(itens.get(0).precoMaoDeObra()).isEqualByComparingTo("80.00");
        assertThat(itens.get(0).pecas()).containsExactly(
                new OrdemExecucao.PecaDiagnostico(filtro.getId(), "Filtro", 2, ativa.getValorUnitario()));
    }

    @Test
    void reabrirSemReservaVoltaParaDiagnosticoEPrioridade3() {
        OrdemExecucao o = concluida();

        o.reabrirDiagnostico(false);

        assertThat(o.getStatus()).isEqualTo(StatusOrdem.EM_DIAGNOSTICO);
        assertThat(o.prioridadeFila()).isEqualTo(3);
    }

    @Test
    void reabrirComReservaAtivaOuForaDeDiagnosticoConcluidoEInvalido() {
        OrdemExecucao o = concluida();
        assertThatThrownBy(() -> o.reabrirDiagnostico(true)).isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("reserva");
        assertThatThrownBy(() -> ordem().reabrirDiagnostico(false)).isInstanceOf(RegraNegocioException.class);
    }

    // ---- Saga: reserva ----------------------------------------------------------------------------

    @Test
    void reservarPecasSoValeEmDiagnosticoConcluidoEnaoMudaOEstado() {
        OrdemExecucao o = concluida();

        o.reservarPecas();

        assertThat(o.getStatus()).isEqualTo(StatusOrdem.DIAGNOSTICO_CONCLUIDO);
        assertThatThrownBy(() -> ordem().reservarPecas()).isInstanceOf(RegraNegocioException.class);
    }

    @Test
    void recusarReservaVoltaParaDiagnosticoComOMotivo() {
        OrdemExecucao o = concluida();

        o.recusarReserva("Filtro: pediu 2, há 1");

        assertThat(o.getStatus()).isEqualTo(StatusOrdem.EM_DIAGNOSTICO);
        assertThat(o.getMotivo()).isEqualTo("Filtro: pediu 2, há 1");
        assertThatThrownBy(() -> ordem().recusarReserva("x")).isInstanceOf(RegraNegocioException.class);
    }

    @Test
    void liberarPorRevisaoOuOrcamentoFalhouReabreODiagnostico() {
        for (MotivoLiberacao motivo : List.of(MotivoLiberacao.REVISAO, MotivoLiberacao.ORCAMENTO_FALHOU)) {
            OrdemExecucao o = concluida();
            o.liberarReserva(motivo);
            assertThat(o.getStatus()).isEqualTo(StatusOrdem.EM_DIAGNOSTICO);
            assertThat(o.naFila()).isTrue();
        }
        OrdemExecucao jaEmDiagnostico = ordem();
        jaEmDiagnostico.liberarReserva(MotivoLiberacao.REVISAO);
        assertThat(jaEmDiagnostico.getStatus()).isEqualTo(StatusOrdem.EM_DIAGNOSTICO);
    }

    @Test
    void liberarPorRecusaExpiracaoOuDesistenciaCancelaEaiDaFila() {
        for (MotivoLiberacao motivo : List.of(MotivoLiberacao.ORCAMENTO_RECUSADO, MotivoLiberacao.ORCAMENTO_EXPIRADO,
                MotivoLiberacao.DESISTENCIA)) {
            OrdemExecucao o = concluida();
            o.liberarReserva(motivo);
            assertThat(o.getStatus()).isEqualTo(StatusOrdem.CANCELADA);
            assertThat(o.getMotivo()).isEqualTo(motivo.name());
            assertThat(o.naFila()).isFalse();
        }
        OrdemExecucao emDiagnostico = ordem();
        emDiagnostico.liberarReserva(MotivoLiberacao.DESISTENCIA);
        assertThat(emDiagnostico.getStatus()).isEqualTo(StatusOrdem.CANCELADA);
    }

    @Test
    void liberarEmOrdemJaCanceladaNaoFazNada() {
        OrdemExecucao o = concluida();
        o.cancelar();

        o.liberarReserva(MotivoLiberacao.REVISAO);

        assertThat(o.getStatus()).isEqualTo(StatusOrdem.CANCELADA);
    }

    @Test
    void liberarDepoisDoInicioDaExecucaoEInvalido() {
        OrdemExecucao o = emExecucao();
        assertThatThrownBy(() -> o.liberarReserva(MotivoLiberacao.DESISTENCIA))
                .isInstanceOf(RegraNegocioException.class);
        OrdemExecucao finalizada = emExecucao();
        finalizada.finalizarPrestacao(finalizada.getPrestacoes().get(0).getId(), relogio(T0.plusSeconds(500)));
        assertThatThrownBy(() -> finalizada.liberarReserva(MotivoLiberacao.REVISAO))
                .isInstanceOf(RegraNegocioException.class);
    }

    @Test
    void cancelarEmDiagnosticoOuConcluidoSaiDaFila() {
        OrdemExecucao emDiagnostico = ordem();
        emDiagnostico.cancelar();
        assertThat(emDiagnostico.getStatus()).isEqualTo(StatusOrdem.CANCELADA);
        assertThat(emDiagnostico.naFila()).isFalse();

        OrdemExecucao concluida = concluida();
        concluida.cancelar();
        assertThat(concluida.getStatus()).isEqualTo(StatusOrdem.CANCELADA);

        concluida.cancelar();
        assertThat(concluida.getStatus()).isEqualTo(StatusOrdem.CANCELADA);
    }

    @Test
    void cancelarDepoisDoInicioDaExecucaoEInvalido() {
        assertThatThrownBy(() -> emExecucao().cancelar()).isInstanceOf(RegraNegocioException.class);
    }

    // ---- execucao ---------------------------------------------------------------------------------

    @Test
    void iniciarExecucaoIniciaAsPrestacoesAtivasESobeParaPrioridade1() {
        OrdemExecucao o = ordem();
        Prestacao ativa = o.incluirPrestacao(SERVICO, "A", BigDecimal.TEN);
        Prestacao removida = o.incluirPrestacao(UUID.randomUUID(), "B", BigDecimal.TEN);
        o.inativarPrestacao(removida.getId());
        o.concluirDiagnostico(relogio(T0));

        o.iniciarExecucao(relogio(T0.plusSeconds(10)));

        assertThat(o.getStatus()).isEqualTo(StatusOrdem.EM_EXECUCAO);
        assertThat(o.getIniciadaEm()).isEqualTo(T0.plusSeconds(10));
        assertThat(ativa.getDataInicio()).isEqualTo(T0.plusSeconds(10));
        assertThat(removida.getDataInicio()).isNull();
        assertThat(o.prioridadeFila()).isEqualTo(1);
    }

    @Test
    void iniciarForaDeDiagnosticoConcluidoEInvalido() {
        assertThatThrownBy(() -> ordem().iniciarExecucao(relogio(T0))).isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> emExecucao().iniciarExecucao(relogio(T0))).isInstanceOf(RegraNegocioException.class);
    }

    @Test
    void finalizarAUltimaPrestacaoFinalizaAOrdemEAiDaFila() {
        OrdemExecucao o = ordem();
        Prestacao p1 = o.incluirPrestacao(SERVICO, "A", BigDecimal.TEN);
        Prestacao p2 = o.incluirPrestacao(UUID.randomUUID(), "B", BigDecimal.TEN);
        o.concluirDiagnostico(relogio(T0));
        o.iniciarExecucao(relogio(T0));

        boolean primeira = o.finalizarPrestacao(p1.getId(), relogio(T0.plus(Duration.ofMinutes(30))));
        boolean ultima = o.finalizarPrestacao(p2.getId(), relogio(T0.plus(Duration.ofMinutes(90))));

        assertThat(primeira).isFalse();
        assertThat(ultima).isTrue();
        assertThat(o.getStatus()).isEqualTo(StatusOrdem.FINALIZADA);
        assertThat(o.getFinalizadaEm()).isEqualTo(T0.plus(Duration.ofMinutes(90)));
        assertThat(o.naFila()).isFalse();
        assertThat(o.tempos()).containsExactly(
                new OrdemExecucao.TempoServico(SERVICO, 30), new OrdemExecucao.TempoServico(p2.getServicoId(), 90));
    }

    @Test
    void minutosArredondaParaBaixoENuncaFicaNegativo() {
        OrdemExecucao o = emExecucao();
        Prestacao p = o.getPrestacoes().get(0);

        assertThat(p.minutos()).isZero();
        o.finalizarPrestacao(p.getId(), relogio(T0.plusSeconds(120 + 119)));

        assertThat(p.minutos()).isEqualTo(1);

        OrdemExecucao retroativa = emExecucao();
        Prestacao q = retroativa.getPrestacoes().get(0);
        retroativa.finalizarPrestacao(q.getId(), relogio(T0));
        assertThat(q.minutos()).isZero();
    }

    @Test
    void finalizarPrestacaoInvalida() {
        OrdemExecucao o = emExecucao();
        UUID id = o.getPrestacoes().get(0).getId();
        assertThatThrownBy(() -> o.finalizarPrestacao(UUID.randomUUID(), relogio(T0)))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        assertThatThrownBy(() -> concluida().finalizarPrestacao(id, relogio(T0)))
                .isInstanceOf(RegraNegocioException.class);

        OrdemExecucao duas = ordem();
        Prestacao p1 = duas.incluirPrestacao(SERVICO, "A", BigDecimal.TEN);
        duas.incluirPrestacao(UUID.randomUUID(), "B", BigDecimal.TEN);
        duas.concluirDiagnostico(relogio(T0));
        duas.iniciarExecucao(relogio(T0));
        duas.finalizarPrestacao(p1.getId(), relogio(T0.plusSeconds(60)));
        assertThatThrownBy(() -> duas.finalizarPrestacao(p1.getId(), relogio(T0.plusSeconds(61))))
                .isInstanceOf(RegraNegocioException.class).hasMessageContaining("já finalizado");
    }

    @Test
    void prestacaoSemInicioOuInativaNaoFinalizaNemInicia() {
        Prestacao p = new Prestacao(SERVICO, "A", BigDecimal.TEN);
        assertThatThrownBy(() -> p.finalizar(T0)).isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("não foi iniciado");
        p.iniciar(T0);
        assertThatThrownBy(() -> p.iniciar(T0)).isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("já iniciado");
        p.inativar();
        assertThatThrownBy(() -> p.iniciar(T0)).isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> p.finalizar(T0)).isInstanceOf(RegraNegocioException.class);
    }

    // ---- idempotencia por chave de negocio (F5-h) ------------------------------------------------------

    @Test
    void comandoAtendidoGuardaACadeiaERespondeAReemissao() {
        OrdemExecucao o = ordem();
        UUID original = UUID.randomUUID();
        UUID reenvio = UUID.randomUUID();
        assertThat(o.comandoAtendido("ReservarPecas")).isEmpty();

        o.registrarComando("ReservarPecas", new ComandoAtendido(List.of(original), "PecasReservadas", "{}"));
        ComandoAtendido guardado = o.comandoAtendido("ReservarPecas").orElseThrow();

        assertThat(guardado.contem(original, null)).isTrue();
        assertThat(guardado.contem(UUID.randomUUID(), original)).isTrue();
        assertThat(guardado.contem(UUID.randomUUID(), UUID.randomUUID())).isFalse();
        ComandoAtendido comReenvio = guardado.comReenvio(reenvio);
        assertThat(comReenvio.ids()).containsExactly(original, reenvio);
        assertThat(comReenvio.contem(UUID.randomUUID(), reenvio)).isTrue();
        assertThat(comReenvio.comReenvio(reenvio)).isSameAs(comReenvio);
    }

    @Test
    void reconstruirPreservaPrestacoesEComandos() {
        OrdemExecucao o = concluida();
        o.registrarComando("X", new ComandoAtendido(List.of(UUID.randomUUID()), "Y", "{}"));

        OrdemExecucao copia = OrdemExecucao.reconstruir(o.getOsId(), o.getSagaId(), o.getVeiculoId(), o.getPlaca(),
                o.getStatus(), o.getAbertaEm(), o.getDiagnosticoConcluidoEm(), o.getIniciadaEm(),
                o.getFinalizadaEm(), o.getMotivo(), o.getPrestacoes(), o.getComandosAtendidos(), 7);

        assertThat(copia.getPrestacoes()).hasSize(1);
        assertThat(copia.getComandosAtendidos()).containsKey("X");
        assertThat(copia.getVersao()).isEqualTo(7);
    }
}
