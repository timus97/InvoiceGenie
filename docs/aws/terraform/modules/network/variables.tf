variable "name_prefix" { type = string }
variable "vpc_cidr" { type = string }
variable "azs" { type = list(string) }
variable "single_nat_gateway" {
  type        = bool
  default     = true
  description = "One NAT for cost (pilot). Set false for HA NAT per AZ."
}
variable "tags" { type = map(string) }
