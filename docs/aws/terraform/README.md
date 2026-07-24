# Terraform layout (skeleton) — InvoiceGenie on AWS

> **Status:** Skeleton only — modules are not fully implemented.  
> Use with [AWS_HOSTING_DESIGN.md](../AWS_HOSTING_DESIGN.md) and [AWS_DEPLOYMENT_CHECKLIST.md](../AWS_DEPLOYMENT_CHECKLIST.md).

## Purpose

Infrastructure as Code for InvoiceGenie AR:

- Quarkus API (`Dockerfile.prod`) on **ECS Fargate**
- Next.js web (`web/Dockerfile`) on **ECS Fargate**
- **RDS PostgreSQL 15** (private)
- ALB + ACM (+ optional CloudFront/WAF)
- Secrets Manager / SSM
- GitHub OIDC deploy roles

## Recommended tree

```
docs/aws/terraform/
├── README.md                 # this file
├── versions.tf               # terraform + provider version constraints
├── modules/
│   ├── network/              # VPC, subnets, NAT, routes, flow logs
│   ├── security/             # SGs, WAF, KMS
│   ├── data/                 # RDS, subnet group, param group
│   ├── ecr/                  # invoicegenie-api, invoicegenie-web
│   ├── ecs/                  # cluster, task definitions, services, IAM
│   ├── alb/                  # ALB, TGs, listeners, rules
│   ├── cdn/                  # CloudFront (optional module)
│   ├── observability/        # log groups, alarms, dashboard
│   └── cicd_iam/             # GitHub OIDC + deploy role
└── envs/
    ├── dev/
    │   ├── main.tf
    │   ├── variables.tf
    │   ├── outputs.tf
    │   ├── terraform.tfvars.example
    │   └── backend.tf        # S3 + DynamoDB lock
    ├── staging/
    └── prod/
```

## Module responsibilities

| Module | Key resources | InvoiceGenie-specific notes |
|--------|---------------|------------------------------|
| `network` | `aws_vpc`, subnets ×3 tiers, NAT, IGW | Data subnets isolated; no public RDS routes |
| `security` | SG rules, WAF WebACL, KMS CMK | Block `/q/swagger-ui`, `/q/openapi` via WAF/ALB rules |
| `data` | `aws_db_instance` Postgres 15 | `engine_version` 15.x; `publicly_accessible = false`; inject endpoint into secrets |
| `ecr` | 2 repositories | Scan on push; lifecycle |
| `ecs` | Cluster, services `api` + `web` | API port **8080**, health **`/q/health`**; web port **3000**; inject `QUARKUS_*`, `INVOICEGENIE_*`, `BACKEND_URL` |
| `alb` | ALB, HTTPS, path rules | `/api/*` + `/q/health` → api; default → web |
| `cdn` | CloudFront | Optional P1; ACM in `us-east-1` for CF |
| `observability` | CW alarms | 5xx, unhealthy hosts, RDS storage |
| `cicd_iam` | OIDC + role | Permissions: ECR push, `ecs:UpdateService`, `iam:PassRole` limited |

## Variable surface (env root)

```hcl
variable "env" { type = string }          # dev | staging | prod
variable "region" { type = string }
variable "vpc_cidr" { type = string }
variable "domain_name" { type = string }
variable "api_image" { type = string }    # ECR URL + tag/digest
variable "web_image" { type = string }
variable "api_desired_count" { type = number }
variable "web_desired_count" { type = number }
variable "db_instance_class" { type = string }
variable "db_multi_az" { type = bool }
variable "enable_waf" { type = bool }
variable "enable_cloudfront" { type = bool }
```

## Secrets wiring pattern

Do **not** put secret values in `tfvars`. Pattern:

1. RDS master password: `random_password` → `aws_secretsmanager_secret_version` (or manage outside TF).
2. ECS task definition `secrets` blocks reference Secrets Manager ARNs for:
   - `QUARKUS_DATASOURCE_PASSWORD`
   - `INVOICEGENIE_API_KEYS`
   - `INVOICEGENIE_JWT_SECRET`
3. Non-secret config as plain `environment` entries (`QUARKUS_PROFILE=prod`, `OUTBOX_KAFKA_ENABLED=false`).

## ECS env mapping (API)

| Container env | Value / source |
|---------------|----------------|
| `QUARKUS_PROFILE` | `prod` |
| `QUARKUS_DATASOURCE_JDBC_URL` | `jdbc:postgresql://${rds_endpoint}:5432/invoicegenie?sslmode=require` |
| `QUARKUS_DATASOURCE_USERNAME` | secret or SSM |
| `QUARKUS_DATASOURCE_PASSWORD` | Secrets Manager |
| `INVOICEGENIE_SECURITY_ENABLED` | `true` |
| `INVOICEGENIE_SECURITY_MODE` | `api-key` |
| `INVOICEGENIE_API_KEYS` | Secrets Manager |
| `INVOICEGENIE_SECURITY_ALLOW_OPENAPI` | `false` |
| `OUTBOX_KAFKA_ENABLED` | `false` |
| `QUARKUS_LOG_CONSOLE_JSON` | `true` |
| `QUARKUS_FLYWAY_MIGRATE_AT_START` | `true` (P0) |

## ECS env mapping (Web)

| Container env | Value / source |
|---------------|----------------|
| `BACKEND_URL` | `http://invoicegenie-api:8080` (Service Connect) |
| `NEXT_PUBLIC_ALLOW_TENANT_OVERRIDE` | `false` |
| `NEXT_PUBLIC_DEFAULT_TENANT_ID` | pilot tenant UUID |
| `NEXT_PUBLIC_APP_NAME` | `InvoiceGenie AR` |

## State & workspaces

- Remote state: S3 bucket + DynamoDB lock table **per account**
- One root module per env directory (clearest blast radius)
- Never share prod state with dev

## Apply order (first time)

```text
1. network
2. security (KMS + SGs)
3. ecr
4. data (RDS)
5. secrets population (manual or null_resource / external)
6. alb (+ ACM validation)
7. ecs (api then web)
8. observability
9. cdn / waf (optional)
10. cicd_iam
```

## Out of scope for this skeleton

- Full HCL implementation
- MSK module (add under `modules/messaging` when `OUTBOX_KAFKA_ENABLED=true`)
- Cognito user pool (STORY-003) — add `modules/auth` later
- Multi-region DR

## Local commands (when modules exist)

```bash
cd docs/aws/terraform/envs/staging
terraform init
terraform plan -out=tfplan
terraform apply tfplan
```

## Related repo artifacts

| Artifact | Path |
|----------|------|
| API Dockerfile | `Dockerfile.prod` |
| Web Dockerfile | `web/Dockerfile` |
| Compose reference | `docker-compose.yml` |
| Nginx TLS path rules | `docs/deploy/nginx-tls.conf` |
| App config | `ar-bootstrap/src/main/resources/application.yml` |
| CI | `.github/workflows/ci.yml` |
