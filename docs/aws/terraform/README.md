# InvoiceGenie on AWS — Terraform

> **Status:** Deployable HCL for P0 (VPC, ALB, ECS Fargate api+web, RDS Postgres 15, ECR, Secrets, alarms, GitHub OIDC).  
> **Design:** [AWS_HOSTING_DESIGN.md](../AWS_HOSTING_DESIGN.md) · **Checklist:** [AWS_DEPLOYMENT_CHECKLIST.md](../AWS_DEPLOYMENT_CHECKLIST.md)  
> **Alternative:** CloudFormation [../cloudformation/template.yaml](../cloudformation/template.yaml)

## Architecture (what this creates)

```
Internet → ALB (80 / optional 443)
             ├─ /api/* , /q/health → ECS Fargate API :8080
             └─ /*               → ECS Fargate Web :3000
                                       │ BACKEND_URL=http://api:8080 (Service Connect)
                                       ▼
                                  API → RDS PostgreSQL 15 (private, SSL)
Secrets Manager: db password, API keys, JWT secret
ECR: api + web repositories
CloudWatch: log groups + ALB/RDS alarms
Optional: GitHub Actions OIDC deploy role
```

## Layout

```
docs/aws/terraform/
├── README.md                 # this file
├── modules/
│   ├── network/              # VPC, 3 tiers, NAT, flow logs
│   ├── security/             # SGs + KMS
│   ├── data/                 # RDS Postgres 15
│   ├── ecr/                  # api + web repos
│   ├── alb/                  # ALB, TGs, path rules, OpenAPI block
│   ├── ecs/                  # cluster, tasks, services, autoscaling
│   ├── observability/        # CW alarms (+ optional SNS email)
│   └── cicd_iam/             # GitHub OIDC deploy role
└── envs/
    ├── staging/              # pilot defaults (single NAT, Single-AZ RDS)
    └── prod/                 # HA defaults in tfvars.example
```

## Prerequisites

1. AWS account + credentials (`aws configure` or env vars)
2. Terraform **≥ 1.5**
3. Docker (to build/push images)
4. IAM permissions to create VPC, ECS, RDS, IAM, Secrets Manager, ECR, ELB

## Quick start (staging)

### 1. Init

```bash
cd docs/aws/terraform/envs/staging
cp terraform.tfvars.example terraform.tfvars
# edit terraform.tfvars as needed
terraform init
```

### 2. Optional: remote state

Create S3 bucket + DynamoDB lock table, then uncomment `backend "s3"` in `versions.tf`.

### 3. Plan & apply

```bash
terraform plan -out=tfplan
terraform apply tfplan
```

**Note:** ECS services start immediately. If ECR images are empty, tasks will fail until you push (step 4).  
Either push images first, or set `api_desired_count = 0` / `web_desired_count = 0` in `terraform.tfvars`, apply, push images, then raise counts and re-apply.

### 4. Build & push images

```bash
# from repo root — commands also printed in terraform output push_images_commands
export AWS_REGION=us-east-1
ACCOUNT=$(aws sts get-caller-identity --query Account --output text)
API_REPO=$(cd docs/aws/terraform/envs/staging && terraform output -raw ecr_api_repository_url)
WEB_REPO=$(cd docs/aws/terraform/envs/staging && terraform output -raw ecr_web_repository_url)

aws ecr get-login-password --region $AWS_REGION | docker login --username AWS --password-stdin $ACCOUNT.dkr.ecr.$AWS_REGION.amazonaws.com

docker build -f Dockerfile.prod -t $API_REPO:latest .
docker push $API_REPO:latest

docker build -f web/Dockerfile -t $WEB_REPO:latest ./web
docker push $WEB_REPO:latest

CLUSTER=$(cd docs/aws/terraform/envs/staging && terraform output -raw ecs_cluster_name)
aws ecs update-service --cluster $CLUSTER --service $(cd docs/aws/terraform/envs/staging && terraform output -raw api_service_name) --force-new-deployment
aws ecs update-service --cluster $CLUSTER --service $(cd docs/aws/terraform/envs/staging && terraform output -raw web_service_name) --force-new-deployment
```

