variable "aws_region" {
  type    = string
  default = "us-east-1"
}

variable "env" {
  type    = string
  default = "staging"
}

variable "project" {
  type    = string
  default = "invoicegenie"
}

variable "vpc_cidr" {
  type    = string
  default = "10.20.0.0/16"
}

variable "single_nat_gateway" {
  type    = bool
  default = true
}

variable "db_instance_class" {
  type    = string
  default = "db.t4g.medium"
}

variable "db_multi_az" {
  type    = bool
  default = false
}

variable "db_name" {
  type    = string
  default = "invoicegenie"
}

variable "db_username" {
  type    = string
  default = "invoicegenie"
}

variable "api_desired_count" {
  type    = number
  default = 1
}

variable "web_desired_count" {
  type    = number
  default = 1
}

variable "api_cpu" {
  type    = number
  default = 1024
}

variable "api_memory" {
  type    = number
  default = 2048
}

variable "web_cpu" {
  type    = number
  default = 512
}

variable "web_memory" {
  type    = number
  default = 1024
}

variable "certificate_arn" {
  type        = string
  default     = ""
  description = "Optional ACM cert ARN in this region for HTTPS on ALB"
}

variable "api_image_tag" {
  type    = string
  default = "latest"
}

variable "web_image_tag" {
  type    = string
  default = "latest"
}

variable "security_mode" {
  type    = string
  default = "api-key"
  validation {
    condition     = contains(["api-key", "jwt", "hybrid"], var.security_mode)
    error_message = "security_mode must be api-key, jwt, or hybrid."
  }
}

variable "pilot_tenant_id" {
  type    = string
  default = "00000000-0000-0000-0000-000000000001"
}

variable "github_org" {
  type    = string
  default = "timus97"
}

variable "github_repo" {
  type    = string
  default = "InvoiceGenie"
}

variable "alarm_email" {
  type    = string
  default = ""
}

variable "enable_cicd_iam" {
  type    = bool
  default = true
}
