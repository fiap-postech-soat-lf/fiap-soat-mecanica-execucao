package br.com.fiap.soat.mecanica.execucao.domain.peca;

import br.com.fiap.soat.mecanica.execucao.domain.enums.StatusRecursoEnum;
import br.com.fiap.soat.mecanica.execucao.domain.exception.RegraNegocioException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PecaTest {

    private static Peca peca(int estoque) {
        return new Peca("Filtro de Oleo", "Bosch", new BigDecimal("39.90"), estoque);
    }

    private static void invarianteMantido(Peca p) {
        assertThat(p.getQtdEstoque()).isEqualTo(p.getQtdDisponivel() + p.getQtdReservada());
    }

    @Test
    void pecaNovaTemTodoOEstoqueDisponivel() {
        Peca p = peca(20);

        assertThat(p.getId()).isNotNull();
        assertThat(p.getQtdEstoque()).isEqualTo(20);
        assertThat(p.getQtdDisponivel()).isEqualTo(20);
        assertThat(p.getQtdReservada()).isZero();
        assertThat(p.getVersao()).isZero();
        assertThat(p.isAtivo()).isTrue();
    }

    @Test
    void dadosInvalidosNaCriacao() {
        BigDecimal valor = BigDecimal.TEN;
        assertThatThrownBy(() -> new Peca(" ", "m", valor, 1)).isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> new Peca("n", null, valor, 1)).isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> new Peca("n", "m", new BigDecimal("-1"), 1)).isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> new Peca("n", "m", null, 1)).isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> new Peca("n", "m", valor, -1)).isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> new Peca("n", "m", valor, null)).isInstanceOf(RegraNegocioException.class);
    }

    @Test
    void reservarMoveDeDisponivelParaReservada() {
        Peca p = peca(10);

        p.reservar(4);

        assertThat(p.getQtdDisponivel()).isEqualTo(6);
        assertThat(p.getQtdReservada()).isEqualTo(4);
        assertThat(p.getQtdEstoque()).isEqualTo(10);
        invarianteMantido(p);
    }

    @Test
    void reservarAlemDoDisponivelEInvalido() {
        Peca p = peca(3);

        assertThatThrownBy(() -> p.reservar(4)).isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("Sem estoque");
        assertThat(p.getQtdDisponivel()).isEqualTo(3);
    }

    @Test
    void reservarQuantidadeNaoPositivaOuPecaInativaEInvalido() {
        Peca p = peca(3);
        assertThatThrownBy(() -> p.reservar(0)).isInstanceOf(RegraNegocioException.class);
        p.inativar();
        assertThatThrownBy(() -> p.reservar(1)).isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("inativa");
    }

    @Test
    void baixarTiraDoEstoqueEDaReserva() {
        Peca p = peca(10);
        p.reservar(4);

        p.baixar(3);

        assertThat(p.getQtdEstoque()).isEqualTo(7);
        assertThat(p.getQtdReservada()).isEqualTo(1);
        assertThat(p.getQtdDisponivel()).isEqualTo(6);
        invarianteMantido(p);
    }

    @Test
    void baixarMaisQueOReservadoOuZeroEInvalido() {
        Peca p = peca(10);
        p.reservar(2);

        assertThatThrownBy(() -> p.baixar(3)).isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> p.baixar(0)).isInstanceOf(RegraNegocioException.class);
    }

    @Test
    void liberarDevolveAoDisponivel() {
        Peca p = peca(10);
        p.reservar(4);

        p.liberar(4);

        assertThat(p.getQtdDisponivel()).isEqualTo(10);
        assertThat(p.getQtdReservada()).isZero();
        invarianteMantido(p);
    }

    @Test
    void liberarMaisQueOReservadoOuNegativoEInvalido() {
        Peca p = peca(10);
        p.reservar(1);

        assertThatThrownBy(() -> p.liberar(2)).isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> p.liberar(-1)).isInstanceOf(RegraNegocioException.class);
    }

    @Test
    void alterarEstoqueRespeitaAReserva() {
        Peca p = peca(10);
        p.reservar(4);

        p.alterarEstoque(8);

        assertThat(p.getQtdEstoque()).isEqualTo(8);
        assertThat(p.getQtdReservada()).isEqualTo(4);
        assertThat(p.getQtdDisponivel()).isEqualTo(4);
        invarianteMantido(p);
    }

    @Test
    void alterarEstoqueAbaixoDoReservadoOuNegativoEInvalido() {
        Peca p = peca(10);
        p.reservar(4);

        assertThatThrownBy(() -> p.alterarEstoque(3)).isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("abaixo do reservado");
        assertThatThrownBy(() -> p.alterarEstoque(-1)).isInstanceOf(RegraNegocioException.class);
        assertThat(p.getQtdEstoque()).isEqualTo(10);
    }

    @Test
    void alterarAtualizaDadosEEstoque() {
        Peca p = peca(10);

        p.alterar("Filtro de Ar", "Mann", new BigDecimal("55.00"), 15);

        assertThat(p.getNome()).isEqualTo("Filtro de Ar");
        assertThat(p.getMarca()).isEqualTo("Mann");
        assertThat(p.getValorUnitario()).isEqualByComparingTo("55.00");
        assertThat(p.getQtdDisponivel()).isEqualTo(15);
        assertThatThrownBy(() -> p.alterar("", "Mann", BigDecimal.ONE, 1)).isInstanceOf(RegraNegocioException.class);
    }

    @Test
    void reconstruirPreservaOEstado() {
        Peca original = peca(10);
        original.reservar(2);

        Peca copia = Peca.reconstruir(original.getId(), StatusRecursoEnum.INATIVO, "n", "m", BigDecimal.ONE,
                10, 2, 8, 5);

        assertThat(copia.getId()).isEqualTo(original.getId());
        assertThat(copia.isInativo()).isTrue();
        assertThat(copia.getVersao()).isEqualTo(5);
        invarianteMantido(copia);
    }
}
