variable "name_prefix" { type = string }
variable "aws_region" { type = string }
variable "private_app_subnet_ids" { type = list(string) }
variable "api_security_group_id" { type = string }
variable "web_security_group_id" { type = string }
variable "api_target_group_arn" { type = string }
variable "web_target_group_arn" { type = string }
variable "api_image" { type = string }
variable "web_image" { type = string }
variable "api_desired_count" { type = number }
variable "web_desired_count" { type = number }
variable "api_cpu" { type = number }
variable "api_memory" { type = number }
variable "web_cpu" { type = number }
variable "web_memory" { type = number }
variable "jdbc_url" { type = string }
variable "db_username" { type = string }
variable "db_password_secret_arn" { type = string }
variable "api_keys_secret_arn" { type = string }
variable "jwt_secret_arn" { type = string }
variable "security_mode" {
  type    = string
  default = "api-key"
}
variable "assign_public_ip" {
  type    = bool
  default = false
}
variable "tags" { type = map(string) }
