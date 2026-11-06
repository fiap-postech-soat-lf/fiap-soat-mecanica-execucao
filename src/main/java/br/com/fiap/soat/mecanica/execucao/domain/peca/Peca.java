package br.com.fiap.soat.mecanica.execucao.domain.peca;

import br.com.fiap.soat.mecanica.execucao.domain.Principal;
import br.com.fiap.soat.mecanica.execucao.domain.enums.StatusRecursoEnum;
import br.com.fiap.soat.mecanica.execucao.domain.exception.RegraNegocioException;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Peca do catalogo com o estoque materializado (F5-e). Vale sempre
 * {@code qtdEstoque = qtdDisponivel + qtdReservada}: a reserva move unidades de "disponivel" para
 * "reservada", a baixa tira das duas "estoque" e "reservada", e a liberacao devolve.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Peca extends Principal {

    private UUID id;
    private String nome;
    private String marca;
    private BigDecimal valorUnitario;
    private int qtdEstoque;
    private int qtdReservada;
    private int qtdDisponivel;
    private long versao;

    public Peca(String nome, String marca, BigDecimal valorUnitario, Integer qtdEstoque) {
        validar(nome, marca, valorUnitario, qtdEstoque);
        this.id = UUID.randomUUID();
        this.nome = nome;
        this.marca = marca;
        this.valorUnitario = valorUnitario;
        this.qtdEstoque = qtdEstoque;
        this.qtdDisponivel = qtdEstoque;
        this.qtdReservada = 0;
    }

    public static Peca reconstruir(UUID id, StatusRecursoEnum status, String nome, String marca,
                                   BigDecimal valorUnitario, int qtdEstoque, int qtdReservada,
                                   int qtdDisponivel, long versao) {
        Peca peca = new Peca();
        peca.id = id;
        peca.status = status;
        peca.nome = nome;
        peca.marca = marca;
        peca.valorUnitario = valorUnitario;
        peca.qtdEstoque = qtdEstoque;
        peca.qtdReservada = qtdReservada;
        peca.qtdDisponivel = qtdDisponivel;
        peca.versao = versao;
        return peca;
    }

    /** Altera os dados da peca e o estoque (que nunca fica abaixo do reservado). */
    public void alterar(String nome, String marca, BigDecimal valorUnitario, Integer qtdEstoque) {
        validar(nome, marca, valorUnitario, qtdEstoque);
        this.nome = nome;
        this.marca = marca;
        this.valorUnitario = valorUnitario;
        alterarEstoque(qtdEstoque);
    }

    public void alterarEstoque(int novoEstoque) {
        if (novoEstoque < 0) {
            throw new RegraNegocioException("Quantidade em estoque deve ser maior ou igual a zero");
        }
        if (novoEstoque < qtdReservada) {
            throw new RegraNegocioException(String.format(
                    "O estoque da peça %s não pode ficar abaixo do reservado (%d)", nome, qtdReservada));
        }
        this.qtdEstoque = novoEstoque;
        this.qtdDisponivel = novoEstoque - qtdReservada;
    }

    public void reservar(int quantidade) {
        exigirPositiva(quantidade, "reserva");
        if (isInativo()) {
            throw new RegraNegocioException("A peça " + nome + " está inativa");
        }
        if (qtdDisponivel < quantidade) {
            throw new RegraNegocioException(String.format(
                    "Sem estoque para a peça %s, há %d disponível", nome, qtdDisponivel));
        }
        qtdDisponivel -= quantidade;
        qtdReservada += quantidade;
    }

    /** Baixa a reserva do estoque (inicio da execucao). */
    public void baixar(int quantidade) {
        exigirPositiva(quantidade, "baixa");
        exigirReservada(quantidade);
        qtdEstoque -= quantidade;
        qtdReservada -= quantidade;
    }

    /** Devolve a reserva ao estoque disponivel. */
    public void liberar(int quantidade) {
        exigirPositiva(quantidade, "liberação");
        exigirReservada(quantidade);
        qtdDisponivel += quantidade;
        qtdReservada -= quantidade;
    }

    private void exigirReservada(int quantidade) {
        if (qtdReservada < quantidade) {
            throw new RegraNegocioException(String.format(
                    "A peça %s tem apenas %d reservada(s)", nome, qtdReservada));
        }
    }

    private static void exigirPositiva(int quantidade, String operacao) {
        if (quantidade <= 0) {
            throw new RegraNegocioException("Quantidade para " + operacao + " deve ser maior que zero");
        }
    }

    private static void validar(String nome, String marca, BigDecimal valorUnitario, Integer qtdEstoque) {
        if (nome == null || nome.isBlank()) {
            throw new RegraNegocioException("Nome obrigatorio");
        }
        if (marca == null || marca.isBlank()) {
            throw new RegraNegocioException("Marca obrigatoria");
        }
        if (valorUnitario == null || valorUnitario.compareTo(BigDecimal.ZERO) < 0) {
            throw new RegraNegocioException("Valor unitario deve ser maior ou igual a zero");
        }
        if (qtdEstoque == null || qtdEstoque < 0) {
            throw new RegraNegocioException("Quantidade em estoque deve ser maior ou igual a zero");
        }
    }
}
