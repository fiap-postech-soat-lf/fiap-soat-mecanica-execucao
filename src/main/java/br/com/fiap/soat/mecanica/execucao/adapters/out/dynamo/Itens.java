package br.com.fiap.soat.mecanica.execucao.adapters.out.dynamo;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Leitura e escrita dos atributos de um item (mapeamento manual, sem reflexao). Ausente e {@code null}. */
public final class Itens {

    private Itens() {
    }

    public static void texto(Map<String, AttributeValue> item, String chave, String valor) {
        if (valor != null) {
            item.put(chave, AttributeValue.fromS(valor));
        }
    }

    public static void uuid(Map<String, AttributeValue> item, String chave, UUID valor) {
        if (valor != null) {
            item.put(chave, AttributeValue.fromS(valor.toString()));
        }
    }

    public static void instante(Map<String, AttributeValue> item, String chave, Instant valor) {
        if (valor != null) {
            item.put(chave, AttributeValue.fromS(Atributos.data(valor)));
        }
    }

    public static void numero(Map<String, AttributeValue> item, String chave, long valor) {
        item.put(chave, Atributos.n(valor));
    }

    public static void decimal(Map<String, AttributeValue> item, String chave, BigDecimal valor) {
        if (valor != null) {
            item.put(chave, AttributeValue.fromN(valor.toPlainString()));
        }
    }

    public static String texto(Map<String, AttributeValue> item, String chave) {
        AttributeValue valor = item.get(chave);
        return valor == null || valor.s() == null ? null : valor.s();
    }

    public static UUID uuid(Map<String, AttributeValue> item, String chave) {
        String texto = texto(item, chave);
        return texto == null ? null : UUID.fromString(texto);
    }

    public static Instant instante(Map<String, AttributeValue> item, String chave) {
        String texto = texto(item, chave);
        return texto == null ? null : Instant.parse(texto);
    }

    public static long numero(Map<String, AttributeValue> item, String chave) {
        AttributeValue valor = item.get(chave);
        return valor == null || valor.n() == null ? 0 : Long.parseLong(valor.n());
    }

    public static BigDecimal decimal(Map<String, AttributeValue> item, String chave) {
        AttributeValue valor = item.get(chave);
        return valor == null || valor.n() == null ? null : new BigDecimal(valor.n());
    }
}
