variable "name_prefix" { type = string }
variable "private_data_subnet_ids" { type = list(string) }
variable "rds_security_group_id" { type = string }
variable "db_name" { type = string }
variable "db_username" { type = string }
variable "db_password" {
  type      = string
  sensitive = true
}
variable "db_instance_class" { type = string }
variable "db_allocated_storage" {
  type    = number
  default = 50
}
variable "db_multi_az" { type = bool }
variable "db_engine_version" {
  type    = string
  default = "15.8"
}
variable "kms_key_arn" { type = string }
variable "backup_retention_days" {
  type    = number
  default = 7
}
variable "tags" { type = map(string) }
