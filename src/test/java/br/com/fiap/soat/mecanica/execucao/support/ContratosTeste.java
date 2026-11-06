package br.com.fiap.soat.mecanica.execucao.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.Error;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SchemaRegistryConfig;
import com.networknt.schema.SpecificationVersion;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Valida os envelopes produzidos contra o schema do proprio Execucao (regra de congelamento da F1). */
public final class ContratosTeste {

    private static final SchemaRegistry REGISTRY = SchemaRegistry.withDefaultDialect(
            SpecificationVersion.DRAFT_2020_12,
            builder -> builder.schemaRegistryConfig(SchemaRegistryConfig.builder().formatAssertionsEnabled(true).build()));
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ContratosTeste() {
    }

    /** Valida o envelope e o payload dele contra {@code contratos/<type>.v<version>.schema.json}; devolve o envelope. */
    public static JsonNode validar(String envelopeJson) {
        List<Error> erros = schema("contratos/envelope.schema.json").validate(envelopeJson, InputFormat.JSON);
        assertThat(erros).as("envelope").isEmpty();
        JsonNode envelope = leitura(envelopeJson);
        String arquivo = "contratos/" + envelope.path("type").asText() + ".v" + envelope.path("version").asInt()
                + ".schema.json";
        List<Error> errosPayload = schema(arquivo).validate(envelope.path("payload").toString(), InputFormat.JSON);
        assertThat(errosPayload).as("payload contra " + arquivo).isEmpty();
        return envelope;
    }

    private static Schema schema(String caminho) {
        try {
            String conteudo = new ClassPathResource(caminho).getContentAsString(StandardCharsets.UTF_8);
            return REGISTRY.getSchema(conteudo, InputFormat.JSON);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static JsonNode leitura(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
