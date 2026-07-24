resource "aws_vpc" "this" {
  cidr_block           = var.vpc_cidr
  enable_dns_hostnames = true
  enable_dns_support   = true
  tags                 = merge(var.tags, { Name = "${var.name_prefix}-vpc" })
}

resource "aws_internet_gateway" "this" {
  vpc_id = aws_vpc.this.id
  tags   = merge(var.tags, { Name = "${var.name_prefix}-igw" })
}

locals {
  az_count = length(var.azs)
}

resource "aws_subnet" "public" {
  count                   = local.az_count
  vpc_id                  = aws_vpc.this.id
  cidr_block              = cidrsubnet(var.vpc_cidr, 4, count.index)
  availability_zone       = var.azs[count.index]
  map_public_ip_on_launch = true
  tags = merge(var.tags, {
    Name = "${var.name_prefix}-public-${var.azs[count.index]}"
    Tier = "public"
  })
}

resource "aws_subnet" "private_app" {
  count             = local.az_count
  vpc_id            = aws_vpc.this.id
  cidr_block        = cidrsubnet(var.vpc_cidr, 4, count.index + 4)
  availability_zone = var.azs[count.index]
  tags = merge(var.tags, {
    Name = "${var.name_prefix}-app-${var.azs[count.index]}"
    Tier = "app"
  })
}

resource "aws_subnet" "private_data" {
  count             = local.az_count
  vpc_id            = aws_vpc.this.id
  cidr_block        = cidrsubnet(var.vpc_cidr, 4, count.index + 8)
  availability_zone = var.azs[count.index]
  tags = merge(var.tags, {
    Name = "${var.name_prefix}-data-${var.azs[count.index]}"
    Tier = "data"
  })
}

resource "aws_eip" "nat" {
  count  = var.single_nat_gateway ? 1 : local.az_count
  domain = "vpc"
  tags   = merge(var.tags, { Name = "${var.name_prefix}-nat-eip-${count.index}" })
}

resource "aws_nat_gateway" "this" {
  count         = var.single_nat_gateway ? 1 : local.az_count
  allocation_id = aws_eip.nat[count.index].id
  subnet_id     = aws_subnet.public[count.index].id
  depends_on    = [aws_internet_gateway.this]
  tags          = merge(var.tags, { Name = "${var.name_prefix}-nat-${count.index}" })
}

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.this.id
  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.this.id
  }
  tags = merge(var.tags, { Name = "${var.name_prefix}-public-rt" })
}

resource "aws_route_table_association" "public" {
  count          = local.az_count
  subnet_id      = aws_subnet.public[count.index].id
  route_table_id = aws_route_table.public.id
}

resource "aws_route_table" "private_app" {
  count  = local.az_count
  vpc_id = aws_vpc.this.id
  route {
    cidr_block     = "0.0.0.0/0"
    nat_gateway_id = var.single_nat_gateway ? aws_nat_gateway.this[0].id : aws_nat_gateway.this[count.index].id
  }
  tags = merge(var.tags, { Name = "${var.name_prefix}-app-rt-${count.index}" })
}

resource "aws_route_table_association" "private_app" {
  count          = local.az_count
  subnet_id      = aws_subnet.private_app[count.index].id
  route_table_id = aws_route_table.private_app[count.index].id
}

resource "aws_route_table" "private_data" {
  vpc_id = aws_vpc.this.id
  tags   = merge(var.tags, { Name = "${var.name_prefix}-data-rt" })
}

resource "aws_route_table_association" "private_data" {
  count          = local.az_count
  subnet_id      = aws_subnet.private_data[count.index].id
  route_table_id = aws_route_table.private_data.id
}

resource "aws_cloudwatch_log_group" "vpc_flow" {
  name              = "/invoicegenie/${var.name_prefix}/vpc-flow"
  retention_in_days = 14
  tags              = var.tags
}

resource "aws_flow_log" "this" {
  vpc_id          = aws_vpc.this.id
  traffic_type    = "ALL"
  iam_role_arn    = aws_iam_role.flow.arn
  log_destination = aws_cloudwatch_log_group.vpc_flow.arn
  tags            = var.tags
}

resource "aws_iam_role" "flow" {
  name = "${var.name_prefix}-vpc-flow"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "vpc-flow-logs.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
  tags = var.tags
}

resource "aws_iam_role_policy" "flow" {
  name = "${var.name_prefix}-vpc-flow"
  role = aws_iam_role.flow.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["logs:CreateLogStream", "logs:PutLogEvents", "logs:DescribeLogGroups", "logs:DescribeLogStreams"]
      Resource = "*"
    }]
  })
}
