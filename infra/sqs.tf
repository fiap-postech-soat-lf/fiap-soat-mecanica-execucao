# Filas do execucao (tabela 3.4 da F1) com DLQ. Destrutivel sem sobra (regras 3.3 do plano da F2).

# Comandos da Saga. O OS envia direto com a LabRole, na mesma conta, entao nao ha queue policy.
resource "aws_sqs_queue" "comandos_dlq" {
  name                      = "mecanica-execucao-comandos-dlq"
  message_retention_seconds = 1209600
}

resource "aws_sqs_queue" "comandos" {
  name                       = "mecanica-execucao-comandos"
  visibility_timeout_seconds = 30
  message_retention_seconds  = 345600

  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.comandos_dlq.arn
    maxReceiveCount     = 5
  })
}

resource "aws_ssm_parameter" "comandos_queue_url" {
  name           = "/mecanica/execucao/execucao-comandos-queue-url"
  type           = "String"
  insecure_value = aws_sqs_queue.comandos.url
}

# F5-b: recebe o OrdemServicoCancelada do topico os-eventos. A assinatura (filtrada por type) fica no
# infra/ do OS, por excecao a regra "o consumidor e dono da assinatura" (ADR-F4-03).
resource "aws_sqs_queue" "os_eventos_dlq" {
  name                      = "mecanica-execucao-os-eventos-dlq"
  message_retention_seconds = 1209600
}

resource "aws_sqs_queue" "os_eventos" {
  name                       = "mecanica-execucao-os-eventos"
  visibility_timeout_seconds = 30
  message_retention_seconds  = 345600

  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.os_eventos_dlq.arn
    maxReceiveCount     = 5
  })
}

# O topico do OS nasce depois deste infra/ (ordem B4), entao o ARN e montado pelo nome, como na F4-a.
resource "aws_sqs_queue_policy" "os_eventos" {
  queue_url = aws_sqs_queue.os_eventos.url

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Sid       = "PermitirTopicoOsEventos"
      Effect    = "Allow"
      Principal = { Service = "sns.amazonaws.com" }
      Action    = "sqs:SendMessage"
      Resource  = aws_sqs_queue.os_eventos.arn
      Condition = {
        ArnEquals = {
          "aws:SourceArn" = "arn:aws:sns:${var.aws_region}:${data.aws_caller_identity.atual.account_id}:mecanica-os-eventos"
        }
      }
    }]
  })
}

# Lido pelo infra/ do OS (endpoint da assinatura).
resource "aws_ssm_parameter" "os_eventos_queue_arn" {
  name           = "/mecanica/execucao/os-eventos-queue-arn"
  type           = "String"
  insecure_value = aws_sqs_queue.os_eventos.arn
}
