package br.com.fiap.soat.mecanica.execucao.adapters.out.dynamo.peca;

import br.com.fiap.soat.mecanica.execucao.application.transacao.ConflitoConcorrenciaException;
import br.com.fiap.soat.mecanica.execucao.application.transacao.TransacaoPort;
import br.com.fiap.soat.mecanica.execucao.domain.peca.Peca;
import br.com.fiap.soat.mecanica.execucao.domain.peca.PecaRepository;
import br.com.fiap.soat.mecanica.execucao.support.IntegracaoTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PecaDynamoRepositoryIT extends IntegracaoTest {

    @Autowired
    private PecaRepository repository;
    @Autowired
    private TransacaoPort transacao;

    private void salvar(Peca peca) {
        transacao.executar(() -> repository.salvar(peca));
    }

    @Test
    void gravaELeAPecaComOEstoqueMaterializado() {
        Peca peca = new Peca("Filtro " + UUID.randomUUID(), "Bosch", new BigDecimal("39.90"), 20);
        peca.reservar(5);
        salvar(peca);

        Peca lida = repository.buscarPorId(peca.getId()).orElseThrow();

        assertThat(lida.getNome()).isEqualTo(peca.getNome());
        assertThat(lida.getValorUnitario()).isEqualByComparingTo("39.90");
        assertThat(lida.getQtdEstoque()).isEqualTo(20);
        assertThat(lida.getQtdReservada()).isEqualTo(5);
        assertThat(lida.getQtdDisponivel()).isEqualTo(15);
        assertThat(lida.isAtivo()).isTrue();
        assertThat(lida.getVersao()).isEqualTo(1);
    }

    @Test
    void pecaInexistenteNaoExiste() {
        assertThat(repository.buscarPorId(UUID.randomUUID())).isEmpty();
    }

    @Test
    void listarDevolveTudoOrdenadoPorNome() {
        String sufixo = UUID.randomUUID().toString();
        Peca b = new Peca("bbb " + sufixo, "m", BigDecimal.ONE, 1);
        Peca a = new Peca("AAA " + sufixo, "m", BigDecimal.ONE, 1);
        salvar(b);
        salvar(a);

        List<String> nomes = repository.listar().stream().map(Peca::getNome).filter(n -> n.endsWith(sufixo)).toList();

        assertThat(nomes).containsExactly("AAA " + sufixo, "bbb " + sufixo);
    }

    @Test
    void gravarComVersaoVelhaFalhaComConflito() {
        Peca peca = new Peca("Filtro " + UUID.randomUUID(), "Bosch", BigDecimal.TEN, 20);
        salvar(peca);
        Peca a = repository.buscarPorId(peca.getId()).orElseThrow();
        Peca b = repository.buscarPorId(peca.getId()).orElseThrow();
        a.alterarEstoque(30);
        salvar(a);
        b.alterarEstoque(40);

        assertThatThrownBy(() -> salvar(b)).isInstanceOf(ConflitoConcorrenciaException.class);
        assertThat(repository.buscarPorId(peca.getId()).orElseThrow().getQtdEstoque()).isEqualTo(30);
    }

    @Test
    void criarDuasVezesAMesmaPecaFalhaComConflito() {
        Peca peca = new Peca("Filtro " + UUID.randomUUID(), "Bosch", BigDecimal.TEN, 20);
        salvar(peca);

        assertThatThrownBy(() -> salvar(peca)).isInstanceOf(ConflitoConcorrenciaException.class);
    }
}
