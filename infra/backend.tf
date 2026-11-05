# Configurado pelo servico-cd.yml com -backend-config (bucket mecanica-tfstate-<accountId>,
# key fiap-soat-mecanica-execucao/terraform.tfstate, region us-east-1, use_lockfile=true).
terraform {
  backend "s3" {}
}
