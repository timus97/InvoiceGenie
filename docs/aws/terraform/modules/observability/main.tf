resource "aws_sns_topic" "alarms" {
  count = var.alarm_email != "" ? 1 : 0
  name  = "${var.name_prefix}-alarms"
  tags  = var.tags
}

resource "aws_sns_topic_subscription" "email" {
  count     = var.alarm_email != "" ? 1 : 0
  topic_arn = aws_sns_topic.alarms[0].arn
  protocol  = "email"
  endpoint  = var.alarm_email
}

locals {
  alarm_actions = var.alarm_email != "" ? [aws_sns_topic.alarms[0].arn] : []
}

resource "aws_cloudwatch_metric_alarm" "alb_5xx" {
  alarm_name          = "${var.name_prefix}-alb-5xx"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 2
  metric_name         = "HTTPCode_Target_5XX_Count"
  namespace           = "AWS/ApplicationELB"
  period              = 60
  statistic           = "Sum"
  threshold           = 10
  alarm_description   = "ALB target 5xx elevated"
  treat_missing_data  = "notBreaching"
  alarm_actions       = local.alarm_actions
  dimensions = {
    LoadBalancer = var.alb_arn_suffix
  }
  tags = var.tags
}

resource "aws_cloudwatch_metric_alarm" "unhealthy" {
  alarm_name          = "${var.name_prefix}-api-unhealthy"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 2
  metric_name         = "UnHealthyHostCount"
  namespace           = "AWS/ApplicationELB"
  period              = 60
  statistic           = "Average"
  threshold           = 0
  alarm_description   = "API target group has unhealthy hosts"
  treat_missing_data  = "notBreaching"
  alarm_actions       = local.alarm_actions
  dimensions = {
    LoadBalancer = var.alb_arn_suffix
    TargetGroup  = var.api_target_group_arn_suffix
  }
  tags = var.tags
}

resource "aws_cloudwatch_metric_alarm" "rds_storage" {
  alarm_name          = "${var.name_prefix}-rds-storage"
  comparison_operator = "LessThanThreshold"
  evaluation_periods  = 1
  metric_name         = "FreeStorageSpace"
  namespace           = "AWS/RDS"
  period              = 300
  statistic           = "Average"
  threshold           = 5000000000 # 5 GB
  alarm_description   = "RDS free storage low"
  alarm_actions       = local.alarm_actions
  dimensions = {
    DBInstanceIdentifier = var.rds_instance_id
  }
  tags = var.tags
}
