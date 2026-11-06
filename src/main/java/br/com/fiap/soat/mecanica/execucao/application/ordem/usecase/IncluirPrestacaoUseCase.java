package br.com.fiap.soat.mecanica.execucao.application.ordem.usecase;

import br.com.fiap.soat.mecanica.execucao.application.ordem.port.CatalogoServicosPort;
import br.com.fiap.soat.mecanica.execucao.application.ordem.port.ServicoCatalogo;
import br.com.fiap.soat.mecanica.execucao.application.transacao.TransacaoPort;
import br.com.fiap.soat.mecanica.execucao.domain.ordem.OrdemExecucao;
import br.com.fiap.soat.mecanica.execucao.domain.ordem.OrdemRepository;
import br.com.fiap.soat.mecanica.execucao.domain.ordem.Prestacao;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

/** Valida o servico no catalogo do OS (F5-o) e inclui a prestacao; o preco da mao de obra vem do mecanico. */
@Service
@RequiredArgsConstructor
public class IncluirPrestacaoUseCase {

    private final OrdemSupport ordens;
    private final OrdemRepository repository;
    private final CatalogoServicosPort catalogo;
    private final TransacaoPort transacao;

    public Prestacao executar(UUID osId, UUID servicoId, BigDecimal precoMaoDeObra) {
        return transacao.executar(() -> {
            OrdemExecucao ordem = ordens.buscar(osId);
            ServicoCatalogo servico = catalogo.consultar(servicoId);
            Prestacao prestacao = ordem.incluirPrestacao(servico.id(), servico.nome(), precoMaoDeObra);
            repository.salvar(ordem);
            return prestacao;
        });
    }
}
