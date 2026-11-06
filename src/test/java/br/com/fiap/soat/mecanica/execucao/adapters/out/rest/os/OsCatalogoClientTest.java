package br.com.fiap.soat.mecanica.execucao.adapters.out.rest.os;

import br.com.fiap.soat.mecanica.execucao.application.ordem.exception.IntegracaoOsException;
import br.com.fiap.soat.mecanica.execucao.application.ordem.exception.OsIndisponivelException;
import br.com.fiap.soat.mecanica.execucao.application.ordem.exception.ServicoInvalidoException;
import br.com.fiap.soat.mecanica.execucao.application.ordem.port.ServicoCatalogo;
import br.com.fiap.soat.mecanica.execucao.config.RestClientConfig;
import br.com.fiap.soat.mecanica.execucao.config.observability.Mdc;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OsCatalogoClientTest {

    private static final UUID SERVICO = UUID.fromString("77777777-7777-7777-7777-777777777777");
    private static final String URL = "/servicos/" + SERVICO;

    private static WireMockServer os;
    private OsCatalogoClient client;

    @BeforeAll
    static void subir() {
        os = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        os.start();
    }

    @AfterAll
    static void derrubar() {
        os.stop();
    }

    @BeforeEach
    void preparar() {
        os.resetAll();
        client = novoClient(Duration.ofMinutes(5));
    }

    @AfterEach
    void limpar() {
        RequestContextHolder.resetRequestAttributes();
        MDC.clear();
    }

    private static OsCatalogoClient novoClient(Duration ttl) {
        RestClient.Builder builder = RestClient.builder();
        new RestClientConfig().timeoutsECorrelacao().customize(builder);
        OsProperties propriedades = new OsProperties(os.baseUrl(),
                new OsProperties.Retry(3, Duration.ofMillis(10), 2), new OsProperties.Catalogo(ttl));
        return new OsCatalogoClient(new OsCatalogoClientConfig().osRestClient(builder, propriedades), propriedades);
    }

    private static String corpo(String status) {
        return """
                {"id":"%s","status":"%s","nome":"Troca de oleo","descricao":"Troca completa","extra":1}"""
                .formatted(SERVICO, status);
    }

    private static void stubOk(String status) {
        os.stubFor(get(urlEqualTo(URL)).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json").withBody(corpo(status))));
    }

    private static void stubStatus(int status) {
        os.stubFor(get(urlEqualTo(URL)).willReturn(aResponse().withStatus(status)));
    }

    @Test
    void devolveOServicoAtivo() {
        stubOk("ATIVO");

        ServicoCatalogo servico = client.consultar(SERVICO);

        assertThat(servico).isEqualTo(new ServicoCatalogo(SERVICO, "Troca de oleo", true));
    }

    @Test
    void servicoInexistenteEInvalidoSemRetry() {
        stubStatus(404);

        assertThatThrownBy(() -> client.consultar(SERVICO)).isInstanceOf(ServicoInvalidoException.class);
        os.verify(1, getRequestedFor(urlEqualTo(URL)));
    }

    @Test
    void servicoInativoEInvalido() {
        stubOk("INATIVO");

        assertThatThrownBy(() -> client.consultar(SERVICO)).isInstanceOf(ServicoInvalidoException.class)
                .hasMessageContaining("inativo");
    }

    @Test
    void corpoVazioEInvalido() {
        os.stubFor(get(urlEqualTo(URL)).willReturn(aResponse().withStatus(200)));

        assertThatThrownBy(() -> client.consultar(SERVICO)).isInstanceOf(ServicoInvalidoException.class);
    }

    @Test
    void sucessoNaTerceiraTentativaDepoisDeDois503() {
        os.stubFor(get(urlEqualTo(URL)).inScenario("retry").whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(503)).willSetStateTo("segunda"));
        os.stubFor(get(urlEqualTo(URL)).inScenario("retry").whenScenarioStateIs("segunda")
                .willReturn(aResponse().withStatus(503)).willSetStateTo("terceira"));
        os.stubFor(get(urlEqualTo(URL)).inScenario("retry").whenScenarioStateIs("terceira")
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody(corpo("ATIVO"))));

        assertThat(client.consultar(SERVICO).nome()).isEqualTo("Troca de oleo");
        os.verify(3, getRequestedFor(urlEqualTo(URL)));
    }

    @Test
    void tresVezes503EsgotamOsRetries() {
        stubStatus(503);

        assertThatThrownBy(() -> client.consultar(SERVICO)).isInstanceOf(OsIndisponivelException.class);
        os.verify(3, getRequestedFor(urlEqualTo(URL)));
    }

    @Test
    void atrasoDeTresSegundosEstouraOTimeoutEFazRetry() {
        os.stubFor(get(urlEqualTo(URL)).inScenario("lento").whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(200).withFixedDelay(3000)).willSetStateTo("rapido"));
        os.stubFor(get(urlEqualTo(URL)).inScenario("lento").whenScenarioStateIs("rapido")
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody(corpo("ATIVO"))));

        assertThat(client.consultar(SERVICO).nome()).isEqualTo("Troca de oleo");
        os.verify(2, getRequestedFor(urlEqualTo(URL)));
    }

    @Test
    void tokenRecusadoVira502SemRetry() {
        for (int status : new int[]{401, 403}) {
            os.resetAll();
            stubStatus(status);

            assertThatThrownBy(() -> novoClient(Duration.ofMinutes(5)).consultar(SERVICO))
                    .isInstanceOf(IntegracaoOsException.class);
            os.verify(1, getRequestedFor(urlEqualTo(URL)));
        }
    }

    @Test
    void segundaChamadaDentroDoTtlNaoChegaAoOs() {
        stubOk("ATIVO");

        client.consultar(SERVICO);
        client.consultar(SERVICO);

        os.verify(1, getRequestedFor(urlEqualTo(URL)));
    }

    @Test
    void depoisDoTtlConsultaDeNovo() throws Exception {
        stubOk("ATIVO");
        OsCatalogoClient curto = novoClient(Duration.ofMillis(100));

        curto.consultar(SERVICO);
        Thread.sleep(250);
        curto.consultar(SERVICO);

        os.verify(2, getRequestedFor(urlEqualTo(URL)));
    }

    @Test
    void respostasQueNaoSao200NaoVaoParaOCache() {
        stubStatus(404);
        assertThatThrownBy(() -> client.consultar(SERVICO)).isInstanceOf(ServicoInvalidoException.class);
        os.resetAll();
        stubOk("ATIVO");

        assertThat(client.consultar(SERVICO).ativo()).isTrue();
        os.verify(1, getRequestedFor(urlEqualTo(URL)));
    }

    @Test
    void repassaOJwtDaRequisicaoEOCorrelationId() {
        MockHttpServletRequest entrada = new MockHttpServletRequest();
        entrada.addHeader("Authorization", "Bearer token-do-mecanico");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(entrada));
        MDC.put(Mdc.CORRELATION_ID, "corr-prestacao");
        stubOk("ATIVO");

        client.consultar(SERVICO);

        os.verify(getRequestedFor(urlEqualTo(URL))
                .withHeader("Authorization", equalTo("Bearer token-do-mecanico"))
                .withHeader("X-Correlation-Id", equalTo("corr-prestacao")));
    }

    @Test
    void propriedadesComPadroes() {
        OsProperties vazias = new OsProperties(null, null, null);

        assertThat(vazias.retry().maxTentativas()).isEqualTo(3);
        assertThat(vazias.catalogo().cacheTtl()).isEqualTo(Duration.ofMinutes(5));
        OsProperties.Retry invalido = new OsProperties.Retry(0, null, 0);
        assertThat(invalido.esperaInicial()).isEqualTo(Duration.ofMillis(200));
        assertThat(invalido.multiplicador()).isEqualTo(2.0);
        assertThat(new OsProperties.Catalogo(null).cacheTtl()).isEqualTo(Duration.ofMinutes(5));
    }
}
