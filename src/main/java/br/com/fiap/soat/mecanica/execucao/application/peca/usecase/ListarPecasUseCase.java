package br.com.fiap.soat.mecanica.execucao.application.peca.usecase;

import br.com.fiap.soat.mecanica.execucao.domain.peca.Peca;
import br.com.fiap.soat.mecanica.execucao.domain.peca.PecaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ListarPecasUseCase {

    private final PecaRepository repository;

    public List<Peca> executar() {
        return repository.listar();
    }
}
