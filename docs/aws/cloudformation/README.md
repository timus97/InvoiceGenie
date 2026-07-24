# CloudFormation — InvoiceGenie AR

Single-stack template: [template.yaml](./template.yaml)

Implements the same P0 topology as Terraform (VPC, ALB, ECS Fargate, RDS, ECR, Secrets).

## Deploy

```bash
# 1) Prefer creating ECR first and pushing images, then pass full image URIs.
# Or deploy with desired count 0 by editing template, push, then update.

aws cloudformation deploy \
  --template-file template.yaml \
  --stack-name invoicegenie-staging \
  --capabilities CAPABILITY_NAMED_IAM \
  --parameter-overrides \
    EnvironmentName=staging \
    DbPassword='ReplaceWithStrongPassword16+' \
    ApiKeysCsv='ig-cf-smoke-notdemo:00000000-0000-0000-0000-000000000001' \
    JwtSecret='replace-with-long-jwt-secret' \
    ApiImageUri='123456789012.dkr.ecr.us-east-1.amazonaws.com/invoicegenie-staging/api:latest' \
    WebImageUri='123456789012.dkr.ecr.us-east-1.amazonaws.com/invoicegenie-staging/web:latest'
```

## Notes

- Do **not** use `dev-local-key` — prod fail-closed rejects it.  
- DB password must not be `ar` / `password` / similar.  
- Optional `CertificateArn` enables HTTPS + HTTP→HTTPS redirect.  
- Full modular Terraform (recommended for day-2 ops): [../terraform/README.md](../terraform/README.md)
