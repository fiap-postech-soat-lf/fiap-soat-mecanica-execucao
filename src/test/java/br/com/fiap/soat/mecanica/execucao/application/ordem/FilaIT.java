package br.com.fiap.soat.mecanica.execucao.application.ordem;

import br.com.fiap.soat.mecanica.execucao.domain.ordem.OrdemExecucao;
import br.com.fiap.soat.mecanica.execucao.domain.ordem.OrdemRepository;
import br.com.fiap.soat.mecanica.execucao.domain.ordem.PaginaFila;
import br.com.fiap.soat.mecanica.execucao.domain.ordem.StatusOrdem;
import br.com.fiap.soat.mecanica.execucao.support.IntegracaoTest;
import br.com.fiap.soat.mecanica.execucao.support.JwtTestTokens;
import br.com.fiap.soat.mecanica.execucao.application.transacao.TransacaoPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Fila de execucao (GSI esparso {@code fila}, F5-j). As tabelas sao compartilhadas com os outros ITs, entao as
 * ordens deste teste ficam em 2020 e o teste olha so para elas dentro da fila inteira.
 */
class FilaIT extends IntegracaoTest {

    private static final Instant BASE = Instant.parse("2020-01-01T00:00:00Z");

    @Autowired
    private OrdemRepository ordens;
    @Autowired
    private TransacaoPort transacao;
    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;

    private final String mecanico = JwtTestTokens.funcionario(UUID.randomUUID(), "MECANICO");

    private static Clock em(int minutos) {
        return Clock.fixed(BASE.plusSeconds(minutos * 60L), ZoneOffset.UTC);
    }

    /** Ordem aberta no minuto {@code minutos} e levada ate o status pedido. */
    private OrdemExecucao criar(StatusOrdem destino, int minutos) {
        OrdemExecucao ordem = OrdemExecucao.abrir(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "FIL" + minutos,
                em(minutos));
        if (destino == StatusOrdem.CANCELADA) {
            ordem.cancelar();
        } else if (destino != StatusOrdem.EM_DIAGNOSTICO) {
            ordem.incluirPrestacao(UUID.randomUUID(), "Servico", new BigDecimal("50.00"));
            ordem.concluirDiagnostico(em(minutos));
            if (destino == StatusOrdem.EM_EXECUCAO || destino == StatusOrdem.FINALIZADA) {
                ordem.iniciarExecucao(em(minutos));
            }
            if (destino == StatusOrdem.FINALIZADA) {
                ordem.finalizarPrestacao(ordem.prestacoesAtivas().get(0).getId(), em(minutos + 1));
            }
        }
        transacao.executar(() -> ordens.salvar(ordem));
        return ordem;
    }

    /** Percorre a fila inteira, de {@code tamanho} em {@code tamanho}, e junta os osIds na ordem recebida. */
    private List<UUID> percorrer(int tamanho) {
        List<UUID> todos = new ArrayList<>();
        String cursor = null;
        do {
            PaginaFila pagina = ordens.listarFila(tamanho, cursor);
            assertThat(pagina.ordens()).hasSizeLessThanOrEqualTo(tamanho);
            pagina.ordens().forEach(o -> todos.add(o.getOsId()));
            cursor = pagina.proximoCursor();
        } while (cursor != null);
        return todos;
    }

    @Test
    void filaVemPorPrioridadeEDepoisPelaDataEIgnoraFinalizadasECanceladas() {
        // criadas fora de ordem
        OrdemExecucao diagnostico2 = criar(StatusOrdem.EM_DIAGNOSTICO, 20);
        OrdemExecucao execucao2 = criar(StatusOrdem.EM_EXECUCAO, 30);
        OrdemExecucao concluido = criar(StatusOrdem.DIAGNOSTICO_CONCLUIDO, 10);
        OrdemExecucao diagnostico1 = criar(StatusOrdem.EM_DIAGNOSTICO, 5);
        OrdemExecucao execucao1 = criar(StatusOrdem.EM_EXECUCAO, 25);
        OrdemExecucao finalizada = criar(StatusOrdem.FINALIZADA, 1);
        OrdemExecucao cancelada = criar(StatusOrdem.CANCELADA, 2);
        List<UUID> minhas = List.of(diagnostico2, execucao2, concluido, diagnostico1, execucao1, finalizada, cancelada)
                .stream().map(OrdemExecucao::getOsId).toList();

        List<UUID> naFila = percorrer(100).stream().filter(minhas::contains).toList();

        assertThat(naFila).containsExactly(execucao1.getOsId(), execucao2.getOsId(), concluido.getOsId(),
                diagnostico1.getOsId(), diagnostico2.getOsId());
    }

    @Test
    void paginacaoPorCursorNaoRepeteNemPulaNenhumaOrdem() {
        List<UUID> criadas = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            criadas.add(criar(StatusOrdem.EM_DIAGNOSTICO, 100 + i).getOsId());
        }

        List<UUID> paginado = percorrer(2);
        List<UUID> inteira = percorrer(100);

        assertThat(paginado).doesNotHaveDuplicates().containsAll(criadas);
        assertThat(paginado).as("a mesma ordem da consulta sem paginar").isEqualTo(inteira);
    }

    @Test
    void ultimaPaginaNaoTemCursorMesmoQuandoCabeExatamente() {
        PaginaFila inteira = ordens.listarFila(100, null);
        int total = percorrer(100).size();
        assertThat(inteira.ordens()).hasSize(Math.min(total, 100));

        PaginaFila exata = ordens.listarFila(Math.max(total, 1), null);

        assertThat(exata.proximoCursor()).isNull();
    }

    @Test
    void cursorInvalidoELancaIllegalArgument() {
        assertThatThrownBy(() -> ordens.listarFila(10, "***"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Cursor");
        String semSeparador = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString("abc".getBytes());
        assertThatThrownBy(() -> ordens.listarFila(10, semSeparador)).isInstanceOf(IllegalArgumentException.class);
        String semUuid = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString("x|y".getBytes());
        assertThatThrownBy(() -> ordens.listarFila(10, semUuid)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rotaDaFilaPaginaPorCursorECursorInvalidoE400() throws Exception {
        List<UUID> criadas = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            criadas.add(criar(StatusOrdem.EM_DIAGNOSTICO, 200 + i).getOsId());
        }

        List<UUID> vistas = new ArrayList<>();
        String cursor = null;
        do {
            var requisicao = get("/execucoes/fila").param("size", "2").header("Authorization", "Bearer " + mecanico);
            if (cursor != null) {
                requisicao.param("cursor", cursor);
            }
            JsonNode pagina = objectMapper.readTree(mvc.perform(requisicao).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString());
            assertThat(pagina.path("itens").size()).isLessThanOrEqualTo(2);
            pagina.path("itens").forEach(i -> {
                vistas.add(UUID.fromString(i.path("osId").asText()));
                assertThat(i.path("status").asText()).isIn("EM_EXECUCAO", "DIAGNOSTICO_CONCLUIDO", "EM_DIAGNOSTICO");
                assertThat(i.has("placa")).isTrue();
                assertThat(i.has("abertaEm")).isTrue();
                assertThat(i.path("prestacoes").isInt()).isTrue();
            });
            cursor = pagina.path("proximoCursor").isNull() ? null : pagina.path("proximoCursor").asText();
        } while (cursor != null);

        assertThat(vistas).doesNotHaveDuplicates().containsAll(criadas);
        mvc.perform(get("/execucoes/fila").param("cursor", "***").header("Authorization", "Bearer " + mecanico))
                .andExpect(status().isBadRequest());
    }
}
