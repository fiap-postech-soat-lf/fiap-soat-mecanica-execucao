package br.com.fiap.soat.mecanica.execucao.adapters.in.web.ordem;

import br.com.fiap.soat.mecanica.execucao.application.transacao.TransacaoPort;
import br.com.fiap.soat.mecanica.execucao.domain.ordem.OrdemExecucao;
import br.com.fiap.soat.mecanica.execucao.domain.ordem.OrdemRepository;
import br.com.fiap.soat.mecanica.execucao.domain.ordem.Reserva;
import br.com.fiap.soat.mecanica.execucao.domain.ordem.ReservaRepository;
import br.com.fiap.soat.mecanica.execucao.domain.peca.Peca;
import br.com.fiap.soat.mecanica.execucao.domain.peca.PecaRepository;
import br.com.fiap.soat.mecanica.execucao.support.ContratosTeste;
import br.com.fiap.soat.mecanica.execucao.support.IntegracaoTest;
import br.com.fiap.soat.mecanica.execucao.support.JwtTestTokens;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import software.amazon.awssdk.services.sqs.model.Message;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Diagnostico de ponta a ponta pelo REST: LocalStack (DynamoDB, SNS, SQS) e WireMock no lugar do OS. Cada teste
 * cria a propria ordem e as proprias pecas (as tabelas sao compartilhadas).
 */
class DiagnosticoIT extends IntegracaoTest {

    /** Servico novo por teste: o cache do catalogo do contexto compartilhado nao vaza entre eles. */
    private final UUID servico = UUID.randomUUID();

    private final String mecanico = JwtTestTokens.funcionario(UUID.randomUUID(), "MECANICO");
    private final String almoxarife = JwtTestTokens.funcionario(UUID.randomUUID(), "ALMOXARIFE");

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private OrdemRepository ordens;
    @Autowired
    private PecaRepository pecas;
    @Autowired
    private ReservaRepository reservas;
    @Autowired
    private TransacaoPort transacao;
    @Autowired
    private Clock clock;

    @BeforeEach
    void servicoAtivoNoCatalogoDoOs() {
        OS.resetAll();
        stubServico(servico, "ATIVO");
    }

