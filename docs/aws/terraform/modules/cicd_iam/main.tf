data "aws_caller_identity" "current" {}
data "aws_region" "current" {}

resource "aws_iam_openid_connect_provider" "github" {
  url             = "https://token.actions.githubusercontent.com"
  client_id_list  = ["sts.amazonaws.com"]
  thumbprint_list = ["6938fd4d98bab03faadb97b34396831e3780aea1", "1c58a3a8518e8759bf075b76b750d4f2df264fcd"]
  tags            = var.tags
}

data "aws_iam_policy_document" "gha_assume" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRoleWithWebIdentity"]
    principals {
      type        = "Federated"
      identifiers = [aws_iam_openid_connect_provider.github.arn]
    }
    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:aud"
      values   = ["sts.amazonaws.com"]
    }
    condition {
      test     = "StringLike"
      variable = "token.actions.githubusercontent.com:sub"
      values = [
        "repo:${var.github_org}/${var.github_repo}:ref:refs/heads/main",
        "repo:${var.github_org}/${var.github_repo}:ref:refs/heads/Development",
        "repo:${var.github_org}/${var.github_repo}:pull_request"
      ]
    }
  }
}

resource "aws_iam_role" "gha_deploy" {
  name               = "${var.name_prefix}-gha-deploy"
  assume_role_policy = data.aws_iam_policy_document.gha_assume.json
  tags               = var.tags
}

resource "aws_iam_role_policy" "gha_deploy" {
  name = "${var.name_prefix}-gha-deploy"
  role = aws_iam_role.gha_deploy.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "ecr:GetAuthorizationToken"
        ]
        Resource = "*"
      },
      {
        Effect = "Allow"
        Action = [
          "ecr:BatchCheckLayerAvailability",
          "ecr:CompleteLayerUpload",
          "ecr:InitiateLayerUpload",
          "ecr:PutImage",
          "ecr:UploadLayerPart",
          "ecr:BatchGetImage",
          "ecr:DescribeRepositories",
          "ecr:DescribeImages"
        ]
        Resource = [var.ecr_api_arn, var.ecr_web_arn]
      },
      {
        Effect = "Allow"
        Action = [
          "ecs:DescribeClusters",
          "ecs:DescribeServices",
          "ecs:DescribeTaskDefinition",
          "ecs:RegisterTaskDefinition",
          "ecs:UpdateService",
          "ecs:ListTasks",
          "ecs:DescribeTasks"
        ]
        Resource = "*"
      },
      {
        Effect   = "Allow"
        Action   = ["iam:PassRole"]
        Resource = [var.ecs_execution_role_arn, var.ecs_task_role_arn]
      }
    ]
  })
}
