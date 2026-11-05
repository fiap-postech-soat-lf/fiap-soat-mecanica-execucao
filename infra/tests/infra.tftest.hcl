# Testes do infra/ com o provider aws mockado: nao precisa de credencial AWS nem do EKS.

mock_provider "aws" {
  # O mock gera ARN aleatorio; o ARN do topico precisa ser valido para o SSM e as validacoes.
  mock_resource "aws_sns_topic" {
    defaults = {
      arn = "arn:aws:sns:us-east-1:123456789012:mecanica-execucao-eventos"
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
    condition     = toset(keys(output.app_config)) == toset(["AWS_REGION", "JWT_PUBLIC_KEY", "APP_MESSAGING_DLQS"])
    error_message = "app_config: regiao, JWT_PUBLIC_KEY e a DLQ, sem APP_MESSAGING_CONSUMIDOR_FILAS (o consumidor entra depois)."
  }

  assert {
    condition     = !contains(keys(output.app_config), "APP_MESSAGING_CONSUMIDOR_FILAS")
    error_message = "O consumidor ainda nao esta ligado (F4-k)."
  }

  assert {
    condition     = length(output.app_secrets) == 0
    error_message = "app_secrets fica vazio ate a F5."
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
    condition     = aws_sqs_queue.comandos.name == "mecanica-execucao-comandos" && aws_sqs_queue.comandos_dlq.name == "mecanica-execucao-comandos-dlq"
    error_message = "Nomes das filas conforme a tabela 3.4 da F1."
  }

  assert {
    condition = (
      jsondecode(aws_sqs_queue.comandos.redrive_policy).maxReceiveCount == 5
      && jsondecode(aws_sqs_queue.comandos.redrive_policy).deadLetterTargetArn == aws_sqs_queue.comandos_dlq.arn
    )
    error_message = "Redrive para a DLQ com maxReceiveCount = 5."
  }

  assert {
    condition     = output.app_config.APP_MESSAGING_DLQS == aws_sqs_queue.comandos_dlq.url
    error_message = "app_config deve apontar para a DLQ."
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
}
