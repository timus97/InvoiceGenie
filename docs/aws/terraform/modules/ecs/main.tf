resource "aws_cloudwatch_log_group" "api" {
  name              = "/invoicegenie/${var.name_prefix}/api"
  retention_in_days = 30
  tags              = var.tags
}

resource "aws_cloudwatch_log_group" "web" {
  name              = "/invoicegenie/${var.name_prefix}/web"
  retention_in_days = 30
  tags              = var.tags
}

resource "aws_ecs_cluster" "this" {
  name = "${var.name_prefix}-cluster"
  setting {
    name  = "containerInsights"
    value = "enabled"
  }
  tags = var.tags
}

resource "aws_ecs_cluster_capacity_providers" "this" {
  cluster_name       = aws_ecs_cluster.this.name
  capacity_providers = ["FARGATE", "FARGATE_SPOT"]
  default_capacity_provider_strategy {
    capacity_provider = "FARGATE"
    weight            = 1
    base              = 1
  }
}

resource "aws_service_discovery_http_namespace" "this" {
  name        = var.name_prefix
  description = "InvoiceGenie Service Connect namespace"
  tags        = var.tags
}

data "aws_iam_policy_document" "ecs_task_assume" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["ecs-tasks.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "execution" {
  name               = "${var.name_prefix}-ecs-exec"
  assume_role_policy = data.aws_iam_policy_document.ecs_task_assume.json
  tags               = var.tags
}

resource "aws_iam_role_policy_attachment" "execution" {
  role       = aws_iam_role.execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

resource "aws_iam_role_policy" "execution_secrets" {
  name = "${var.name_prefix}-ecs-exec-secrets"
  role = aws_iam_role.execution.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Action = ["secretsmanager:GetSecretValue", "kms:Decrypt"]
      Resource = [
        var.db_password_secret_arn,
        var.api_keys_secret_arn,
        var.jwt_secret_arn
      ]
    }]
  })
}

resource "aws_iam_role" "task" {
  name               = "${var.name_prefix}-ecs-task"
  assume_role_policy = data.aws_iam_policy_document.ecs_task_assume.json
  tags               = var.tags
}

resource "aws_ecs_task_definition" "api" {
  family                   = "${var.name_prefix}-api"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = var.api_cpu
  memory                   = var.api_memory
  execution_role_arn       = aws_iam_role.execution.arn
  task_role_arn            = aws_iam_role.task.arn
  runtime_platform {
    operating_system_family = "LINUX"
    cpu_architecture        = "X86_64"
  }
  container_definitions = jsonencode([{
    name      = "api"
    image     = var.api_image
    essential = true
    portMappings = [{
      name          = "api"
      containerPort = 8080
      hostPort      = 8080
      protocol      = "tcp"
      appProtocol   = "http"
    }]
    environment = [
      { name = "QUARKUS_PROFILE", value = "prod" },
      { name = "QUARKUS_HTTP_HOST", value = "0.0.0.0" },
      { name = "QUARKUS_DATASOURCE_JDBC_URL", value = var.jdbc_url },
      { name = "QUARKUS_DATASOURCE_USERNAME", value = var.db_username },
      { name = "INVOICEGENIE_SECURITY_ENABLED", value = "true" },
      { name = "INVOICEGENIE_SECURITY_MODE", value = var.security_mode },
      { name = "INVOICEGENIE_SECURITY_ALLOW_OPENAPI", value = "false" },
      { name = "QUARKUS_FLYWAY_MIGRATE_AT_START", value = "true" },
      { name = "OUTBOX_KAFKA_ENABLED", value = "false" },
      { name = "QUARKUS_LOG_CONSOLE_JSON", value = "true" },
      { name = "QUARKUS_LOG_FILE_ENABLE", value = "false" }
    ]
    secrets = [
      { name = "QUARKUS_DATASOURCE_PASSWORD", valueFrom = var.db_password_secret_arn },
      { name = "INVOICEGENIE_API_KEYS", valueFrom = var.api_keys_secret_arn },
      { name = "INVOICEGENIE_JWT_SECRET", valueFrom = var.jwt_secret_arn }
    ]
    logConfiguration = {
      logDriver = "awslogs"
      options = {
        awslogs-group         = aws_cloudwatch_log_group.api.name
        awslogs-region        = var.aws_region
        awslogs-stream-prefix = "api"
      }
    }
    healthCheck = {
      command     = ["CMD-SHELL", "curl -fsS http://127.0.0.1:8080/q/health || exit 1"]
      interval    = 30
      timeout     = 5
      retries     = 3
      startPeriod = 90
    }
  }])
  tags = var.tags
}

