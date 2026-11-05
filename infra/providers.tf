provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Project    = "mecanica"
      ManagedBy  = "terraform"
      Repository = "fiap-soat-mecanica-execucao"
    }
  }
}
