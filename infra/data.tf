# Publicada pelo Identity. Leitura direta: na ordem B4 (F4-a) o Identity sempre aplica antes.
data "aws_ssm_parameter" "jwt_public_key" {
  name = "/mecanica/identity/jwt-public-key"
}
