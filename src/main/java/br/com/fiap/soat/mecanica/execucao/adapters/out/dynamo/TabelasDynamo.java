package br.com.fiap.soat.mecanica.execucao.adapters.out.dynamo;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Nomes das 6 tabelas (RFC-F4-02); fixos no application.properties, iguais em todos os ambientes. */
@ConfigurationProperties("app.dynamo.tabelas")
public record TabelasDynamo(String ordens, String pecas, String reservas, String outbox,
                            String idempotencia, String tempos) {

    public static final String INDICE_FILA = "fila";
    public static final String INDICE_PENDENTES = "pendentes";

    public TabelasDynamo {
        ordens = padrao(ordens, "execucao-ordens");
        pecas = padrao(pecas, "execucao-pecas");
        reservas = padrao(reservas, "execucao-reservas");
        outbox = padrao(outbox, "execucao-outbox");
        idempotencia = padrao(idempotencia, "execucao-idempotencia");
        tempos = padrao(tempos, "execucao-tempos");
    }

    private static String padrao(String valor, String padrao) {
        return valor == null || valor.isBlank() ? padrao : valor;
    }
}
