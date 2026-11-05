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

    # Sem APP_MESSAGING_CONSUMIDOR_FILAS: o consumidor entra na fase do servico.
    APP_MESSAGING_DLQS = aws_sqs_queue.comandos_dlq.url
  }
}

output "app_secrets" {
  description = "Vira o Secret mecanica-execucao-secret."
  sensitive   = true
  # Vazio ate a F5 (o kubectl aceita --from-env-file de um arquivo vazio).
  value = {}
}
