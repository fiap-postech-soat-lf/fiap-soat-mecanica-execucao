# ADR-F4-08: um repositorio por servico, criado no infra/ do proprio servico.
resource "aws_ecr_repository" "this" {
  name                 = "mecanica-execucao"
  image_tag_mutability = "MUTABLE" # o workflow_dispatch do CD reconstroi o mesmo SHA
  force_delete         = true      # destroy diario com imagens no repositorio

  image_scanning_configuration {
    scan_on_push = true
  }
}

resource "aws_ecr_lifecycle_policy" "this" {
  repository = aws_ecr_repository.this.name

  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "Mantem as 10 imagens mais recentes"
      selection = {
        tagStatus   = "any"
        countType   = "imageCountMoreThan"
        countNumber = 10
      }
      action = {
        type = "expire"
      }
    }]
  })
}
