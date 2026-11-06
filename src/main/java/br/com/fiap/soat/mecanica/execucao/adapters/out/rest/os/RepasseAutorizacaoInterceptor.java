package br.com.fiap.soat.mecanica.execucao.adapters.out.rest.os;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.IOException;

/**
 * Copia o header Authorization da requisição em andamento para a chamada ao OS (F4-b). É aplicado só
 * ao RestClient do {@link OsCatalogoClient}: um interceptor global vazaria o token do usuário para qualquer destino.
 */
public class RepasseAutorizacaoInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes atributos) {
            String autorizacao = atributos.getRequest().getHeader(HttpHeaders.AUTHORIZATION);
            if (autorizacao != null && !request.getHeaders().containsKey(HttpHeaders.AUTHORIZATION)) {
                request.getHeaders().set(HttpHeaders.AUTHORIZATION, autorizacao);
            }
        }
        return execution.execute(request, body);
    }
}
