package br.com.fiap.soat.mecanica.execucao.application.saga;

import br.com.fiap.soat.mecanica.execucao.application.mensageria.MensagemEnvelope;
import br.com.fiap.soat.mecanica.execucao.domain.ordem.OrdemExecucao;
import br.com.fiap.soat.mecanica.execucao.domain.ordem.StatusOrdem;
import br.com.fiap.soat.mecanica.execucao.domain.peca.Peca;
import br.com.fiap.soat.mecanica.execucao.support.ContratosTeste;
import br.com.fiap.soat.mecanica.execucao.support.JwtTestTokens;
import br.com.fiap.soat.mecanica.execucao.support.SagaIntegracaoTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import software.amazon.awssdk.services.sqs.model.Message;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A Saga do ponto de vista do Execucao, pelas filas reais de teste: os comandos entram pela fila consumida pelo
 * {@code SqsConsumidor} e as respostas saem pela outbox e pelo topico ate a fila assinante. O diagnostico e feito
 * pelo REST, como o mecanico faria. Cada envelope recebido e validado contra o schema do proprio Execucao.
 */
class SagaParticipanteIT extends SagaIntegracaoTest {

    private final UUID servico = UUID.randomUUID();
    private final String mecanico = JwtTestTokens.funcionario(UUID.randomUUID(), "MECANICO");
    private final String almoxarife = JwtTestTokens.funcionario(UUID.randomUUID(), "ALMOXARIFE");
    private final List<JsonNode> recebidos = new ArrayList<>();

    @Autowired
    private MockMvc mvc;

