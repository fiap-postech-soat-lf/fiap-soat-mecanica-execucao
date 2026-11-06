package br.com.fiap.soat.mecanica.execucao.application.ordem.usecase;

import br.com.fiap.soat.mecanica.execucao.domain.ordem.OrdemRepository;
import br.com.fiap.soat.mecanica.execucao.domain.ordem.PaginaFila;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Fila de execucao: uma consulta no GSI {@code fila}, ja ordenada por prioridade e data (F5-j). */
@Service
@RequiredArgsConstructor
public class ListarFilaUseCase {

    public static final int TAMANHO_PADRAO = 20;
    public static final int TAMANHO_MAXIMO = 100;

    private final OrdemRepository repository;

    /** {@code tamanho} fora de 1..100 e ajustado ao limite mais proximo. */
    public PaginaFila executar(int tamanho, String cursor) {
        int ajustado = Math.min(Math.max(tamanho, 1), TAMANHO_MAXIMO);
        return repository.listarFila(ajustado, cursor);
    }
}
