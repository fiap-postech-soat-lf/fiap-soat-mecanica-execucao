# As 6 tabelas da RFC-F4-02 (so o Execucao as acessa). On-demand, sem protecao contra delecao e sem PITR:
# o ambiente e descartavel e destrutivel sem sobra (regras 3.3 do plano da F2).

# Ordem de execucao como documento unico (prestacoes e alocacoes aninhadas).
# GSI esparso "fila": so as ordens ativas tem os atributos fila e filaOrdem (F5-j).
resource "aws_dynamodb_table" "ordens" {
  name                        = "execucao-ordens"
  billing_mode                = "PAY_PER_REQUEST"
  hash_key                    = "osId"
  deletion_protection_enabled = false

  attribute {
    name = "osId"
    type = "S"
  }

  attribute {
    name = "fila"
    type = "S"
  }

  attribute {
    name = "filaOrdem"
    type = "S"
  }

  global_secondary_index {
    name            = "fila"
    hash_key        = "fila"
    range_key       = "filaOrdem"
    projection_type = "ALL"
  }
}

# Estoque materializado: qtdEstoque = qtdDisponivel + qtdReservada (F5-e).
resource "aws_dynamodb_table" "pecas" {
  name                        = "execucao-pecas"
  billing_mode                = "PAY_PER_REQUEST"
  hash_key                    = "pecaId"
  deletion_protection_enabled = false

  attribute {
    name = "pecaId"
    type = "S"
  }
}

resource "aws_dynamodb_table" "reservas" {
  name                        = "execucao-reservas"
  billing_mode                = "PAY_PER_REQUEST"
  hash_key                    = "osId"
  range_key                   = "pecaId"
  deletion_protection_enabled = false

  attribute {
    name = "osId"
    type = "S"
  }

  attribute {
    name = "pecaId"
    type = "S"
  }
}

# GSI esparso "pendentes": o atributo pendente so existe enquanto a mensagem nao foi publicada (F5-i).
resource "aws_dynamodb_table" "outbox" {
  name                        = "execucao-outbox"
  billing_mode                = "PAY_PER_REQUEST"
  hash_key                    = "id"
  deletion_protection_enabled = false

  attribute {
    name = "id"
    type = "S"
  }

  attribute {
    name = "pendente"
    type = "S"
  }

  attribute {
    name = "criadoEm"
    type = "S"
  }

  global_secondary_index {
    name            = "pendentes"
    hash_key        = "pendente"
    range_key       = "criadoEm"
    projection_type = "ALL"
  }

  ttl {
    attribute_name = "expiraEm"
    enabled        = true
  }
}

resource "aws_dynamodb_table" "idempotencia" {
  name                        = "execucao-idempotencia"
  billing_mode                = "PAY_PER_REQUEST"
  hash_key                    = "messageId"
  deletion_protection_enabled = false

  attribute {
    name = "messageId"
    type = "S"
  }

  ttl {
    attribute_name = "expiraEm"
    enabled        = true
  }
}

# Tempo medio por servico (F5-d): totalMinutos e amostras atualizados com ADD.
resource "aws_dynamodb_table" "tempos" {
  name                        = "execucao-tempos"
  billing_mode                = "PAY_PER_REQUEST"
  hash_key                    = "servicoId"
  deletion_protection_enabled = false

  attribute {
    name = "servicoId"
    type = "S"
  }
}
