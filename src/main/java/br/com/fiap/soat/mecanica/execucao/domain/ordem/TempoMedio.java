package br.com.fiap.soat.mecanica.execucao.domain.ordem;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/** Tempo acumulado de um servico (item de {@code execucao-tempos}, F5-d): soma de minutos e numero de amostras. */
public record TempoMedio(UUID servicoId, String nomeServico, long totalMinutos, long amostras) {

    /** {@code totalMinutos / amostras}, arredondado a 1 casa; 0 sem amostras. */
    public BigDecimal minutosMedio() {
        if (amostras <= 0) {
            return BigDecimal.ZERO.setScale(1);
        }
        return BigDecimal.valueOf(totalMinutos).divide(BigDecimal.valueOf(amostras), 1, RoundingMode.HALF_UP);
    }
}
