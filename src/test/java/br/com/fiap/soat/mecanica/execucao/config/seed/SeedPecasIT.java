package br.com.fiap.soat.mecanica.execucao.config.seed;

import br.com.fiap.soat.mecanica.execucao.adapters.out.dynamo.TabelasDynamo;
import br.com.fiap.soat.mecanica.execucao.support.IntegracaoTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** O runner e ligado a mao (o perfil de teste o desliga, para o contexto compartilhado nao depender dele). */
class SeedPecasIT extends IntegracaoTest {

    private static final String FILTRO = "55555555-5555-5555-5555-555555555555";

    @Autowired
    private TabelasDynamo tabelas;

    @Test
    void gravaAsTresPecasDoV9ESemSobrescreverEstoqueJaAlterado() {
        SeedPecas seed = new SeedPecas(DYNAMO, tabelas);
        DYNAMO.deleteItem(r -> r.tableName(tabelas.pecas()).key(Map.of("pecaId", AttributeValue.fromS(FILTRO))));

        seed.run(null);

        var filtro = item(tabelas.pecas(), "pecaId", FILTRO);
        assertThat(filtro.get("nome").s()).isEqualTo("Filtro de Oleo");
        assertThat(new java.math.BigDecimal(filtro.get("valorUnitario").n())).isEqualByComparingTo("39.90");
        assertThat(filtro.get("qtdEstoque").n()).isEqualTo("20");
        assertThat(filtro.get("qtdDisponivel").n()).isEqualTo("20");
        assertThat(filtro.get("qtdReservada").n()).isEqualTo("0");
        assertThat(item(tabelas.pecas(), "pecaId", "55555555-5555-5555-5555-555555555556").get("nome").s())
                .isEqualTo("Pastilha de Freio");
        assertThat(item(tabelas.pecas(), "pecaId", "55555555-5555-5555-5555-555555555557").get("nome").s())
                .isEqualTo("Oleo 5W30");

        DYNAMO.updateItem(r -> r.tableName(tabelas.pecas()).key(Map.of("pecaId", AttributeValue.fromS(FILTRO)))
                .updateExpression("SET qtdEstoque = :e, qtdDisponivel = :e")
                .expressionAttributeValues(Map.of(":e", AttributeValue.fromN("3"))));
        seed.run(null);
        seed.run(null);

        var depois = item(tabelas.pecas(), "pecaId", FILTRO);
        assertThat(depois.get("qtdEstoque").n()).isEqualTo("3");
        assertThat(depois.get("qtdDisponivel").n()).isEqualTo("3");
    }
}
