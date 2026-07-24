output "sns_topic_arn" {
  value = try(aws_sns_topic.alarms[0].arn, null)
}
