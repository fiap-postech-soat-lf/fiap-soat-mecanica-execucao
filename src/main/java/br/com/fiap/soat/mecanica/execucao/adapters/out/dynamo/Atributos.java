package br.com.fiap.soat.mecanica.execucao.adapters.out.dynamo;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Atalhos para {@link AttributeValue} e o formato de data das chaves (RFC-F4-02): texto UTC com largura
 * fixa, porque {@code Instant.toString()} omite milissegundos zerados e quebra a ordem por texto.
 */
public final class Atributos {

    private static final DateTimeFormatter DATA =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

    private Atributos() {
    }

    public static AttributeValue s(String valor) {
        return AttributeValue.fromS(valor);
    }

    public static AttributeValue n(long valor) {
        return AttributeValue.fromN(Long.toString(valor));
    }

    public static String data(Instant instante) {
        return DATA.format(instante);
    }
}
