package br.com.fiap.soat.mecanica.execucao.application.ordem.usecase;

import br.com.fiap.soat.mecanica.execucao.domain.ordem.TempoMedio;
import br.com.fiap.soat.mecanica.execucao.domain.ordem.TempoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/** Tempo medio por servico, lido de {@code execucao-tempos} (F5-d), sem varrer as ordens. */
@Service
@RequiredArgsConstructor
public class ConsultarTempoMedioUseCase {

    private final TempoRepository tempos;

    /** Com {@code servicoId}, so o servico (lista vazia se ainda nao ha amostras); sem ele, todos. */
    public List<TempoMedio> executar(UUID servicoId) {
        if (servicoId == null) {
            return tempos.listar();
        }
        return tempos.buscar(servicoId).map(List::of).orElseGet(List::of);
    }
}
