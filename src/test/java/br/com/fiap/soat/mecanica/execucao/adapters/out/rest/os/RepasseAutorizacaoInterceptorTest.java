package br.com.fiap.soat.mecanica.execucao.adapters.out.rest.os;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.support.HttpRequestWrapper;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RepasseAutorizacaoInterceptorTest {

    private final RepasseAutorizacaoInterceptor interceptor = new RepasseAutorizacaoInterceptor();

    @AfterEach
    void limpar() {
        RequestContextHolder.resetRequestAttributes();
    }

    private HttpHeaders executar() throws Exception {
        MockClientHttpRequest saida = new MockClientHttpRequest(HttpMethod.GET, URI.create("http://os/servicos/1"));
        interceptor.intercept(saida, new byte[0], mock(ClientHttpRequestExecution.class));
        return saida.getHeaders();
    }

    @Test
    void copiaOHeaderAuthorizationDaRequisicaoEmAndamento() throws Exception {
        MockHttpServletRequest entrada = new MockHttpServletRequest();
        entrada.addHeader(HttpHeaders.AUTHORIZATION, "Bearer abc");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(entrada));

        assertThat(executar().getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer abc");
    }

    @Test
    void semRequisicaoEmAndamentoNaoAdicionaNada() throws Exception {
        assertThat(executar().containsKey(HttpHeaders.AUTHORIZATION)).isFalse();
    }

    @Test
    void semHeaderNaRequisicaoNaoAdicionaNada() throws Exception {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));

        assertThat(executar().containsKey(HttpHeaders.AUTHORIZATION)).isFalse();
    }

    @Test
    void naoSobrescreveUmHeaderJaDefinido() throws Exception {
        MockHttpServletRequest entrada = new MockHttpServletRequest();
        entrada.addHeader(HttpHeaders.AUTHORIZATION, "Bearer da-requisicao");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(entrada));
        MockClientHttpRequest saida = new MockClientHttpRequest(HttpMethod.GET, URI.create("http://os/x"));
        saida.getHeaders().set(HttpHeaders.AUTHORIZATION, "Bearer explicito");

        interceptor.intercept(new HttpRequestWrapper(saida), new byte[0], mock(ClientHttpRequestExecution.class));

        assertThat(saida.getHeaders().getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer explicito");
    }
}