    @BeforeEach
    void servicoAtivoNoCatalogoDoOs() {
        OS.resetAll();
        OS.stubFor(get(urlEqualTo("/servicos/" + servico)).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"id\":\"%s\",\"status\":\"ATIVO\",\"nome\":\"Troca de oleo\",\"descricao\":\"d\"}"
                        .formatted(servico))));
    }

    @Test
    void cicloCompletoDaSagaComRodadaNovaEReenvioDaExecucao() throws Exception {
        Peca peca = criarPeca(10);
        UUID osId = UUID.randomUUID();
        UUID sagaId = UUID.randomUUID();

        // 1. abertura
        MensagemEnvelope abrir = enviar("AbrirDiagnostico", osId, sagaId, null,
                Map.of("osId", osId, "veiculoId", UUID.randomUUID(), "placa", "ABC1D23"));
        JsonNode aberto = aguardar(osId, "DiagnosticoAberto", 1).get(0);
        assertThat(aberto.path("causationId").asText()).isEqualTo(abrir.messageId().toString());
        assertThat(aberto.path("sagaId").asText()).isEqualTo(sagaId.toString());
        assertThat(recarregar(osId).getStatus()).isEqualTo(StatusOrdem.EM_DIAGNOSTICO);

        // 2. diagnostico pelo REST, com uma peca; o DiagnosticoConcluido usa o sagaId guardado na ordem
        concluirDiagnostico(osId, peca, 2);
        JsonNode concluido = aguardar(osId, "DiagnosticoConcluido", 1).get(0);
        assertThat(concluido.path("sagaId").asText()).isEqualTo(sagaId.toString());

        // 3. reserva
        MensagemEnvelope reservar = enviar("ReservarPecas", osId, sagaId, null, Map.of("osId", osId,
                "pecas", List.of(Map.of("pecaId", peca.getId(), "quantidade", 2))));
        JsonNode reservada = aguardar(osId, "PecasReservadas", 1).get(0);
        assertThat(reservada.path("causationId").asText()).isEqualTo(reservar.messageId().toString());
        assertEstoque(peca, 10, 8, 2);

        // 4. revisao: devolve ao estoque e reabre o diagnostico
        MensagemEnvelope revisao = enviar("LiberarReservaPecas", osId, sagaId, null,
                Map.of("osId", osId, "motivo", "REVISAO"));
        JsonNode liberada = aguardar(osId, "ReservaPecasLiberada", 1).get(0);
        assertThat(liberada.path("causationId").asText()).isEqualTo(revisao.messageId().toString());
        assertEstoque(peca, 10, 10, 0);
        assertThat(recarregar(osId).getStatus()).isEqualTo(StatusOrdem.EM_DIAGNOSTICO);

        // 5. nova conclusao e nova reserva (nova rodada: messageId novo, fora da cadeia)
        enviar(patch("/execucoes/{os}/diagnostico/concluir", osId), mecanico).andExpect(status().isOk());
        aguardar(osId, "DiagnosticoConcluido", 2);
        MensagemEnvelope reservar2 = enviar("ReservarPecas", osId, sagaId, null, Map.of("osId", osId,
                "pecas", List.of(Map.of("pecaId", peca.getId(), "quantidade", 2))));
        aguardar(osId, "PecasReservadas", 2);
        assertEstoque(peca, 10, 8, 2);
        assertThat(recarregar(osId).comandoAtendido("ReservarPecas").orElseThrow().ids())
                .as("a nova rodada substitui a cadeia").containsExactly(reservar2.messageId());

        // 6. inicio da execucao: baixa o estoque
        MensagemEnvelope iniciar = enviar("IniciarExecucao", osId, sagaId, null, Map.of("osId", osId));
        JsonNode iniciada = aguardar(osId, "ExecucaoIniciada", 1).get(0);
        assertThat(iniciada.path("causationId").asText()).isEqualTo(iniciar.messageId().toString());
        assertThat(iniciada.at("/payload/iniciadaEm").asText()).isNotBlank();
        assertEstoque(peca, 8, 8, 0);
        assertThat(recarregar(osId).getStatus()).isEqualTo(StatusOrdem.EM_EXECUCAO);

        // 7. F4-h: o OS reenvia (messageId novo, causationId = o original) e depois reenvia de novo, desta vez
        //    apontando para o 1o reenvio; as duas respostas sao reemitidas, sem nova baixa
        MensagemEnvelope reenvio1 = enviar("IniciarExecucao", osId, sagaId, iniciar.messageId(), Map.of("osId", osId));
        JsonNode reemitida1 = aguardar(osId, "ExecucaoIniciada", 2).get(1);
        assertThat(reemitida1.path("causationId").asText()).isEqualTo(reenvio1.messageId().toString());
        assertThat(reemitida1.path("messageId").asText()).isNotEqualTo(iniciada.path("messageId").asText());
        assertThat(reemitida1.at("/payload/iniciadaEm").asText()).isEqualTo(iniciada.at("/payload/iniciadaEm").asText());

        MensagemEnvelope reenvio2 = enviar("IniciarExecucao", osId, sagaId, reenvio1.messageId(), Map.of("osId", osId));
        JsonNode reemitida2 = aguardar(osId, "ExecucaoIniciada", 3).get(2);
        assertThat(reemitida2.path("causationId").asText()).isEqualTo(reenvio2.messageId().toString());
        assertEstoque(peca, 8, 8, 0);
        assertThat(recarregar(osId).comandoAtendido("IniciarExecucao").orElseThrow().ids())
                .containsExactly(iniciar.messageId(), reenvio1.messageId(), reenvio2.messageId());

        // 8. a unica prestacao e finalizada pelo REST: a ordem fecha, sai da fila e o ExecucaoFinalizada
        //    (validado contra o schema) leva o sagaId guardado na ordem e o tempo do servico
        UUID prestacaoId = recarregar(osId).prestacoesAtivas().get(0).getId();
        enviar(patch("/execucoes/{os}/prestacoes/{p}/finalizar", osId, prestacaoId), mecanico)
                .andExpect(status().isOk());
        JsonNode finalizada = aguardar(osId, "ExecucaoFinalizada", 1).get(0);
        assertThat(finalizada.path("sagaId").asText()).isEqualTo(sagaId.toString());
        assertThat(finalizada.at("/payload/tempos/0/servicoId").asText()).isEqualTo(servico.toString());
        assertThat(finalizada.at("/payload/tempos/0/minutos").asLong()).isZero();
        OrdemExecucao fim = recarregar(osId);
        assertThat(fim.getStatus()).isEqualTo(StatusOrdem.FINALIZADA);
        assertThat(item(tabelas.ordens(), "osId", osId.toString())).doesNotContainKey("fila");
    }

    @Test
    void recusaPorFaltaDeEstoqueDevolveAOrdemParaODiagnosticoERespondeAsFaltantes() throws Exception {
        Peca peca = criarPeca(1);
        UUID osId = UUID.randomUUID();
        UUID sagaId = UUID.randomUUID();
        enviar("AbrirDiagnostico", osId, sagaId, null,
                Map.of("osId", osId, "veiculoId", UUID.randomUUID(), "placa", "ABC1D23"));
        aguardar(osId, "DiagnosticoAberto", 1);
        concluirDiagnostico(osId, peca, 3);
        aguardar(osId, "DiagnosticoConcluido", 1);

        enviar("ReservarPecas", osId, sagaId, null, Map.of("osId", osId,
                "pecas", List.of(Map.of("pecaId", peca.getId(), "quantidade", 3))));

        JsonNode recusada = aguardar(osId, "ReservaPecasRecusada", 1).get(0);
        assertThat(recusada.at("/payload/faltantes/0/solicitada").asInt()).isEqualTo(3);
        assertThat(recusada.at("/payload/faltantes/0/disponivel").asInt()).isEqualTo(1);
        assertEstoque(peca, 1, 1, 0);
        OrdemExecucao ordem = recarregar(osId);
        assertThat(ordem.getStatus()).isEqualTo(StatusOrdem.EM_DIAGNOSTICO);
        assertThat(ordem.getMotivo()).contains(peca.getId().toString());
    }

    @Test
    void reenvioDoAbrirDiagnosticoReemiteODiagnosticoAbertoSemRecriarAOrdem() throws Exception {
        UUID osId = UUID.randomUUID();
        UUID sagaId = UUID.randomUUID();
        Map<String, Object> payload = Map.of("osId", osId, "veiculoId", UUID.randomUUID(), "placa", "ABC1D23");
        enviar("AbrirDiagnostico", osId, sagaId, null, payload);
        JsonNode primeiro = aguardar(osId, "DiagnosticoAberto", 1).get(0);

        // reenvio fora da cadeia (causationId solto): como a ordem ja existe, a resposta e reemitida mesmo assim
        enviar("AbrirDiagnostico", osId, sagaId, UUID.randomUUID(), payload);

        JsonNode segundo = aguardar(osId, "DiagnosticoAberto", 2).get(1);
        assertThat(segundo.at("/payload/abertoEm").asText()).isEqualTo(primeiro.at("/payload/abertoEm").asText());
        assertThat(recarregar(osId).getStatus()).isEqualTo(StatusOrdem.EM_DIAGNOSTICO);
    }

    // ---- REST --------------------------------------------------------------------------------------

    private void concluirDiagnostico(UUID osId, Peca peca, int quantidade) throws Exception {
        String corpoPrestacao = "{\"servicoId\":\"%s\",\"precoMaoDeObra\":80.00}".formatted(servico);
        String resposta = enviar(post("/execucoes/{os}/prestacoes", osId), mecanico, corpoPrestacao)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String prestacaoId = objectMapper.readTree(resposta).get("id").asText();
        enviar(post("/execucoes/{os}/prestacoes/{p}/pecas", osId, prestacaoId), almoxarife,
                "{\"pecaId\":\"%s\",\"quantidade\":%d}".formatted(peca.getId(), quantidade))
                .andExpect(status().isCreated());
        enviar(patch("/execucoes/{os}/diagnostico/concluir", osId), mecanico).andExpect(status().isOk());
    }

    private ResultActions enviar(MockHttpServletRequestBuilder requisicao, String token) throws Exception {
        return enviar(requisicao, token, null);
    }

    private ResultActions enviar(MockHttpServletRequestBuilder requisicao, String token, String corpo) throws Exception {
        requisicao.header("Authorization", "Bearer " + token);
        if (corpo != null) {
            requisicao.contentType(MediaType.APPLICATION_JSON).content(corpo);
        }
        return mvc.perform(requisicao);
    }

    // ---- mensageria --------------------------------------------------------------------------------

    /** Envia o comando pela fila consumida pela aplicacao. */
    private MensagemEnvelope enviar(String tipo, UUID osId, UUID sagaId, UUID causationId, Map<String, ?> payload)
            throws Exception {
        MensagemEnvelope envelope = comando(tipo, osId, sagaId, causationId, payload);
        String corpo = toJson(envelope);
        SQS.sendMessage(r -> r.queueUrl(MENSAGERIA.filaEntrada()).messageBody(corpo));
        return envelope;
    }

    /** Espera ate haver {@code quantidade} envelopes do tipo para a ordem (todos validados no schema); devolve-os em ordem. */
    private List<JsonNode> aguardar(UUID osId, String tipo, int quantidade) throws Exception {
        long limite = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (System.nanoTime() < limite) {
            for (Message mensagem : receber(MENSAGERIA.filaAssinante())) {
                if (mensagem.body().contains(osId.toString())) {
                    recebidos.add(ContratosTeste.validar(mensagem.body()));
                }
            }
            List<JsonNode> doTipo = recebidos.stream()
                    .filter(e -> osId.toString().equals(e.path("osId").asText()) && tipo.equals(e.path("type").asText()))
                    .toList();
            if (doTipo.size() >= quantidade) {
                return doTipo;
            }
        }
        throw new AssertionError("Esperava " + quantidade + " x " + tipo + " para a ordem " + osId
                + ", recebidos: " + recebidos.stream().filter(e -> osId.toString().equals(e.path("osId").asText()))
                .map(e -> e.path("type").asText()).toList());
    }

    private String toJson(MensagemEnvelope envelope) throws Exception {
        return objectMapper.writeValueAsString(envelope);
    }

    private void assertEstoque(Peca peca, int estoque, int disponivel, int reservada) {
        Peca atual = peca(peca.getId());
        assertThat(atual.getQtdEstoque()).as("qtdEstoque").isEqualTo(estoque);
        assertThat(atual.getQtdDisponivel()).as("qtdDisponivel").isEqualTo(disponivel);
        assertThat(atual.getQtdReservada()).as("qtdReservada").isEqualTo(reservada);
    }
}
