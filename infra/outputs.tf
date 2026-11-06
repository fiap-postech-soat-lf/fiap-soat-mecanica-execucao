# Contrato infra -> CD (secao 3.6 do plano da F1): o servico-cd.yml le estes tres outputs.

output "ecr_repository_url" {
  description = "Repositorio da imagem do servico."
  value       = aws_ecr_repository.this.repository_url
}

output "app_config" {
  description = "Vira o ConfigMap mecanica-execucao-config."
  value = {
    AWS_REGION = var.aws_region
    # --from-env-file nao aceita valor multilinha; o JwtTokenValidator aceita PEM com \n escapado.
    JWT_PUBLIC_KEY = replace(data.aws_ssm_parameter.jwt_public_key.insecure_value, "\n", "\\n")

    # URL do OS pelo DNS do cluster, sem passar pelo API Gateway (catalogo de servicos, F5-o).
    APP_OS_BASE_URL = "http://mecanica-os.mecanica.svc.cluster.local:8080"

    APP_MESSAGING_CONSUMIDOR_FILAS = join(",", [aws_sqs_queue.comandos.url, aws_sqs_queue.os_eventos.url])
    APP_MESSAGING_DLQS             = join(",", [aws_sqs_queue.comandos_dlq.url, aws_sqs_queue.os_eventos_dlq.url])

    MESSAGING_DESTINO_EXECUCAO_EVENTOS = aws_sns_topic.eventos.arn
  }
}

output "app_secrets" {
  description = "Vira o Secret mecanica-execucao-secret."
  sensitive   = true
  # Vazio: o servico usa a LabRole, sem segredo (o kubectl aceita --from-env-file de um arquivo vazio).
  value = {}
}
