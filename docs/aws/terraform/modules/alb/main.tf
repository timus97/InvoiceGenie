resource "aws_lb" "this" {
  name               = substr("${var.name_prefix}-alb", 0, 32)
  internal           = false
  load_balancer_type = "application"
  security_groups    = [var.alb_security_group_id]
  subnets            = var.public_subnet_ids
  tags               = merge(var.tags, { Name = "${var.name_prefix}-alb" })
}

resource "aws_lb_target_group" "api" {
  name        = substr("${var.name_prefix}-api", 0, 32)
  port        = 8080
  protocol    = "HTTP"
  vpc_id      = var.vpc_id
  target_type = "ip"
  health_check {
    enabled             = true
    path                = "/q/health"
    protocol            = "HTTP"
    matcher             = "200"
    interval            = 30
    timeout             = 5
    healthy_threshold   = 2
    unhealthy_threshold = 3
  }
  deregistration_delay = 30
  tags                 = var.tags
}

resource "aws_lb_target_group" "web" {
  name        = substr("${var.name_prefix}-web", 0, 32)
  port        = 3000
  protocol    = "HTTP"
  vpc_id      = var.vpc_id
  target_type = "ip"
  health_check {
    enabled             = true
    path                = "/"
    protocol            = "HTTP"
    matcher             = "200-399"
    interval            = 30
    timeout             = 5
    healthy_threshold   = 2
    unhealthy_threshold = 3
  }
  deregistration_delay = 30
  tags                 = var.tags
}

locals {
  use_https = var.certificate_arn != null && var.certificate_arn != ""
}

resource "aws_lb_listener" "http" {
  load_balancer_arn = aws_lb.this.arn
  port              = 80
  protocol          = "HTTP"

  dynamic "default_action" {
    for_each = local.use_https ? [1] : []
    content {
      type = "redirect"
      redirect {
        port        = "443"
        protocol    = "HTTPS"
        status_code = "HTTP_301"
      }
    }
  }

  dynamic "default_action" {
    for_each = local.use_https ? [] : [1]
    content {
      type             = "forward"
      target_group_arn = aws_lb_target_group.web.arn
    }
  }
}

resource "aws_lb_listener" "https" {
  count             = local.use_https ? 1 : 0
  load_balancer_arn = aws_lb.this.arn
  port              = 443
  protocol          = "HTTPS"
  ssl_policy        = "ELBSecurityPolicy-TLS13-1-2-2021-06"
  certificate_arn   = var.certificate_arn
  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.web.arn
  }
}

locals {
  primary_listener_arn = local.use_https ? aws_lb_listener.https[0].arn : aws_lb_listener.http.arn
}

# Path rules on the active listener (HTTPS when present, else HTTP)
resource "aws_lb_listener_rule" "api" {
  listener_arn = local.primary_listener_arn
  priority     = 10
  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.api.arn
  }
  condition {
    path_pattern { values = ["/api/*", "/q/health", "/q/health/*"] }
  }
}

# Explicit deny swagger/openapi at edge (parity with nginx-tls sample)
resource "aws_lb_listener_rule" "block_openapi" {
  listener_arn = local.primary_listener_arn
  priority     = 5
  action {
    type = "fixed-response"
    fixed_response {
      content_type = "text/plain"
      message_body = "Not Found"
      status_code  = "404"
    }
  }
  condition {
    path_pattern { values = ["/q/swagger-ui", "/q/swagger-ui/*", "/q/openapi", "/q/openapi/*"] }
  }
}

# When HTTPS is enabled, also attach API rules on HTTP? redirect handles it.
# When HTTP-only, add API rules already on http via primary_listener.