### 5. Retrieve secrets & smoke

```bash
aws secretsmanager get-secret-value \
  --secret-id $(cd docs/aws/terraform/envs/staging && terraform output -raw secrets_app_config_arn) \
  --query SecretString --output text | jq .

ALB=$(cd docs/aws/terraform/envs/staging && terraform output -raw alb_dns_name)
# extract api key from secret (jq -r .api_keys), then:
curl -sS "http://$ALB/q/health"
curl -sS -H "X-API-Key: <key>" -H "X-Tenant-Id: 00000000-0000-0000-0000-000000000001" \
  "http://$ALB/api/v1/customers"
```

### 6. HTTPS (optional)

1. Request ACM certificate in the **same region** as the ALB.  
2. Validate DNS.  
3. Set `certificate_arn` in `terraform.tfvars` and re-apply.  
4. HTTP will redirect to HTTPS; OpenAPI/Swagger paths stay 404 at the ALB.

## Important app wiring

| Env | Source |
|-----|--------|
| `QUARKUS_PROFILE=prod` | Task definition |
| `QUARKUS_DATASOURCE_JDBC_URL` | RDS endpoint + `sslmode=require` |
| `QUARKUS_DATASOURCE_PASSWORD` | Secrets Manager |
| `INVOICEGENIE_SECURITY_ENABLED=true` | Task definition |
| `INVOICEGENIE_API_KEYS` | Secrets Manager (generated non-demo key) |
| `BACKEND_URL` (web) | `http://api:8080` via **ECS Service Connect** |
| `NEXT_PUBLIC_ALLOW_TENANT_OVERRIDE` | `false` |

**ProdSecurityValidator** rejects demo keys (`dev-local-key`, `test-key`, `demo*`) and weak DB passwords (`ar`, `password`, …). Terraform generates strong random values.

## GitHub Actions deploy role

When `enable_cicd_iam = true`, output `github_deploy_role_arn` can be used from Actions:

```yaml
permissions:
  id-token: write
  contents: read
# aws-actions/configure-aws-credentials with role-to-assume: <github_deploy_role_arn>
```

## Destroy

```bash
terraform destroy
```

RDS is set `skip_final_snapshot = true` for staging ease; **prod module should enable final snapshot** before production destroy (edit `modules/data` or override).

## Cost notes (staging defaults)

- 1× NAT Gateway  
- RDS `db.t4g.medium` Single-AZ  
- 1 API + 1 web Fargate task  
- ALB  

Expect roughly low-hundreds USD/month depending on region and traffic. Use `prod` tfvars for Multi-AZ / dual NAT / 2 tasks.

## CloudFormation alternative

```bash
aws cloudformation deploy \
  --template-file docs/aws/cloudformation/template.yaml \
  --stack-name invoicegenie-staging \
  --capabilities CAPABILITY_NAMED_IAM \
  --parameter-overrides \
    DbPassword='YourStrongPasswordHere1' \
    ApiKeysCsv='ig-cf-smoke-abc123:00000000-0000-0000-0000-000000000001' \
    JwtSecret='your-jwt-secret-at-least-16' \
    ApiImageUri='ACCOUNT.dkr.ecr.REGION.amazonaws.com/invoicegenie-staging/api:latest' \
    WebImageUri='ACCOUNT.dkr.ecr.REGION.amazonaws.com/invoicegenie-staging/web:latest'
```

Push images to the ECR repos created by the stack (or pre-create repos). CFN web uses ALB DNS for `BACKEND_URL` (Terraform uses Service Connect `http://api:8080`).

## Out of scope (later modules)

- CloudFront / WAF WebACL  
- Cognito user pool  
- MSK  
- Multi-region DR  
