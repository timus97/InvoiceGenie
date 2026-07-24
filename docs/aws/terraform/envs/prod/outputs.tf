output "alb_dns_name" {
  description = "Public ALB DNS — open http://THIS/ after images are deployed"
  value       = module.alb.alb_dns_name
}

output "ecr_api_repository_url" {
  value = module.ecr.api_repository_url
}

output "ecr_web_repository_url" {
  value = module.ecr.web_repository_url
}

output "rds_endpoint" {
  value = module.data.db_endpoint
}

output "jdbc_url" {
  value     = module.data.jdbc_url
  sensitive = true
}

output "ecs_cluster_name" {
  value = module.ecs.cluster_name
}

output "api_service_name" {
  value = module.ecs.api_service_name
}

output "web_service_name" {
  value = module.ecs.web_service_name
}

output "secrets_app_config_arn" {
  description = "JSON bundle with jdbc, api keys, jwt — retrieve with AWS CLI"
  value       = aws_secretsmanager_secret.app_bundle.arn
}

output "github_deploy_role_arn" {
  value = try(module.cicd_iam[0].github_deploy_role_arn, null)
}

output "push_images_commands" {
  description = "Run after first apply to push images then force new ECS deployment"
  value       = <<-EOT
    AWS_REGION=${var.aws_region}
    ACCOUNT=$(aws sts get-caller-identity --query Account --output text)
    aws ecr get-login-password --region $AWS_REGION | docker login --username AWS --password-stdin $ACCOUNT.dkr.ecr.$AWS_REGION.amazonaws.com
    docker build -f Dockerfile.prod -t ${module.ecr.api_repository_url}:${var.api_image_tag} .
    docker push ${module.ecr.api_repository_url}:${var.api_image_tag}
    docker build -f web/Dockerfile -t ${module.ecr.web_repository_url}:${var.web_image_tag} ./web
    docker push ${module.ecr.web_repository_url}:${var.web_image_tag}
    aws ecs update-service --cluster ${module.ecs.cluster_name} --service ${module.ecs.api_service_name} --force-new-deployment --region $AWS_REGION
    aws ecs update-service --cluster ${module.ecs.cluster_name} --service ${module.ecs.web_service_name} --force-new-deployment --region $AWS_REGION
  EOT
}

output "smoke_hint" {
  value = "aws secretsmanager get-secret-value --secret-id ${aws_secretsmanager_secret.app_bundle.name} --query SecretString --output text"
}
