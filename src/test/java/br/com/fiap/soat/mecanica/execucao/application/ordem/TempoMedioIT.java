package br.com.fiap.soat.mecanica.execucao.application.ordem;

import br.com.fiap.soat.mecanica.execucao.adapters.out.dynamo.TabelasDynamo;
import br.com.fiap.soat.mecanica.execucao.application.mensageria.MensagemFactory;
import br.com.fiap.soat.mecanica.execucao.application.mensageria.OutboxPort;
import br.com.fiap.soat.mecanica.execucao.application.ordem.usecase.FinalizarPrestacaoUseCase;
import br.com.fiap.soat.mecanica.execucao.application.ordem.usecase.OrdemSupport;
import br.com.fiap.soat.mecanica.execucao.application.transacao.TransacaoPort;
import br.com.fiap.soat.mecanica.execucao.domain.exception.RegraNegocioException;
import br.com.fiap.soat.mecanica.execucao.domain.ordem.OrdemExecucao;
import br.com.fiap.soat.mecanica.execucao.domain.ordem.OrdemRepository;
import br.com.fiap.soat.mecanica.execucao.domain.ordem.TempoRepository;
import br.com.fiap.soat.mecanica.execucao.support.IntegracaoTest;
import br.com.fiap.soat.mecanica.execucao.support.JwtTestTokens;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Tempo medio por servico (F5-d): {@code ADD} em {@code execucao-tempos} na mesma transacao da finalizacao. */
class TempoMedioIT extends IntegracaoTest {

    private static final Instant T0 = Instant.parse("2026-11-06T12:00:00Z");

    @Autowired
    private OrdemRepository ordens;
    @Autowired
    private TempoRepository tempos;
    @Autowired
    private OrdemSupport ordemSupport;
    @Autowired
    private OutboxPort outbox;
    @Autowired
    private MensagemFactory mensagens;
    @Autowired
    private TransacaoPort transacao;
    @Autowired
    private TabelasDynamo tabelas;
    @Autowired
    private MockMvc mvc;

    private final UUID servico = UUID.randomUUID();
    private final String mecanico = JwtTestTokens.funcionario(UUID.randomUUID(), "MECANICO");

    /** Ordem em execucao desde T0 (relogio controlado), com uma prestacao do servico do teste. */
    private OrdemExecucao emExecucao() {
        Clock abertura = Clock.fixed(T0, ZoneOffset.UTC);
        OrdemExecucao ordem = OrdemExecucao.abrir(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "TMP1A23",
                abertura);
        ordem.incluirPrestacao(servico, "Troca de oleo", new BigDecimal("80.00"));
        ordem.concluirDiagnostico(abertura);
        ordem.iniciarExecucao(abertura);
        transacao.executar(() -> ordens.salvar(ordem));
        return ordem;
    }

    private void finalizar(OrdemExecucao ordem, int minutosDepois) {
        FinalizarPrestacaoUseCase useCase = new FinalizarPrestacaoUseCase(ordemSupport, ordens, tempos, outbox,
                mensagens, transacao, Clock.fixed(T0.plusSeconds(minutosDepois * 60L), ZoneOffset.UTC));
        useCase.executar(ordem.getOsId(), ordem.prestacoesAtivas().get(0).getId());
    }

    @Test
    void duasOrdensFinalizadasComOMesmoServicoDao45DeMediaEDuasAmostras() throws Exception {
        finalizar(emExecucao(), 30);
        finalizar(emExecucao(), 60);

        var item = item(tabelas.tempos(), "servicoId", servico.toString());
        assertThat(item.get("totalMinutos").n()).isEqualTo("90");
        assertThat(item.get("amostras").n()).isEqualTo("2");
        assertThat(item.get("nomeServico").s()).isEqualTo("Troca de oleo");

        mvc.perform(get("/execucoes/tempo-medio").param("servicoId", servico.toString())
                        .header("Authorization", "Bearer " + mecanico))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].servicoId").value(servico.toString()))
                .andExpect(jsonPath("$[0].nomeServico").value("Troca de oleo"))
                .andExpect(jsonPath("$[0].minutosMedio").value(45.0))
                .andExpect(jsonPath("$[0].amostras").value(2));
        // sem servicoId, o servico aparece entre todos (Scan da tabela pequena)
        mvc.perform(get("/execucoes/tempo-medio").header("Authorization", "Bearer " + mecanico))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].servicoId", hasItem(servico.toString())));
    }

    @Test
    void itemSemNomeNaTabelaNaoDerrubaAListagem() throws Exception {
        UUID semNome = UUID.randomUUID();
        DYNAMO.putItem(r -> r.tableName(tabelas.tempos()).item(java.util.Map.of(
                "servicoId", software.amazon.awssdk.services.dynamodb.model.AttributeValue.fromS(semNome.toString()),
                "totalMinutos", software.amazon.awssdk.services.dynamodb.model.AttributeValue.fromN("5"),
                "amostras", software.amazon.awssdk.services.dynamodb.model.AttributeValue.fromN("1"))));

        mvc.perform(get("/execucoes/tempo-medio").header("Authorization", "Bearer " + mecanico))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].servicoId", hasItem(semNome.toString())));
    }

    @Test
    void servicoSemAmostrasVoltaListaVazia() throws Exception {
        mvc.perform(get("/execucoes/tempo-medio").param("servicoId", UUID.randomUUID().toString())
                        .header("Authorization", "Bearer " + mecanico))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void repetirAFinalizacaoNaoContaAAmostraDeNovo() {
        OrdemExecucao ordem = emExecucao();
        UUID prestacaoId = ordem.prestacoesAtivas().get(0).getId();
        finalizar(ordem, 10);

        // repetir o PATCH: a ordem ja esta FINALIZADA (422), e a amostra nao e contada de novo
        assertThatThrownBy(() -> finalizar(ordem, 20)).isInstanceOf(RegraNegocioException.class);

        assertThat(tempos.buscar(servico).orElseThrow().amostras()).isEqualTo(1);
        assertThat(ordens.buscarPorId(ordem.getOsId()).orElseThrow().buscarPrestacao(prestacaoId).minutos())
                .isEqualTo(10);
    }
}
