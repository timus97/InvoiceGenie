output "db_endpoint" { value = aws_db_instance.this.address }
output "db_port" { value = aws_db_instance.this.port }
output "db_name" { value = aws_db_instance.this.db_name }
output "db_instance_arn" { value = aws_db_instance.this.arn }
output "jdbc_url" {
  value = "jdbc:postgresql://${aws_db_instance.this.address}:${aws_db_instance.this.port}/${aws_db_instance.this.db_name}?sslmode=require"
}
