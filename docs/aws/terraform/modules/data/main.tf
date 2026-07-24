resource "aws_db_subnet_group" "this" {
  name       = "${var.name_prefix}-db"
  subnet_ids = var.private_data_subnet_ids
  tags       = merge(var.tags, { Name = "${var.name_prefix}-db-subnets" })
}

resource "aws_db_parameter_group" "this" {
  name   = "${var.name_prefix}-pg15"
  family = "postgres15"
  parameter {
    name  = "rds.force_ssl"
    value = "1"
  }
  tags = var.tags
}

resource "aws_db_instance" "this" {
  identifier                            = "${var.name_prefix}-pg"
  engine                                = "postgres"
  engine_version                        = var.db_engine_version
  instance_class                        = var.db_instance_class
  allocated_storage                     = var.db_allocated_storage
  max_allocated_storage                 = var.db_allocated_storage * 4
  storage_type                          = "gp3"
  storage_encrypted                     = true
  kms_key_id                            = var.kms_key_arn
  db_name                               = var.db_name
  username                              = var.db_username
  password                              = var.db_password
  db_subnet_group_name                  = aws_db_subnet_group.this.name
  vpc_security_group_ids                = [var.rds_security_group_id]
  parameter_group_name                  = aws_db_parameter_group.this.name
  multi_az                              = var.db_multi_az
  publicly_accessible                   = false
  backup_retention_period               = var.backup_retention_days
  deletion_protection                   = false
  skip_final_snapshot                   = true
  copy_tags_to_snapshot                 = true
  performance_insights_enabled          = true
  performance_insights_kms_key_id       = var.kms_key_arn
  auto_minor_version_upgrade            = true
  tags                                  = merge(var.tags, { Name = "${var.name_prefix}-rds" })
}
