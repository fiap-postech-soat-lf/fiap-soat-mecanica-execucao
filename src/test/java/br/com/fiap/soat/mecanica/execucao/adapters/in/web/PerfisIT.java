package br.com.fiap.soat.mecanica.execucao.adapters.in.web;

import br.com.fiap.soat.mecanica.execucao.support.IntegracaoTest;
import br.com.fiap.soat.mecanica.execucao.support.JwtTestTokens;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Matriz de perfis (tabela 3.2 da F5): para cada rota, sem token e 401, perfil errado e 403 e perfil certo
 * passa da seguranca (2xx, 404 ou 422). Os ids sao aleatorios, entao as rotas de ordem respondem 404.
 */
class PerfisIT extends IntegracaoTest {

    private static final String OS = UUID.randomUUID().toString();
    private static final String ID = UUID.randomUUID().toString();
    private static final String PECA = "{\"nome\":\"Filtro\",\"marca\":\"Bosch\",\"valorUnitario\":10.00,\"qtdEstoque\":5}";
    private static final String PRESTACAO = "{\"servicoId\":\"" + ID + "\",\"precoMaoDeObra\":80.00}";
    private static final String ALOCACAO = "{\"pecaId\":\"" + ID + "\",\"quantidade\":1}";

    @Autowired
    private MockMvc mvc;

    record Rota(HttpMethod metodo, String caminho, String corpo, List<String> permitidos) {
        @Override
        public String toString() {
            return metodo + " " + caminho + " " + permitidos;
        }
    }

    static Stream<Arguments> rotas() {
        List<String> m = List.of("MECANICO");
        List<String> a = List.of("ALMOXARIFE");
        List<String> ma = List.of("MECANICO", "ALMOXARIFE");
        return Stream.of(
                new Rota(HttpMethod.GET, "/pecas", null, a),
                new Rota(HttpMethod.POST, "/pecas", PECA, a),
                new Rota(HttpMethod.GET, "/pecas/" + ID, null, a),
                new Rota(HttpMethod.PUT, "/pecas/" + ID, PECA, a),
                new Rota(HttpMethod.GET, "/execucoes/" + OS, null, ma),
                new Rota(HttpMethod.POST, "/execucoes/" + OS + "/prestacoes", PRESTACAO, m),
                new Rota(HttpMethod.PATCH, "/execucoes/" + OS + "/prestacoes/" + ID + "/inativar", null, m),
                new Rota(HttpMethod.POST, "/execucoes/" + OS + "/prestacoes/" + ID + "/pecas", ALOCACAO, a),
                new Rota(HttpMethod.PATCH, "/execucoes/" + OS + "/alocacoes/" + ID + "/inativar", null, a),
                new Rota(HttpMethod.PATCH, "/execucoes/" + OS + "/diagnostico/concluir", null, m),
                new Rota(HttpMethod.PATCH, "/execucoes/" + OS + "/diagnostico/reabrir", null, m),
                new Rota(HttpMethod.PATCH, "/execucoes/" + OS + "/prestacoes/" + ID + "/finalizar", null, m),
                new Rota(HttpMethod.GET, "/execucoes/fila", null, m),
                new Rota(HttpMethod.GET, "/execucoes/tempo-medio", null, m)
        ).map(Arguments::of);
    }

    private MockHttpServletRequestBuilder requisicao(Rota rota, String token) {
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.request(rota.metodo(), rota.caminho());
        if (rota.corpo() != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(rota.corpo());
        }
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return builder;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rotas")
    @DisplayName("sem token responde 401")
    void semTokenE401(Rota rota) throws Exception {
        mvc.perform(requisicao(rota, null)).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rotas")
    @DisplayName("perfil errado responde 403")
    void perfilErradoE403(Rota rota) throws Exception {
        List<String> errados = Stream.of("MECANICO", "ALMOXARIFE", "ATENDENTE")
                .filter(cargo -> !rota.permitidos().contains(cargo)).toList();
        for (String cargo : errados) {
            mvc.perform(requisicao(rota, JwtTestTokens.funcionario(UUID.randomUUID(), cargo)))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(requisicao(rota, JwtTestTokens.cliente(UUID.randomUUID()))).andExpect(status().isForbidden());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rotas")
    @DisplayName("perfil certo passa da seguranca")
    void perfilCertoPassa(Rota rota) throws Exception {
        for (String cargo : rota.permitidos()) {
            int status = mvc.perform(requisicao(rota, JwtTestTokens.funcionario(UUID.randomUUID(), cargo)))
                    .andReturn().getResponse().getStatus();
            assertThat(status).as(cargo).satisfiesAnyOf(
                    s -> assertThat(s).isBetween(200, 299), s -> assertThat(s).isIn(404, 422));
        }
    }
}
