variable "name_prefix" { type = string }
variable "alb_arn_suffix" { type = string }
variable "api_target_group_arn_suffix" { type = string }
variable "rds_instance_id" { type = string }
variable "alarm_email" {
  type    = string
  default = ""
}
variable "tags" { type = map(string) }
