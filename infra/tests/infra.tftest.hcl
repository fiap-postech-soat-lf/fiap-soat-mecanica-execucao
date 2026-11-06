# Testes do infra/ com o provider aws mockado: nao precisa de credencial AWS nem do EKS.

mock_provider "aws" {
  # O mock gera ARN aleatorio; o ARN do topico precisa ser valido para o SSM e as validacoes.
  mock_resource "aws_sns_topic" {
    defaults = {
      arn = "arn:aws:sns:us-east-1:123456789012:mecanica-execucao-eventos"
    }
  }

  override_data {
    target = data.aws_caller_identity.atual
    values = {
      account_id = "123456789012"
    }
  }

  override_data {
    target = data.aws_ssm_parameter.jwt_public_key
    values = {
      insecure_value = "-----BEGIN PUBLIC KEY-----\nMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8A\n-----END PUBLIC KEY-----\n"
    }
  }
}

# O servico-cd.yml transforma as chaves em ConfigMap e Secret com --from-env-file.
run "contrato_infra_cd" {
  command = apply

  assert {
    condition = toset(keys(output.app_config)) == toset([
      "AWS_REGION", "JWT_PUBLIC_KEY", "APP_OS_BASE_URL",
      "APP_MESSAGING_CONSUMIDOR_FILAS", "APP_MESSAGING_DLQS", "MESSAGING_DESTINO_EXECUCAO_EVENTOS",
    ])
    error_message = "app_config deve ter as 6 chaves da secao 9 da F5."
  }

  assert {
    condition     = !anytrue([for k in keys(output.app_config) : strcontains(upper(k), "PASSWORD") || strcontains(upper(k), "SECRET") || strcontains(upper(k), "PRIVATE")])
    error_message = "Nenhuma senha ou chave privada no app_config."
  }

  assert {
    condition     = length(output.app_secrets) == 0
    error_message = "app_secrets fica vazio: o servico usa a LabRole e nao tem segredo."
  }

  assert {
    condition     = output.app_config.APP_OS_BASE_URL == "http://mecanica-os.mecanica.svc.cluster.local:8080" && output.app_config.MESSAGING_DESTINO_EXECUCAO_EVENTOS == aws_sns_topic.eventos.arn
    error_message = "O OS e chamado pelo DNS do cluster e o destino de eventos e o proprio topico."
  }

  assert {
    condition     = output.ecr_repository_url == aws_ecr_repository.this.repository_url && aws_ecr_repository.this.name == "mecanica-execucao"
    error_message = "ecr_repository_url deve ser o repositorio mecanica-execucao."
  }

  assert {
    condition     = !strcontains(output.app_config.JWT_PUBLIC_KEY, "\n") && startswith(output.app_config.JWT_PUBLIC_KEY, "-----BEGIN PUBLIC KEY-----\\n")
    error_message = "JWT_PUBLIC_KEY numa linha, com \\n escapado."
  }
}

run "fila_com_dlq" {
  command = apply

  assert {
    condition = (
      aws_sqs_queue.comandos.name == "mecanica-execucao-comandos" && aws_sqs_queue.comandos_dlq.name == "mecanica-execucao-comandos-dlq"
      && aws_sqs_queue.os_eventos.name == "mecanica-execucao-os-eventos" && aws_sqs_queue.os_eventos_dlq.name == "mecanica-execucao-os-eventos-dlq"
    )
    error_message = "Nomes das filas conforme a tabela 3.4 da F4."
  }

  assert {
    condition = alltrue([
      for par in [[aws_sqs_queue.comandos, aws_sqs_queue.comandos_dlq], [aws_sqs_queue.os_eventos, aws_sqs_queue.os_eventos_dlq]] :
      jsondecode(par[0].redrive_policy).maxReceiveCount == 5 && jsondecode(par[0].redrive_policy).deadLetterTargetArn == par[1].arn
    ])
    error_message = "Redrive para a DLQ com maxReceiveCount = 5 nas duas filas."
  }

  assert {
    condition = (
      output.app_config.APP_MESSAGING_CONSUMIDOR_FILAS == "${aws_sqs_queue.comandos.url},${aws_sqs_queue.os_eventos.url}"
      && output.app_config.APP_MESSAGING_DLQS == "${aws_sqs_queue.comandos_dlq.url},${aws_sqs_queue.os_eventos_dlq.url}"
    )
    error_message = "app_config deve apontar para as 2 filas consumidas e as 2 DLQs."
  }
}

