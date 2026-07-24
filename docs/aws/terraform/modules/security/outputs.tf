output "alb_security_group_id" { value = aws_security_group.alb.id }
output "web_security_group_id" { value = aws_security_group.web.id }
output "api_security_group_id" { value = aws_security_group.api.id }
output "rds_security_group_id" { value = aws_security_group.rds.id }
output "kms_key_arn" { value = aws_kms_key.this.arn }
output "kms_key_id" { value = aws_kms_key.this.key_id }
