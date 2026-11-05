# Fila de comandos do execucao (tabela 3.4 da F1) com DLQ. O OS envia direto com a LabRole, na mesma
# conta, entao nao ha queue policy. Antecipada pela F4: o consumidor so entra na fase do servico.
# Destrutivel sem sobra (regras 3.3 do plano da F2).
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
