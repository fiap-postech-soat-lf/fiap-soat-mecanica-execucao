# Topico de eventos do execucao. O OS assina na fila os-saga-respostas (infra/ do OS, tabela 3.4 da F4).
resource "aws_sns_topic" "eventos" {
  name = "mecanica-execucao-eventos"
}

resource "aws_ssm_parameter" "eventos_topic_arn" {
  name           = "/mecanica/execucao/execucao-eventos-topic-arn"
  type           = "String"
  insecure_value = aws_sns_topic.eventos.arn
}