    private static void stubServico(UUID id, String status) {
        OS.stubFor(get(urlEqualTo("/servicos/" + id)).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"id\":\"%s\",\"status\":\"%s\",\"nome\":\"Troca de oleo\",\"descricao\":\"d\"}"
                        .formatted(id, status))));
    }

    private String prestacaoJson(UUID id) {
        return "{\"servicoId\":\"%s\",\"precoMaoDeObra\":80.00}".formatted(id);
    }

    private OrdemExecucao criarOrdem() {
        OrdemExecucao ordem = OrdemExecucao.abrir(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "ABC1D23", clock);
        transacao.executar(() -> ordens.salvar(ordem));
        return ordem;
    }

    private Peca criarPeca(int estoque) {
        Peca peca = new Peca("Filtro " + UUID.randomUUID(), "Bosch", new BigDecimal("39.90"), estoque);
        transacao.executar(() -> pecas.salvar(peca));
        return peca;
    }

    private ResultActions enviar(MockHttpServletRequestBuilder requisicao, String token, String corpo) throws Exception {
        requisicao.header("Authorization", "Bearer " + token);
        if (corpo != null) {
            requisicao.contentType(MediaType.APPLICATION_JSON).content(corpo);
        }
        return mvc.perform(requisicao);
    }

    private String incluirPrestacao(UUID osId) throws Exception {
        String resposta = enviar(post("/execucoes/{os}/prestacoes", osId), mecanico,
                prestacaoJson(servico))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(resposta).get("id").asText();
    }

    @Test
    void diagnosticoCompletoPublicaODiagnosticoConcluidoValidoNoContrato() throws Exception {
        OrdemExecucao ordem = criarOrdem();
        Peca peca = criarPeca(10);

        enviar(post("/execucoes/{os}/prestacoes", ordem.getOsId()), mecanico,
                prestacaoJson(servico))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nomeServico").value("Troca de oleo"))
                .andExpect(jsonPath("$.status").value("ATIVO"))
                .andExpect(jsonPath("$.servicoId").value(servico.toString()));
        String prestacaoId = objectMapper.readTree(enviar(get("/execucoes/{os}", ordem.getOsId()), mecanico, null)
                .andReturn().getResponse().getContentAsString()).at("/prestacoes/0/id").asText();

        enviar(post("/execucoes/{os}/prestacoes/{p}/pecas", ordem.getOsId(), prestacaoId), almoxarife,
                "{\"pecaId\":\"%s\",\"quantidade\":2}".formatted(peca.getId()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.descricao").value(peca.getNome()))
                .andExpect(jsonPath("$.quantidade").value(2));

        // alocar nao mexe no estoque
        enviar(get("/pecas/{id}", peca.getId()), almoxarife, null)
                .andExpect(jsonPath("$.qtdDisponivel").value(10))
                .andExpect(jsonPath("$.qtdReservada").value(0));

        enviar(patch("/execucoes/{os}/diagnostico/concluir", ordem.getOsId()), mecanico, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DIAGNOSTICO_CONCLUIDO"));

        JsonNode envelope = aguardarMensagem(ordem.getOsId(), "DiagnosticoConcluido");
        assertThat(envelope.path("sagaId").asText()).isEqualTo(ordem.getSagaId().toString());
        JsonNode item = envelope.at("/payload/itens/0");
        assertThat(item.path("servicoId").asText()).isEqualTo(servico.toString());
        assertThat(item.path("descricao").asText()).isEqualTo("Troca de oleo");
        assertThat(item.path("precoMaoDeObra").decimalValue()).isEqualByComparingTo("80.00");
        assertThat(item.at("/pecas/0/pecaId").asText()).isEqualTo(peca.getId().toString());
        assertThat(item.at("/pecas/0/quantidade").asInt()).isEqualTo(2);
        assertThat(item.at("/pecas/0/valorUnitario").decimalValue()).isEqualByComparingTo("39.90");

        enviar(get("/execucoes/{os}", ordem.getOsId()), almoxarife, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DIAGNOSTICO_CONCLUIDO"))
                .andExpect(jsonPath("$.reservas").isEmpty());
    }

    @Test
    void oJwtDoMecanicoERepassadoAoOs() throws Exception {
        OrdemExecucao ordem = criarOrdem();

        incluirPrestacao(ordem.getOsId());

        OS.verify(getRequestedFor(urlEqualTo("/servicos/" + servico)).withHeader("Authorization", equalTo("Bearer " + mecanico)));
    }

    @Test
    void reabrirSemReservaVoltaParaEmDiagnostico() throws Exception {
        OrdemExecucao ordem = criarOrdem();
        incluirPrestacao(ordem.getOsId());
        enviar(patch("/execucoes/{os}/diagnostico/concluir", ordem.getOsId()), mecanico, null).andExpect(status().isOk());

        enviar(patch("/execucoes/{os}/diagnostico/reabrir", ordem.getOsId()), mecanico, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EM_DIAGNOSTICO"));
    }

    @Test
    void reabrirComReservaAtivaE422() throws Exception {
        OrdemExecucao ordem = criarOrdem();
        incluirPrestacao(ordem.getOsId());
        enviar(patch("/execucoes/{os}/diagnostico/concluir", ordem.getOsId()), mecanico, null).andExpect(status().isOk());
        transacao.executar(() -> reservas.registrar(Reserva.nova(ordem.getOsId(), UUID.randomUUID(), 1, clock.instant())));

        enviar(patch("/execucoes/{os}/diagnostico/reabrir", ordem.getOsId()), mecanico, null)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem").value(org.hamcrest.Matchers.containsString("reserva")));
        enviar(get("/execucoes/{os}", ordem.getOsId()), mecanico, null)
                .andExpect(jsonPath("$.status").value("DIAGNOSTICO_CONCLUIDO"))
                .andExpect(jsonPath("$.reservas[0].status").value("ATIVA"));
    }

    @Test
    void concluirSemPrestacaoE422ENadaEPublicado() throws Exception {
        OrdemExecucao ordem = criarOrdem();

        enviar(patch("/execucoes/{os}/diagnostico/concluir", ordem.getOsId()), mecanico, null)
                .andExpect(status().isUnprocessableEntity());

        assertThat(mensagensPublicadas(ordem.getOsId(), Duration.ofSeconds(3))).isEmpty();
    }

    @Test
    void inativarPrestacaoEAlocacaoSaemDoDiagnostico() throws Exception {
        OrdemExecucao ordem = criarOrdem();
        Peca peca = criarPeca(10);
        String prestacao = incluirPrestacao(ordem.getOsId());
        String alocacao = objectMapper.readTree(enviar(
                post("/execucoes/{os}/prestacoes/{p}/pecas", ordem.getOsId(), prestacao), almoxarife,
                "{\"pecaId\":\"%s\",\"quantidade\":1}".formatted(peca.getId()))
                .andReturn().getResponse().getContentAsString()).get("id").asText();

        enviar(patch("/execucoes/{os}/alocacoes/{a}/inativar", ordem.getOsId(), alocacao), almoxarife, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INATIVO"));
        enviar(patch("/execucoes/{os}/prestacoes/{p}/inativar", ordem.getOsId(), prestacao), mecanico, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INATIVO"));
        enviar(patch("/execucoes/{os}/diagnostico/concluir", ordem.getOsId()), mecanico, null)
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void mesmoServicoDuasVezesE422() throws Exception {
        OrdemExecucao ordem = criarOrdem();
        incluirPrestacao(ordem.getOsId());

        enviar(post("/execucoes/{os}/prestacoes", ordem.getOsId()), mecanico,
                prestacaoJson(servico))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void alocarPecaInexistenteE404() throws Exception {
        OrdemExecucao ordem = criarOrdem();
        String prestacao = incluirPrestacao(ordem.getOsId());

        enviar(post("/execucoes/{os}/prestacoes/{p}/pecas", ordem.getOsId(), prestacao), almoxarife,
                "{\"pecaId\":\"%s\",\"quantidade\":1}".formatted(UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void ordemDesconhecidaE404() throws Exception {
        enviar(get("/execucoes/{os}", UUID.randomUUID()), mecanico, null).andExpect(status().isNotFound());
    }

    @Test
    void servicoInativoOuInexistenteNoCatalogoE422ComCodigo() throws Exception {
        OrdemExecucao ordem = criarOrdem();
        UUID inativo = UUID.randomUUID();
        stubServico(inativo, "INATIVO");

        enviar(post("/execucoes/{os}/prestacoes", ordem.getOsId()), mecanico, prestacaoJson(inativo))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.erro").value("SERVICO_INVALIDO"));

        UUID inexistente = UUID.randomUUID();
        OS.stubFor(get(urlEqualTo("/servicos/" + inexistente)).willReturn(aResponse().withStatus(404)));
        enviar(post("/execucoes/{os}/prestacoes", ordem.getOsId()), mecanico, prestacaoJson(inexistente))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.erro").value("SERVICO_INVALIDO"));
    }

    @Test
    void osForaDoArE503EOsRecusandoOTokenE502() throws Exception {
        OrdemExecucao ordem = criarOrdem();
        UUID fora = UUID.randomUUID();
        String corpo = prestacaoJson(fora);
        OS.stubFor(get(urlEqualTo("/servicos/" + fora)).willReturn(aResponse().withStatus(503)));

        enviar(post("/execucoes/{os}/prestacoes", ordem.getOsId()), mecanico, corpo)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.erro").value("OS_INDISPONIVEL"));

        UUID recusa = UUID.randomUUID();
        OS.stubFor(get(urlEqualTo("/servicos/" + recusa)).willReturn(aResponse().withStatus(403)));
        enviar(post("/execucoes/{os}/prestacoes", ordem.getOsId()), mecanico, prestacaoJson(recusa))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.erro").value("INTEGRACAO_OS"));
    }

    @Test
    void corpoInvalidoE400() throws Exception {
        OrdemExecucao ordem = criarOrdem();

        enviar(post("/execucoes/{os}/prestacoes", ordem.getOsId()), mecanico, "{\"precoMaoDeObra\":0}")
                .andExpect(status().isBadRequest());
    }

    /** Espera o envelope do tipo para a ordem na fila assinante do topico e o valida contra o schema do Execucao. */
    private JsonNode aguardarMensagem(UUID osId, String tipo) throws Exception {
        long limite = System.nanoTime() + Duration.ofSeconds(15).toNanos();
        while (System.nanoTime() < limite) {
            for (Message mensagem : mensagensPublicadas(osId, Duration.ofSeconds(1))) {
                JsonNode envelope = objectMapper.readTree(mensagem.body());
                if (tipo.equals(envelope.path("type").asText())) {
                    return ContratosTeste.validar(mensagem.body());
                }
            }
        }
        throw new AssertionError("Mensagem " + tipo + " nao chegou para a ordem " + osId);
    }

    private java.util.List<Message> mensagensPublicadas(UUID osId, Duration espera) {
        java.util.List<Message> encontradas = new java.util.ArrayList<>();
        long limite = System.nanoTime() + espera.toNanos();
        do {
            receber(MENSAGERIA.filaAssinante()).stream()
                    .filter(m -> m.body().contains(osId.toString())).forEach(encontradas::add);
        } while (System.nanoTime() < limite && encontradas.isEmpty());
        return encontradas;
    }
}
