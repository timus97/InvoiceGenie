data "aws_availability_zones" "available" {
  state = "available"
}

locals {
  name_prefix = "${var.project}-${var.env}"
  azs         = slice(data.aws_availability_zones.available.names, 0, 2)
  tags = {
    Project   = "InvoiceGenie"
    Env       = var.env
    ManagedBy = "terraform"
  }

  # Generate a non-demo API key prefix (ProdSecurityValidator bans dev-local-key/test-key/demo)
  api_key_value = "ig-${var.env}-${random_password.api_key_suffix.result}"
  api_keys_csv  = "${local.api_key_value}:${var.pilot_tenant_id}"
}

resource "random_password" "db" {
  length           = 32
  special          = true
  override_special = "!#$%&*()-_=+[]{}<>:?"
}

resource "random_password" "api_key_suffix" {
  length  = 24
  special = false
}

resource "random_password" "jwt" {
  length  = 48
  special = false
}

module "network" {
  source             = "../../modules/network"
  name_prefix        = local.name_prefix
  vpc_cidr           = var.vpc_cidr
  azs                = local.azs
  single_nat_gateway = var.single_nat_gateway
  tags               = local.tags
}

module "security" {
  source    = "../../modules/security"
  name_prefix = local.name_prefix
  vpc_id    = module.network.vpc_id
  vpc_cidr  = module.network.vpc_cidr
  tags      = local.tags
}

module "ecr" {
  source      = "../../modules/ecr"
  name_prefix = local.name_prefix
  tags        = local.tags
}

module "data" {
  source                  = "../../modules/data"
  name_prefix             = local.name_prefix
  private_data_subnet_ids = module.network.private_data_subnet_ids
  rds_security_group_id   = module.security.rds_security_group_id
  db_name                 = var.db_name
  db_username             = var.db_username
  db_password             = random_password.db.result
  db_instance_class       = var.db_instance_class
  db_multi_az             = var.db_multi_az
  kms_key_arn             = module.security.kms_key_arn
  tags                    = local.tags
}

# --- Secrets Manager (values never in tfstate outputs as plaintext if marked sensitive) ---
resource "aws_secretsmanager_secret" "db_password" {
  name                    = "${local.name_prefix}/db-password"
  recovery_window_in_days = 0
  kms_key_id              = module.security.kms_key_arn
  tags                    = local.tags
}

resource "aws_secretsmanager_secret_version" "db_password" {
  secret_id     = aws_secretsmanager_secret.db_password.id
  secret_string = random_password.db.result
}

resource "aws_secretsmanager_secret" "api_keys" {
  name                    = "${local.name_prefix}/api-keys"
  recovery_window_in_days = 0
  kms_key_id              = module.security.kms_key_arn
  tags                    = local.tags
}

resource "aws_secretsmanager_secret_version" "api_keys" {
  secret_id     = aws_secretsmanager_secret.api_keys.id
  secret_string = local.api_keys_csv
}

resource "aws_secretsmanager_secret" "jwt" {
  name                    = "${local.name_prefix}/jwt-secret"
  recovery_window_in_days = 0
  kms_key_id              = module.security.kms_key_arn
  tags                    = local.tags
}

resource "aws_secretsmanager_secret_version" "jwt" {
  secret_id     = aws_secretsmanager_secret.jwt.id
  secret_string = random_password.jwt.result
}

# Bundle for operators (username + jdbc + keys) — sensitive output optional
resource "aws_secretsmanager_secret" "app_bundle" {
  name                    = "${local.name_prefix}/app-config"
  recovery_window_in_days = 0
  kms_key_id              = module.security.kms_key_arn
  tags                    = local.tags
}

resource "aws_secretsmanager_secret_version" "app_bundle" {
  secret_id = aws_secretsmanager_secret.app_bundle.id
  secret_string = jsonencode({
    jdbc_url     = module.data.jdbc_url
    db_username  = var.db_username
    db_password  = random_password.db.result
    api_keys     = local.api_keys_csv
    jwt_secret   = random_password.jwt.result
    pilot_tenant = var.pilot_tenant_id
  })
}

module "alb" {
  source               = "../../modules/alb"
  name_prefix          = local.name_prefix
  vpc_id               = module.network.vpc_id
  public_subnet_ids    = module.network.public_subnet_ids
  alb_security_group_id = module.security.alb_security_group_id
  certificate_arn      = var.certificate_arn
  tags                 = local.tags
}

module "ecs" {
  source                   = "../../modules/ecs"
  name_prefix              = local.name_prefix
  aws_region               = var.aws_region
  private_app_subnet_ids   = module.network.private_app_subnet_ids
  api_security_group_id    = module.security.api_security_group_id
  web_security_group_id    = module.security.web_security_group_id
  api_target_group_arn     = module.alb.api_target_group_arn
  web_target_group_arn     = module.alb.web_target_group_arn
  api_image                = "${module.ecr.api_repository_url}:${var.api_image_tag}"
  web_image                = "${module.ecr.web_repository_url}:${var.web_image_tag}"
  api_desired_count        = var.api_desired_count
  web_desired_count        = var.web_desired_count
  api_cpu                  = var.api_cpu
  api_memory               = var.api_memory
  web_cpu                  = var.web_cpu
  web_memory               = var.web_memory
  jdbc_url                 = module.data.jdbc_url
  db_username              = var.db_username
  db_password_secret_arn   = aws_secretsmanager_secret.db_password.arn
  api_keys_secret_arn      = aws_secretsmanager_secret.api_keys.arn
  jwt_secret_arn           = aws_secretsmanager_secret.jwt.arn
  security_mode            = var.security_mode
  tags                     = local.tags
}

module "observability" {
  source                      = "../../modules/observability"
  name_prefix                 = local.name_prefix
  alb_arn_suffix              = element(split("loadbalancer/", module.alb.alb_arn), 1)
  api_target_group_arn_suffix = "targetgroup/${element(split(":targetgroup/", module.alb.api_target_group_arn), 1)}"
  rds_instance_id             = "${local.name_prefix}-pg"
  alarm_email                 = var.alarm_email
  tags                        = local.tags
}

module "cicd_iam" {
  count                   = var.enable_cicd_iam ? 1 : 0
  source                  = "../../modules/cicd_iam"
  name_prefix             = local.name_prefix
  github_org              = var.github_org
  github_repo             = var.github_repo
  ecr_api_arn             = module.ecr.api_repository_arn
  ecr_web_arn             = module.ecr.web_repository_arn
  ecs_cluster_arn         = module.ecs.cluster_arn
  ecs_execution_role_arn  = module.ecs.execution_role_arn
  ecs_task_role_arn       = module.ecs.task_role_arn
  tags                    = local.tags
}
