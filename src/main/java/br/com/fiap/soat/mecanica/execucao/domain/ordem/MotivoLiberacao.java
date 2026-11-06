package br.com.fiap.soat.mecanica.execucao.domain.ordem;

/** Motivos do comando {@code LiberarReservaPecas} (contrato do OS). */
public enum MotivoLiberacao {

    REVISAO(false),
    ORCAMENTO_FALHOU(false),
    ORCAMENTO_RECUSADO(true),
    ORCAMENTO_EXPIRADO(true),
    DESISTENCIA(true);

    private final boolean cancelaOrdem;

    MotivoLiberacao(boolean cancelaOrdem) {
        this.cancelaOrdem = cancelaOrdem;
    }

    /** {@code true} quando a Saga do OS termina cancelada e a ordem sai da fila; senao o diagnostico e reaberto. */
    public boolean cancelaOrdem() {
        return cancelaOrdem;
    }
}
