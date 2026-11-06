# Publicada pelo Identity. Leitura direta: na ordem B4 (F4-a) o Identity sempre aplica antes.
data "aws_ssm_parameter" "jwt_public_key" {
  name = "/mecanica/identity/jwt-public-key"
}

# O ARN do topico do OS e montado pelo nome (F5-b): o topico mecanica-os-eventos nasce depois do
# Execucao na ordem B4, entao nao ha SSM para ler aqui.
data "aws_caller_identity" "atual" {}
