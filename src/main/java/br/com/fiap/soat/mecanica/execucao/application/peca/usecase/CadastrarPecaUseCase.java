package br.com.fiap.soat.mecanica.execucao.application.peca.usecase;

import br.com.fiap.soat.mecanica.execucao.application.transacao.TransacaoPort;
import br.com.fiap.soat.mecanica.execucao.domain.peca.Peca;
import br.com.fiap.soat.mecanica.execucao.domain.peca.PecaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class CadastrarPecaUseCase {

    private final PecaRepository repository;
    private final TransacaoPort transacao;

    public Peca executar(String nome, String marca, BigDecimal valorUnitario, Integer qtdEstoque) {
        return transacao.executar(() -> {
            Peca peca = new Peca(nome, marca, valorUnitario, qtdEstoque);
            repository.salvar(peca);
            return peca;
        });
    }
}
