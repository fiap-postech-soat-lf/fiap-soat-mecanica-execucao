package br.com.fiap.soat.mecanica.execucao.config.seed;

import br.com.fiap.soat.mecanica.execucao.adapters.out.dynamo.Atributos;
import br.com.fiap.soat.mecanica.execucao.adapters.out.dynamo.TabelasDynamo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

import java.util.HashMap;
import java.util.Map;

/**
 * Grava as 3 pecas do V9 do monolito com {@code PutItem} condicional (F5-l): os UUIDs ficam estaveis (ADR-F4-13)
 * e um estoque ja alterado nunca e sobrescrito. Ligado por {@code app.seed.pecas.enabled} (padrao {@code true}).
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.seed.pecas.enabled", havingValue = "true", matchIfMissing = true)
public class SeedPecas implements ApplicationRunner {

    record PecaSeed(String id, String nome, String marca, String valorUnitario, int estoque) {
    }

    static final java.util.List<PecaSeed> PECAS = java.util.List.of(
            new PecaSeed("55555555-5555-5555-5555-555555555555", "Filtro de Oleo", "Bosch", "39.90", 20),
            new PecaSeed("55555555-5555-5555-5555-555555555556", "Pastilha de Freio", "Fras-le", "129.90", 12),
            new PecaSeed("55555555-5555-5555-5555-555555555557", "Oleo 5W30", "Shell", "49.90", 30));

    private final DynamoDbClient dynamo;
    private final TabelasDynamo tabelas;

    public SeedPecas(DynamoDbClient dynamo, TabelasDynamo tabelas) {
        this.dynamo = dynamo;
        this.tabelas = tabelas;
    }

    @Override
    public void run(ApplicationArguments args) {
        int gravadas = 0;
        for (PecaSeed peca : PECAS) {
            if (gravar(peca)) {
                gravadas++;
            }
        }
        log.atInfo().addKeyValue("event", "seed_pecas").addKeyValue("gravadas", gravadas)
                .log("Seed de peças concluído");
    }

    private boolean gravar(PecaSeed peca) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("pecaId", Atributos.s(peca.id()));
        item.put("nome", Atributos.s(peca.nome()));
        item.put("marca", Atributos.s(peca.marca()));
        item.put("valorUnitario", AttributeValue.fromN(peca.valorUnitario()));
        item.put("qtdEstoque", Atributos.n(peca.estoque()));
        item.put("qtdReservada", Atributos.n(0));
        item.put("qtdDisponivel", Atributos.n(peca.estoque()));
        item.put("status", Atributos.s("ATIVO"));
        item.put("versao", Atributos.n(1));
        try {
            dynamo.putItem(r -> r.tableName(tabelas.pecas()).item(item)
                    .conditionExpression("attribute_not_exists(pecaId)"));
            return true;
        } catch (ConditionalCheckFailedException e) {
            return false;
        }
    }
}