run "ssm_publicados" {
  command = apply

  assert {
    condition     = aws_sns_topic.eventos.name == "mecanica-execucao-eventos"
    error_message = "Topico mecanica-execucao-eventos."
  }

  assert {
    condition     = aws_ssm_parameter.eventos_topic_arn.name == "/mecanica/execucao/execucao-eventos-topic-arn" && aws_ssm_parameter.eventos_topic_arn.insecure_value == aws_sns_topic.eventos.arn
    error_message = "O ARN do topico deve estar no SSM (lido pelo infra/ do OS)."
  }

  assert {
    condition     = aws_ssm_parameter.comandos_queue_url.name == "/mecanica/execucao/execucao-comandos-queue-url" && aws_ssm_parameter.comandos_queue_url.insecure_value == aws_sqs_queue.comandos.url
    error_message = "A URL da fila de comandos deve estar no SSM (lida pelo infra/ do OS)."
  }

  assert {
    condition     = aws_ssm_parameter.os_eventos_queue_arn.name == "/mecanica/execucao/os-eventos-queue-arn" && aws_ssm_parameter.os_eventos_queue_arn.insecure_value == aws_sqs_queue.os_eventos.arn
    error_message = "O ARN da fila os-eventos deve estar no SSM (lido pelo infra/ do OS, F5-b)."
  }
}

run "tabelas_dynamo" {
  command = apply

  assert {
    condition = (
      aws_dynamodb_table.ordens.name == "execucao-ordens" && aws_dynamodb_table.ordens.hash_key == "osId"
      && aws_dynamodb_table.pecas.name == "execucao-pecas" && aws_dynamodb_table.pecas.hash_key == "pecaId"
      && aws_dynamodb_table.reservas.name == "execucao-reservas" && aws_dynamodb_table.reservas.hash_key == "osId" && aws_dynamodb_table.reservas.range_key == "pecaId"
      && aws_dynamodb_table.outbox.name == "execucao-outbox" && aws_dynamodb_table.outbox.hash_key == "id"
      && aws_dynamodb_table.idempotencia.name == "execucao-idempotencia" && aws_dynamodb_table.idempotencia.hash_key == "messageId"
      && aws_dynamodb_table.tempos.name == "execucao-tempos" && aws_dynamodb_table.tempos.hash_key == "servicoId"
    )
    error_message = "6 tabelas com os nomes e as chaves da RFC-F4-02."
  }

  assert {
    condition = alltrue([
      for t in [
        aws_dynamodb_table.ordens, aws_dynamodb_table.pecas, aws_dynamodb_table.reservas,
        aws_dynamodb_table.outbox, aws_dynamodb_table.idempotencia, aws_dynamodb_table.tempos
      ] : t.billing_mode == "PAY_PER_REQUEST" && t.deletion_protection_enabled == false
    ])
    error_message = "Todas on-demand e sem deletion_protection (ambiente descartavel)."
  }
}

run "indices_esparsos" {
  command = apply

  assert {
    condition = (
      length(aws_dynamodb_table.ordens.global_secondary_index) == 1
      && one(aws_dynamodb_table.ordens.global_secondary_index).name == "fila"
      && one(aws_dynamodb_table.ordens.global_secondary_index).hash_key == "fila"
      && one(aws_dynamodb_table.ordens.global_secondary_index).range_key == "filaOrdem"
      && one(aws_dynamodb_table.ordens.global_secondary_index).projection_type == "ALL"
    )
    error_message = "GSI fila: fila + filaOrdem, projecao ALL (F5-j)."
  }

  assert {
    condition = (
      length(aws_dynamodb_table.outbox.global_secondary_index) == 1
      && one(aws_dynamodb_table.outbox.global_secondary_index).name == "pendentes"
      && one(aws_dynamodb_table.outbox.global_secondary_index).hash_key == "pendente"
      && one(aws_dynamodb_table.outbox.global_secondary_index).range_key == "criadoEm"
      && one(aws_dynamodb_table.outbox.global_secondary_index).projection_type == "ALL"
    )
    error_message = "GSI pendentes: pendente + criadoEm, projecao ALL (F5-i)."
  }
}

run "ttl" {
  command = apply

  assert {
    condition = alltrue([
      for t in [aws_dynamodb_table.outbox, aws_dynamodb_table.idempotencia] :
      one(t.ttl).enabled == true && one(t.ttl).attribute_name == "expiraEm"
    ])
    error_message = "TTL ligado em expiraEm na outbox e na idempotencia."
  }
}

run "policy_fila_os_eventos" {
  command = apply

  assert {
    condition = (
      length(jsondecode(aws_sqs_queue_policy.os_eventos.policy).Statement) == 1
      && jsondecode(aws_sqs_queue_policy.os_eventos.policy).Statement[0].Action == "sqs:SendMessage"
      && jsondecode(aws_sqs_queue_policy.os_eventos.policy).Statement[0].Resource == aws_sqs_queue.os_eventos.arn
      && jsondecode(aws_sqs_queue_policy.os_eventos.policy).Statement[0].Condition.ArnEquals["aws:SourceArn"] == "arn:aws:sns:us-east-1:123456789012:mecanica-os-eventos"
    )
    error_message = "sqs:SendMessage so do SourceArn montado pelo nome do topico mecanica-os-eventos (F5-b)."
  }
}
