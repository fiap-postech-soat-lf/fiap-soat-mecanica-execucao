package br.com.fiap.soat.mecanica.execucao.application.ordem.usecase;

import br.com.fiap.soat.mecanica.execucao.domain.exception.RecursoNaoEncontradoException;
import br.com.fiap.soat.mecanica.execucao.domain.ordem.OrdemExecucao;
import br.com.fiap.soat.mecanica.execucao.domain.ordem.OrdemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Leitura da ordem compartilhada pelos casos de uso (404 se o {@code AbrirDiagnostico} ainda nao chegou). */
@Component
@RequiredArgsConstructor
public class OrdemSupport {

    private final OrdemRepository repository;

    public OrdemExecucao buscar(UUID osId) {
        return repository.buscarPorId(osId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Ordem de execução não encontrada"));
    }
}
