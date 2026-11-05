# infra/ do serviço execucao

Terraform do Execução, aplicado pelo `servico-cd.yml@v1` antes do build da imagem.

> **Antecipado pela F4 (F4-k).** O OS precisa dos tópicos e das filas para a Saga existir no EKS, então o `infra/` já tem ECR, tópico, fila de comandos + DLQ e os SSM. **Nenhum consumidor está ligado**: o `app_config` não tem `APP_MESSAGING_CONSUMIDOR_FILAS`, e os comandos ficam na fila para inspeção. A F5 acrescenta as tabelas DynamoDB, o `APP_MESSAGING_CONSUMIDOR_FILAS` e o consumidor de `execucao-comandos`.

## Recursos

| Arquivo | Recursos |
|---|---|
| `data.tf` | Lê do SSM a chave pública do JWT (`/mecanica/identity/jwt-public-key`), publicada pelo Identity (ordem B4, F4-a) |
| `ecr.tf` | Repositório `mecanica-execucao` (`MUTABLE`, `force_delete`, scan on push) e lifecycle das 10 imagens mais recentes |
| `sns.tf` | Tópico `mecanica-execucao-eventos` e SSM `/mecanica/execucao/execucao-eventos-topic-arn` (lido pelo `infra/` do OS, que assina na `os-saga-respostas`) |
| `sqs.tf` | Fila `mecanica-execucao-comandos` (visibility 30 s, retenção 4 dias) + DLQ, redrive após 5 recebimentos, e SSM `/mecanica/execucao/execucao-comandos-queue-url`. Sem queue policy: o OS envia direto com a `LabRole`, na mesma conta |

- Tudo é destrutível sem sobra (regras 3.3 do plano da F2).
- Ordem B4: `identity → execucao → billing → os → -auth`. O OS lê o SSM deste serviço no `plan`.

## Comandos

```bash
terraform -chdir=infra init -backend=false
terraform -chdir=infra validate
terraform -chdir=infra test   # provider aws mockado, sem credencial
```

## Contrato com o CD (`servico-cd.yml@v1`)

O `infra/outputs.tf` precisa expor:

| Output | Tipo | Uso no CD |
|---|---|---|
| `ecr_repository_url` | `string` | Repositório da imagem (`mecanica-execucao`) |
| `app_config` | `map(string)` | Vira o ConfigMap `mecanica-execucao-config` |
| `app_secrets` | `map(string)`, `sensitive = true` | Vira o Secret `mecanica-execucao-secret` |

- Backend S3 configurado pelo CD: bucket `mecanica-tfstate-<accountId>`, key `fiap-soat-mecanica-execucao/terraform.tfstate`. O `backend.tf` fica só com `backend "s3" {}`.
- Provider AWS `~> 6.0`, `required_version = ">= 1.10.0, < 2.0.0"`.
- O CD passa `TF_VAR_mp_access_token` e `TF_VAR_mp_webhook_secret`. Variáveis não declaradas são ignoradas.
- Nomes de filas, tópicos, RDS e SSM seguem a tabela 3.4 do plano da F1.

### Chaves de mensageria no `app_config`

As chaves do `app_config` viram variáveis de ambiente do pod (`envFrom`).

| Chave | Valor | Propriedade |
|---|---|---|
| `APP_MESSAGING_CONSUMIDOR_FILAS` | URLs das filas consumidas, separadas por vírgula | `app.messaging.consumidor.filas` |
| `APP_MESSAGING_DLQS` | URLs das DLQs dessas filas | `app.messaging.dlqs` (gauge `dlq.depth`) |
| `MESSAGING_DESTINO_<NOME>` | ARN do tópico ou URL da fila | declarada no `application.properties` do serviço como `app.messaging.destinos.<nome>=${MESSAGING_DESTINO_<NOME>:}` |

Os destinos usam variável explícita porque a ligação relaxada do Spring não preserva o `-` das chaves de mapa vindas de variáveis de ambiente (`execucao-comandos` viraria `execucao.comandos`). A role do pod precisa de `sns:Publish` nos tópicos e de `sqs:SendMessage`, `ReceiveMessage`, `DeleteMessage` e `GetQueueAttributes` nas filas.

Valores atuais do `app_config`: `AWS_REGION`, `JWT_PUBLIC_KEY` (PEM com `\n` escapado) e `APP_MESSAGING_DLQS` (URL da DLQ). O `app_secrets` fica **vazio** até a F5: o `kubectl create secret --from-env-file` aceita um arquivo vazio (conferido na F4).
