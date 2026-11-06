package br.com.fiap.soat.mecanica.execucao.adapters.in.web.peca;

import br.com.fiap.soat.mecanica.execucao.application.transacao.TransacaoPort;
import br.com.fiap.soat.mecanica.execucao.domain.peca.Peca;
import br.com.fiap.soat.mecanica.execucao.domain.peca.PecaRepository;
import br.com.fiap.soat.mecanica.execucao.support.IntegracaoTest;
import br.com.fiap.soat.mecanica.execucao.support.JwtTestTokens;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PecaControllerIT extends IntegracaoTest {

    private final String auth = "Bearer " + JwtTestTokens.funcionario(UUID.randomUUID(), "ALMOXARIFE");

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private PecaRepository pecas;
    @Autowired
    private TransacaoPort transacao;

    private String corpo(String nome, int estoque) {
        return "{\"nome\":\"%s\",\"marca\":\"Bosch\",\"valorUnitario\":39.90,\"qtdEstoque\":%d}".formatted(nome, estoque);
    }

    private String cadastrar(String nome, int estoque) throws Exception {
        String resposta = mvc.perform(post("/pecas").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content(corpo(nome, estoque)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.qtdEstoque").value(estoque))
                .andExpect(jsonPath("$.qtdDisponivel").value(estoque))
                .andExpect(jsonPath("$.qtdReservada").value(0))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(resposta).get("id").asText();
    }

    @Test
    void cadastraBuscaEListaAPeca() throws Exception {
        String nome = "Filtro " + UUID.randomUUID();
        String id = cadastrar(nome, 7);

        mvc.perform(get("/pecas/{id}", id).header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value(nome))
                .andExpect(jsonPath("$.status").value("ATIVO"));
        mvc.perform(get("/pecas").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id").value(hasItem(id)));
    }

    @Test
    void buscarPecaInexistenteE404() throws Exception {
        mvc.perform(get("/pecas/{id}", UUID.randomUUID()).header("Authorization", auth))
                .andExpect(status().isNotFound());
    }

    @Test
    void alterarAtualizaDadosEEstoque() throws Exception {
        String id = cadastrar("Filtro " + UUID.randomUUID(), 7);

        mvc.perform(put("/pecas/{id}", id).header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content(corpo("Filtro de Ar", 12)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Filtro de Ar"))
                .andExpect(jsonPath("$.qtdEstoque").value(12))
                .andExpect(jsonPath("$.qtdDisponivel").value(12));
    }

    @Test
    void alterarEstoqueAbaixoDoReservadoE422() throws Exception {
        Peca peca = new Peca("Filtro " + UUID.randomUUID(), "Bosch", new BigDecimal("39.90"), 10);
        peca.reservar(4);
        transacao.executar(() -> pecas.salvar(peca));

        mvc.perform(put("/pecas/{id}", peca.getId()).header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content(corpo("Filtro", 3)))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(put("/pecas/{id}", peca.getId()).header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content(corpo("Filtro", 6)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.qtdReservada").value(4))
                .andExpect(jsonPath("$.qtdDisponivel").value(2));
    }

    @Test
    void alterarPecaInexistenteE404EDadosInvalidosE400() throws Exception {
        mvc.perform(put("/pecas/{id}", UUID.randomUUID()).header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content(corpo("x", 1)))
                .andExpect(status().isNotFound());
        mvc.perform(post("/pecas").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"\",\"marca\":\"B\",\"valorUnitario\":-1,\"qtdEstoque\":-1}"))
                .andExpect(status().isBadRequest());
    }
}