resource "aws_ecs_task_definition" "web" {
  family                   = "${var.name_prefix}-web"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = var.web_cpu
  memory                   = var.web_memory
  execution_role_arn       = aws_iam_role.execution.arn
  task_role_arn            = aws_iam_role.task.arn
  runtime_platform {
    operating_system_family = "LINUX"
    cpu_architecture        = "X86_64"
  }
  container_definitions = jsonencode([{
    name      = "web"
    image     = var.web_image
    essential = true
    portMappings = [{
      name          = "web"
      containerPort = 3000
      hostPort      = 3000
      protocol      = "tcp"
      appProtocol   = "http"
    }]
    environment = [
      # Service Connect DNS: http://api:8080 within namespace
      { name = "BACKEND_URL", value = "http://api:8080" },
      { name = "NEXT_PUBLIC_ALLOW_TENANT_OVERRIDE", value = "false" },
      { name = "NEXT_PUBLIC_APP_NAME", value = "InvoiceGenie AR" },
      { name = "PORT", value = "3000" },
      { name = "HOSTNAME", value = "0.0.0.0" }
    ]
    logConfiguration = {
      logDriver = "awslogs"
      options = {
        awslogs-group         = aws_cloudwatch_log_group.web.name
        awslogs-region        = var.aws_region
        awslogs-stream-prefix = "web"
      }
    }
  }])
  tags = var.tags
}

resource "aws_ecs_service" "api" {
  name            = "${var.name_prefix}-api"
  cluster         = aws_ecs_cluster.this.id
  task_definition = aws_ecs_task_definition.api.arn
  desired_count   = var.api_desired_count
  launch_type     = "FARGATE"
  network_configuration {
    subnets          = var.private_app_subnet_ids
    security_groups  = [var.api_security_group_id]
    assign_public_ip = var.assign_public_ip
  }
  load_balancer {
    target_group_arn = var.api_target_group_arn
    container_name   = "api"
    container_port   = 8080
  }
  service_connect_configuration {
    enabled   = true
    namespace = aws_service_discovery_http_namespace.this.arn
    service {
      port_name      = "api"
      discovery_name = "api"
      client_alias {
        port     = 8080
        dns_name = "api"
      }
    }
  }
  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }
  health_check_grace_period_seconds = 120
  tags                              = var.tags
  depends_on                        = [aws_ecs_task_definition.api]
  lifecycle {
    ignore_changes = [task_definition]
  }
}

resource "aws_ecs_service" "web" {
  name            = "${var.name_prefix}-web"
  cluster         = aws_ecs_cluster.this.id
  task_definition = aws_ecs_task_definition.web.arn
  desired_count   = var.web_desired_count
  launch_type     = "FARGATE"
  network_configuration {
    subnets          = var.private_app_subnet_ids
    security_groups  = [var.web_security_group_id]
    assign_public_ip = var.assign_public_ip
  }
  load_balancer {
    target_group_arn = var.web_target_group_arn
    container_name   = "web"
    container_port   = 3000
  }
  service_connect_configuration {
    enabled   = true
    namespace = aws_service_discovery_http_namespace.this.arn
  }
  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }
  health_check_grace_period_seconds = 60
  tags                              = var.tags
  depends_on                        = [aws_ecs_service.api]
  lifecycle {
    ignore_changes = [task_definition]
  }
}

resource "aws_appautoscaling_target" "api" {
  max_capacity       = max(var.api_desired_count * 4, 4)
  min_capacity       = var.api_desired_count
  resource_id        = "service/${aws_ecs_cluster.this.name}/${aws_ecs_service.api.name}"
  scalable_dimension = "ecs:service:DesiredCount"
  service_namespace  = "ecs"
}

resource "aws_appautoscaling_policy" "api_cpu" {
  name               = "${var.name_prefix}-api-cpu"
  policy_type        = "TargetTrackingScaling"
  resource_id        = aws_appautoscaling_target.api.resource_id
  scalable_dimension = aws_appautoscaling_target.api.scalable_dimension
  service_namespace  = aws_appautoscaling_target.api.service_namespace
  target_tracking_scaling_policy_configuration {
    predefined_metric_specification {
      predefined_metric_type = "ECSServiceAverageCPUUtilization"
    }
    target_value       = 60
    scale_in_cooldown  = 120
    scale_out_cooldown = 60
  }
}
