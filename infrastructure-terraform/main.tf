terraform {
  required_version = ">= 1.5.0"
  required_providers {
    aws = { source = "hashicorp/aws", version = "~> 5.0" }
  }

  # Remote state backend.
  # On first `terraform init`, Terraform will prompt you for these values:
  # - bucket: an S3 bucket you create for tfstate (example: ai-antfarm-tfstate-802539608101)
  # - key: path within the bucket (example: ai-antfarm/dev/terraform.tfstate)
  # - region: us-east-2
  # Optionally add a DynamoDB table for locking (recommended): ai-antfarm-tflock
  backend "s3" {}
}

provider "aws" {
  region = var.region
}

# CloudFront certificates MUST be in us-east-1.
provider "aws" {
  alias  = "use1"
  region = "us-east-1"
}

data "aws_caller_identity" "current" {}

data "aws_availability_zones" "available" {
  state = "available"
}

locals {
  name_prefix = "${var.project}-${var.env}"

  dynamodb_table_name = var.dynamodb_table_name != "" ? var.dynamodb_table_name : local.name_prefix

  api_fqdn = (var.domain_name != "" ? "${var.api_subdomain}.${var.domain_name}" : "")
  app_fqdn = (var.domain_name != "" ? "${var.app_subdomain}.${var.domain_name}" : "")

  # ECS env var expects comma-separated list
  cors_allowed_origins_csv = join(",", var.cors_allowed_origins)

  enable_custom_domain = (var.domain_name != "" && var.route53_zone_id != "")
}

# -----------------------------
# Networking (minimal VPC)
# -----------------------------
resource "aws_vpc" "main" {
  cidr_block           = "10.10.0.0/16"
  enable_dns_hostnames = true
  enable_dns_support   = true

  tags = {
    Name = "${local.name_prefix}-vpc"
  }
}

resource "aws_internet_gateway" "igw" {
  vpc_id = aws_vpc.main.id

  tags = {
    Name = "${local.name_prefix}-igw"
  }
}

# Two public subnets for the ALB
resource "aws_subnet" "public" {
  count                   = 2
  vpc_id                  = aws_vpc.main.id
  cidr_block              = cidrsubnet(aws_vpc.main.cidr_block, 8, count.index)
  availability_zone       = data.aws_availability_zones.available.names[count.index]
  map_public_ip_on_launch = true

  tags = {
    Name = "${local.name_prefix}-public-${count.index}"
  }
}

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.main.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.igw.id
  }

  tags = {
    Name = "${local.name_prefix}-public-rt"
  }
}

resource "aws_route_table_association" "public" {
  count          = 2
  subnet_id      = aws_subnet.public[count.index].id
  route_table_id = aws_route_table.public.id
}

# Private subnets for ECS tasks (no NAT gateway by default to keep idle cost low)
resource "aws_subnet" "private" {
  count                   = 2
  vpc_id                  = aws_vpc.main.id
  cidr_block              = cidrsubnet(aws_vpc.main.cidr_block, 8, count.index + 10)
  availability_zone       = data.aws_availability_zones.available.names[count.index]
  map_public_ip_on_launch = false

  tags = {
    Name = "${local.name_prefix}-private-${count.index}"
  }
}

resource "aws_route_table" "private" {
  vpc_id = aws_vpc.main.id

  tags = {
    Name = "${local.name_prefix}-private-rt"
  }
}

resource "aws_route_table_association" "private" {
  count          = 2
  subnet_id      = aws_subnet.private[count.index].id
  route_table_id = aws_route_table.private.id
}

# -----------------------------
# ECR (backend image)
# -----------------------------
resource "aws_ecr_repository" "backend" {
  name                 = "${local.name_prefix}-backend"
  image_tag_mutability = "MUTABLE"

  image_scanning_configuration {
    scan_on_push = true
  }

  tags = {
    Name = "${local.name_prefix}-backend"
  }
}

# -----------------------------
# CloudWatch logs (ECS)
# -----------------------------
resource "aws_cloudwatch_log_group" "backend" {
  name              = "/${local.name_prefix}/backend"
  retention_in_days = 14
}

# -----------------------------
# SSM Parameter Store (secrets/config)
# -----------------------------
# We create placeholders so Terraform owns the names.
# You will populate the SecureString values after apply.
resource "aws_ssm_parameter" "jwt_secret" {
  name        = "/${local.name_prefix}/APP_JWT_SECRET"
  description = "JWT signing secret for AiAntFarm backend"
  type        = "SecureString"
  value       = "CHANGE_ME_AFTER_APPLY"
  lifecycle {
    ignore_changes = [value]
  }
}

resource "aws_ssm_parameter" "openai_api_key" {
  name        = "/${local.name_prefix}/OPENAI_API_KEY"
  description = "OpenAI API key"
  type        = "SecureString"
  value       = "CHANGE_ME_AFTER_APPLY"
  lifecycle {
    ignore_changes = [value]
  }
}

resource "aws_ssm_parameter" "anthropic_api_key" {
  name        = "/${local.name_prefix}/ANTHROPIC_API_KEY"
  description = "Anthropic API key"
  type        = "SecureString"
  value       = "CHANGE_ME_AFTER_APPLY"
  lifecycle {
    ignore_changes = [value]
  }
}

# -----------------------------
# DynamoDB
# -----------------------------
# For your current setup, we default to using the existing table and do NOT manage it
# unless you explicitly enable create_dynamodb_table.

data "aws_dynamodb_table" "existing" {
  count = var.create_dynamodb_table ? 0 : 1
  name  = local.dynamodb_table_name
}

resource "aws_dynamodb_table" "main" {
  count        = var.create_dynamodb_table ? 1 : 0
  name         = local.dynamodb_table_name
  billing_mode = "PAY_PER_REQUEST"

  hash_key  = "pk"
  range_key = "sk"

  attribute {
    name = "pk"
    type = "S"
  }

  attribute {
    name = "sk"
    type = "S"
  }

  # TTL enabled in your existing table as ttlEpochSeconds
  ttl {
    attribute_name = "ttlEpochSeconds"
    enabled        = true
  }

  # GSIs inferred from your console screenshots
  # NOTE: AntRuns have been removed from the application; GSI_ANT_ID is no longer required.

  attribute {
    name = "emailGSI"
    type = "S"
  }

  attribute {
    name = "messageIdGSI"
    type = "S"
  }

  attribute {
    name = "createdByUserIdGSI"
    type = "S"
  }

  attribute {
    name = "nameGSI"
    type = "S"
  }

  attribute {
    name = "antIdGSI"
    type = "S"
  }

  attribute {
    name = "roomIdGSI"
    type = "S"
  }

  global_secondary_index {
    name            = "GSI_ANT_ID"
    hash_key        = "antIdGSI"
    projection_type = "ALL"
  }

  global_secondary_index {
    name            = "GSI_EMAIL"
    hash_key        = "emailGSI"
    projection_type = "ALL"
  }

  global_secondary_index {
    name            = "GSI_MESSAGE_ID"
    hash_key        = "messageIdGSI"
    projection_type = "ALL"
  }

  global_secondary_index {
    name            = "GSI_ROOM_CREATED_BY"
    hash_key        = "createdByUserIdGSI"
    projection_type = "ALL"
  }

  global_secondary_index {
    name            = "GSI_ROOM_ID"
    hash_key        = "roomIdGSI"
    projection_type = "ALL"
  }

  global_secondary_index {
    name            = "GSI_ROOM_NAME"
    hash_key        = "nameGSI"
    projection_type = "ALL"
  }

  # PITR is OFF in your current table; keep it off here for parity.
  point_in_time_recovery {
    enabled = false
  }

  tags = {
    Name = local.dynamodb_table_name
  }
}

# -----------------------------
# IAM (ECS task roles)
# -----------------------------
resource "aws_iam_role" "ecs_task_execution" {
  name = "${local.name_prefix}-ecs-exec"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Principal = { Service = "ecs-tasks.amazonaws.com" }
      Action = "sts:AssumeRole"
    }]
  })
}

resource "aws_iam_role_policy_attachment" "ecs_exec_policy" {
  role       = aws_iam_role.ecs_task_execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

# Allow the *execution* role (used during pull/startup) to read SSM SecureString params.
resource "aws_iam_role_policy" "ecs_exec_ssm" {
  name = "${local.name_prefix}-ecs-exec-ssm"
  role = aws_iam_role.ecs_task_execution.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = ["ssm:GetParameters", "ssm:GetParameter", "ssm:GetParametersByPath"]
        Resource = [
          aws_ssm_parameter.jwt_secret.arn,
          aws_ssm_parameter.openai_api_key.arn,
          aws_ssm_parameter.anthropic_api_key.arn
        ]
      },
      {
        Effect = "Allow"
        Action = ["kms:Decrypt"]
        Resource = "*"
      }
    ]
  })
}

resource "aws_iam_role" "ecs_task" {
  name = "${local.name_prefix}-ecs-task"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Principal = { Service = "ecs-tasks.amazonaws.com" }
      Action = "sts:AssumeRole"
    }]
  })
}

resource "aws_iam_policy" "ecs_task_policy" {
  name = "${local.name_prefix}-ecs-task-policy"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      # DynamoDB access
      {
        Effect = "Allow"
        Action = [
          "dynamodb:GetItem",
          "dynamodb:PutItem",
          "dynamodb:UpdateItem",
          "dynamodb:DeleteItem",
          "dynamodb:Query",
          "dynamodb:Scan"
        ]
        Resource = compact([
          var.create_dynamodb_table ? aws_dynamodb_table.main[0].arn : data.aws_dynamodb_table.existing[0].arn,
          var.create_dynamodb_table ? "${aws_dynamodb_table.main[0].arn}/index/*" : "${data.aws_dynamodb_table.existing[0].arn}/index/*"
        ])
      },
      # SSM params
      {
        Effect = "Allow"
        Action = ["ssm:GetParameter", "ssm:GetParameters", "ssm:GetParametersByPath"]
        Resource = [
          aws_ssm_parameter.jwt_secret.arn,
          aws_ssm_parameter.openai_api_key.arn,
          aws_ssm_parameter.anthropic_api_key.arn
        ]
      },
      # KMS decrypt for SecureString (AWS-managed SSM key)
      {
        Effect = "Allow"
        Action = ["kms:Decrypt"]
        Resource = "*"
      }
    ]
  })
}

resource "aws_iam_role_policy_attachment" "ecs_task_attach" {
  role       = aws_iam_role.ecs_task.name
  policy_arn = aws_iam_policy.ecs_task_policy.arn
}

# -----------------------------
# Security groups
# -----------------------------
resource "aws_security_group" "alb" {
  name        = "${local.name_prefix}-alb-sg"
  description = "ALB ingress"
  vpc_id      = aws_vpc.main.id

  ingress {
    description = "HTTP"
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    description = "HTTPS"
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "${local.name_prefix}-alb-sg"
  }
}

resource "aws_security_group" "ecs" {
  name        = "${local.name_prefix}-ecs-sg"
  description = "ECS tasks"
  vpc_id      = aws_vpc.main.id

  ingress {
    description     = "From ALB"
    from_port       = var.backend_container_port
    to_port         = var.backend_container_port
    protocol        = "tcp"
    security_groups = [aws_security_group.alb.id]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "${local.name_prefix}-ecs-sg"
  }
}

# -----------------------------
# VPC Endpoints (so private subnets can reach AWS APIs without NAT)
# -----------------------------

# Security group for interface endpoints (allow HTTPS from ECS tasks)
resource "aws_security_group" "vpc_endpoints" {
  name        = "${local.name_prefix}-vpce-sg"
  description = "Allow ECS tasks to reach VPC interface endpoints (HTTPS)"
  vpc_id      = aws_vpc.main.id

  ingress {
    description     = "HTTPS from ECS tasks"
    from_port       = 443
    to_port         = 443
    protocol        = "tcp"
    security_groups = [aws_security_group.ecs.id]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "${local.name_prefix}-vpce-sg"
  }
}

# SSM is required because ECS pulls SecureString env vars from Parameter Store before the container starts.
resource "aws_vpc_endpoint" "ssm" {
  vpc_id              = aws_vpc.main.id
  service_name        = "com.amazonaws.${var.region}.ssm"
  vpc_endpoint_type   = "Interface"
  subnet_ids          = aws_subnet.private[*].id
  private_dns_enabled = true
  security_group_ids  = [aws_security_group.vpc_endpoints.id]

  tags = {
    Name = "${local.name_prefix}-vpce-ssm"
  }
}

resource "aws_vpc_endpoint" "ssmmessages" {
  vpc_id              = aws_vpc.main.id
  service_name        = "com.amazonaws.${var.region}.ssmmessages"
  vpc_endpoint_type   = "Interface"
  subnet_ids          = aws_subnet.private[*].id
  private_dns_enabled = true
  security_group_ids  = [aws_security_group.vpc_endpoints.id]

  tags = {
    Name = "${local.name_prefix}-vpce-ssmmessages"
  }
}

resource "aws_vpc_endpoint" "ec2messages" {
  vpc_id              = aws_vpc.main.id
  service_name        = "com.amazonaws.${var.region}.ec2messages"
  vpc_endpoint_type   = "Interface"
  subnet_ids          = aws_subnet.private[*].id
  private_dns_enabled = true
  security_group_ids  = [aws_security_group.vpc_endpoints.id]

  tags = {
    Name = "${local.name_prefix}-vpce-ec2messages"
  }
}

# ECR endpoints let tasks pull images without NAT.
resource "aws_vpc_endpoint" "ecr_api" {
  vpc_id              = aws_vpc.main.id
  service_name        = "com.amazonaws.${var.region}.ecr.api"
  vpc_endpoint_type   = "Interface"
  subnet_ids          = aws_subnet.private[*].id
  private_dns_enabled = true
  security_group_ids  = [aws_security_group.vpc_endpoints.id]

  tags = {
    Name = "${local.name_prefix}-vpce-ecr-api"
  }
}

resource "aws_vpc_endpoint" "ecr_dkr" {
  vpc_id              = aws_vpc.main.id
  service_name        = "com.amazonaws.${var.region}.ecr.dkr"
  vpc_endpoint_type   = "Interface"
  subnet_ids          = aws_subnet.private[*].id
  private_dns_enabled = true
  security_group_ids  = [aws_security_group.vpc_endpoints.id]

  tags = {
    Name = "${local.name_prefix}-vpce-ecr-dkr"
  }
}

# Image layers are stored in S3; gateway endpoint keeps that traffic inside the VPC.
resource "aws_vpc_endpoint" "s3" {
  vpc_id            = aws_vpc.main.id
  service_name      = "com.amazonaws.${var.region}.s3"
  vpc_endpoint_type = "Gateway"

  route_table_ids = [aws_route_table.private.id]

  tags = {
    Name = "${local.name_prefix}-vpce-s3"
  }
}

# CloudWatch Logs endpoint helps with reliability (tasks can still log without NAT).
resource "aws_vpc_endpoint" "logs" {
  vpc_id              = aws_vpc.main.id
  service_name        = "com.amazonaws.${var.region}.logs"
  vpc_endpoint_type   = "Interface"
  subnet_ids          = aws_subnet.private[*].id
  private_dns_enabled = true
  security_group_ids  = [aws_security_group.vpc_endpoints.id]

  tags = {
    Name = "${local.name_prefix}-vpce-logs"
  }
}

# -----------------------------
# ALB + Target Group
# -----------------------------
resource "aws_lb" "api" {
  name               = substr("${local.name_prefix}-api", 0, 32)
  internal           = false
  load_balancer_type = "application"
  security_groups    = [aws_security_group.alb.id]
  subnets            = aws_subnet.public[*].id

  tags = {
    Name = "${local.name_prefix}-api-alb"
  }
}

resource "aws_lb_target_group" "api" {
  name        = substr("${local.name_prefix}-tg", 0, 32)
  port        = var.backend_container_port
  protocol    = "HTTP"
  target_type = "ip"
  vpc_id      = aws_vpc.main.id

  health_check {
    path                = "/actuator/health"
    healthy_threshold   = 2
    unhealthy_threshold = 3
    timeout             = 5
    interval            = 15
    matcher             = "200"
  }

  deregistration_delay = 10
}

# For now, we always create a regional ACM cert for the ALB using DNS validation
# if and only if you provided domain_name + route53_zone_id.
resource "aws_acm_certificate" "api" {
  count             = (var.domain_name != "" && var.route53_zone_id != "") ? 1 : 0
  domain_name       = local.api_fqdn
  validation_method = "DNS"
}

resource "aws_route53_record" "api_cert_validation" {
  count   = (var.domain_name != "" && var.route53_zone_id != "") ? length(aws_acm_certificate.api[0].domain_validation_options) : 0
  zone_id = var.route53_zone_id

  name    = aws_acm_certificate.api[0].domain_validation_options[count.index].resource_record_name
  type    = aws_acm_certificate.api[0].domain_validation_options[count.index].resource_record_type
  records = [aws_acm_certificate.api[0].domain_validation_options[count.index].resource_record_value]
  ttl     = 60
}

resource "aws_acm_certificate_validation" "api" {
  count           = (var.domain_name != "" && var.route53_zone_id != "") ? 1 : 0
  certificate_arn = aws_acm_certificate.api[0].arn
  validation_record_fqdns = aws_route53_record.api_cert_validation[*].fqdn
}

resource "aws_lb_listener" "http_forward" {
  count             = local.enable_custom_domain ? 0 : 1
  load_balancer_arn = aws_lb.api.arn
  port              = 80
  protocol          = "HTTP"

  default_action {
    type = "forward"

    forward {
      target_group {
        arn = aws_lb_target_group.api.arn
      }
    }
  }
}

resource "aws_lb_listener" "http_redirect" {
  count             = local.enable_custom_domain ? 1 : 0
  load_balancer_arn = aws_lb.api.arn
  port              = 80
  protocol          = "HTTP"

  default_action {
    type = "redirect"

    redirect {
      port        = "443"
      protocol    = "HTTPS"
      status_code = "HTTP_301"
    }
  }
}

resource "aws_lb_listener" "https" {
  count             = local.enable_custom_domain ? 1 : 0
  load_balancer_arn = aws_lb.api.arn
  port              = 443
  protocol          = "HTTPS"
  ssl_policy        = "ELBSecurityPolicy-TLS13-1-2-2021-06"
  certificate_arn   = aws_acm_certificate_validation.api[0].certificate_arn

  default_action {
    type = "forward"

    forward {
      target_group {
        arn = aws_lb_target_group.api.arn
      }
    }
  }

  depends_on = [aws_acm_certificate_validation.api]
}

resource "aws_route53_record" "api" {
  count   = (var.domain_name != "" && var.route53_zone_id != "") ? 1 : 0
  zone_id = var.route53_zone_id
  name    = var.api_subdomain
  type    = "A"

  alias {
    name                   = aws_lb.api.dns_name
    zone_id                = aws_lb.api.zone_id
    evaluate_target_health = true
  }
}

# -----------------------------
# ECS (cluster + service)
# -----------------------------
resource "aws_ecs_cluster" "main" {
  name = "${local.name_prefix}-cluster"
}

resource "aws_ecs_cluster_capacity_providers" "main" {
  cluster_name = aws_ecs_cluster.main.name

  capacity_providers = ["FARGATE", "FARGATE_SPOT"]

  default_capacity_provider_strategy {
    capacity_provider = "FARGATE_SPOT"
    weight            = 2
  }

  default_capacity_provider_strategy {
    capacity_provider = "FARGATE"
    weight            = 1
  }
}

locals {
  backend_image_effective = var.backend_image != "" ? var.backend_image : "public.ecr.aws/docker/library/nginx:alpine"
}

resource "aws_ecs_task_definition" "backend" {
  family                   = "${local.name_prefix}-backend"
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  cpu                      = "512"
  memory                   = "1024"

  execution_role_arn = aws_iam_role.ecs_task_execution.arn
  task_role_arn      = aws_iam_role.ecs_task.arn

  container_definitions = jsonencode([
    {
      name  = "backend"
      image = local.backend_image_effective

      essential = true

      portMappings = [
        {
          containerPort = var.backend_container_port
          protocol      = "tcp"
        }
      ]

      environment = [
        { name = "APP_CORS_ALLOWED_ORIGINS", value = local.cors_allowed_origins_csv },
        { name = "ANTFARM_DDB_TABLE", value = local.dynamodb_table_name },
        { name = "SPRING_PROFILES_ACTIVE", value = var.env }
      ]

      secrets = [
        { name = "APP_JWT_SECRET", valueFrom = aws_ssm_parameter.jwt_secret.arn },
        { name = "OPENAI_API_KEY", valueFrom = aws_ssm_parameter.openai_api_key.arn },
        { name = "ANTHROPIC_API_KEY", valueFrom = aws_ssm_parameter.anthropic_api_key.arn }
      ]

      logConfiguration = {
        logDriver = "awslogs"
        options = {
          awslogs-group         = aws_cloudwatch_log_group.backend.name
          awslogs-region        = var.region
          awslogs-stream-prefix = "ecs"
        }
      }

      healthCheck = {
        command     = ["CMD-SHELL", "wget -qO- http://localhost:${var.backend_container_port}/actuator/health | grep -q UP"]
        interval    = 30
        timeout     = 5
        retries     = 3
        startPeriod = 30
      }
    }
  ])
}

resource "aws_ecs_service" "backend" {
  name            = "${local.name_prefix}-backend"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.backend.arn
  desired_count   = var.backend_desired_count

  enable_execute_command = true

  capacity_provider_strategy {
    capacity_provider = "FARGATE_SPOT"
    weight            = 2
  }

  capacity_provider_strategy {
    capacity_provider = "FARGATE"
    weight            = 1
  }

  network_configuration {
    subnets         = aws_subnet.public[*].id
    security_groups = [aws_security_group.ecs.id]

    # Cheap/simple option: give tasks public IP so they can reach external AI APIs without NAT.
    # If you want private-only tasks, we’ll add NAT or a proxy.
    assign_public_ip = true
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.api.arn
    container_name   = "backend"
    container_port   = var.backend_container_port
  }

  depends_on = [aws_lb_listener.http_forward, aws_lb_listener.http_redirect]
}

# -----------------------------
# Frontend hosting (S3 + CloudFront)
# -----------------------------
resource "aws_s3_bucket" "frontend" {
  bucket        = "${local.name_prefix}-frontend"
  force_destroy = true
}

resource "aws_s3_bucket_public_access_block" "frontend" {
  bucket                  = aws_s3_bucket.frontend.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_cloudfront_origin_access_control" "frontend" {
  name                              = "${local.name_prefix}-frontend-oac"
  description                       = "OAC for frontend S3 origin"
  origin_access_control_origin_type = "s3"
  signing_behavior                  = "always"
  signing_protocol                  = "sigv4"
}

resource "aws_s3_bucket_policy" "frontend" {
  bucket = aws_s3_bucket.frontend.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid    = "AllowCloudFrontRead"
        Effect = "Allow"
        Principal = {
          Service = "cloudfront.amazonaws.com"
        }
        Action   = ["s3:GetObject"]
        Resource = ["${aws_s3_bucket.frontend.arn}/*"]
        Condition = {
          StringEquals = {
            "AWS:SourceArn" = aws_cloudfront_distribution.frontend.arn
          }
        }
      }
    ]
  })

  depends_on = [aws_cloudfront_distribution.frontend]
}

# CloudFront cert (optional) in us-east-1
resource "aws_acm_certificate" "frontend" {
  provider          = aws.use1
  count             = (var.domain_name != "" && var.route53_zone_id != "") ? 1 : 0
  domain_name       = local.app_fqdn
  validation_method = "DNS"
}

resource "aws_route53_record" "frontend_cert_validation" {
  count   = (var.domain_name != "" && var.route53_zone_id != "") ? length(aws_acm_certificate.frontend[0].domain_validation_options) : 0
  zone_id = var.route53_zone_id

  name    = aws_acm_certificate.frontend[0].domain_validation_options[count.index].resource_record_name
  type    = aws_acm_certificate.frontend[0].domain_validation_options[count.index].resource_record_type
  records = [aws_acm_certificate.frontend[0].domain_validation_options[count.index].resource_record_value]
  ttl     = 60
}

resource "aws_acm_certificate_validation" "frontend" {
  provider        = aws.use1
  count           = (var.domain_name != "" && var.route53_zone_id != "") ? 1 : 0
  certificate_arn = aws_acm_certificate.frontend[0].arn
  validation_record_fqdns = aws_route53_record.frontend_cert_validation[*].fqdn
}

resource "aws_cloudfront_distribution" "frontend" {
  enabled             = true
  default_root_object = "index.html"

  # Attach domain + cert only if provided.
  aliases = (var.domain_name != "" && var.route53_zone_id != "") ? [local.app_fqdn] : []

  origin {
    domain_name              = aws_s3_bucket.frontend.bucket_regional_domain_name
    origin_id                = "s3-frontend"
    origin_access_control_id = aws_cloudfront_origin_access_control.frontend.id
  }

  default_cache_behavior {
    target_origin_id       = "s3-frontend"
    viewer_protocol_policy = "redirect-to-https"

    allowed_methods  = ["GET", "HEAD", "OPTIONS"]
    cached_methods   = ["GET", "HEAD", "OPTIONS"]
    compress         = true

    forwarded_values {
      query_string = false
      cookies {
        forward = "none"
      }
    }
  }

  # SPA routing: send 403/404 to index.html
  custom_error_response {
    error_code            = 403
    response_code         = 200
    response_page_path    = "/index.html"
    error_caching_min_ttl = 0
  }

  custom_error_response {
    error_code            = 404
    response_code         = 200
    response_page_path    = "/index.html"
    error_caching_min_ttl = 0
  }

  restrictions {
    geo_restriction {
      restriction_type = "none"
    }
  }

  viewer_certificate {
    cloudfront_default_certificate = (var.domain_name == "" || var.route53_zone_id == "")
    acm_certificate_arn            = (var.domain_name != "" && var.route53_zone_id != "") ? aws_acm_certificate_validation.frontend[0].certificate_arn : null
    ssl_support_method             = (var.domain_name != "" && var.route53_zone_id != "") ? "sni-only" : null
    minimum_protocol_version       = "TLSv1.2_2021"
  }

  depends_on = [aws_acm_certificate_validation.frontend]
}

resource "aws_route53_record" "frontend" {
  count   = (var.domain_name != "" && var.route53_zone_id != "") ? 1 : 0
  zone_id = var.route53_zone_id
  name    = var.app_subdomain
  type    = "A"

  alias {
    name                   = aws_cloudfront_distribution.frontend.domain_name
    zone_id                = aws_cloudfront_distribution.frontend.hosted_zone_id
    evaluate_target_health = false
  }
}

locals {
  # CloudFront function runs on viewer-response to inject headers.
}

# -----------------------------
# Backend API edge (CloudFront -> ALB)
# -----------------------------

resource "aws_cloudfront_function" "api_cors" {
  name    = "${local.name_prefix}-api-cors"
  runtime = "cloudfront-js-1.0"
  comment = "Inject CORS headers for /api/* responses"
  publish = true

  code = <<EOT
function handler(event) {
  var response = event.response;
  var request = event.request;
  var headers = response.headers;

  var originHeader = request.headers.origin;
  var origin = originHeader && originHeader.value ? originHeader.value : "";

  // Allowed origins are injected at deploy time. If empty, allow all (dev convenience).
  var allowed = "${local.cors_allowed_origins_csv}";
  var allowOrigin = "";

  if (!allowed || allowed.length === 0) {
    allowOrigin = origin;
  } else {
    var allowedList = allowed.split(',');
    for (var i = 0; i < allowedList.length; i++) {
      if (allowedList[i] === origin) {
        allowOrigin = origin;
        break;
      }
    }
  }

  if (allowOrigin) {
    headers['access-control-allow-origin'] = { value: allowOrigin };
    headers['access-control-allow-credentials'] = { value: 'true' };
    headers['access-control-allow-methods'] = { value: 'GET,POST,PUT,PATCH,DELETE,OPTIONS' };
    headers['access-control-allow-headers'] = { value: 'authorization,content-type' };
    headers['vary'] = { value: 'origin' };
  }

  // Helpful for debugging
  headers['x-aiantfarm-edge'] = { value: 'cf-api' };
  return response;
}
EOT
}

resource "aws_cloudfront_distribution" "api" {
  enabled = true

  origin {
    domain_name = aws_lb.api.dns_name
    origin_id   = "alb-backend"

    custom_origin_config {
      http_port              = 80
      https_port             = 443
      origin_protocol_policy = "http-only" # ALB is HTTP-only for now
      origin_ssl_protocols   = ["TLSv1.2"]
    }
  }

  default_cache_behavior {
    target_origin_id       = "alb-backend"
    viewer_protocol_policy = "redirect-to-https"

    allowed_methods = ["GET", "HEAD", "OPTIONS", "PUT", "POST", "PATCH", "DELETE"]
    cached_methods  = ["GET", "HEAD", "OPTIONS"]

    compress = true

    # No caching for API (especially OPTIONS preflight)
    min_ttl     = 0
    default_ttl = 0
    max_ttl     = 0

    forwarded_values {
      query_string = true

      # IMPORTANT: include preflight and Origin headers so CORS can work.
      headers = [
        "Authorization",
        "Origin",
        "Content-Type",
        "Accept",
        "Access-Control-Request-Method",
        "Access-Control-Request-Headers"
      ]

      cookies {
        forward = "all"
      }
    }

    function_association {
      event_type   = "viewer-response"
      function_arn = aws_cloudfront_function.api_cors.arn
    }
  }

  restrictions {
    geo_restriction {
      restriction_type = "none"
    }
  }

  viewer_certificate {
    cloudfront_default_certificate = true
    minimum_protocol_version       = "TLSv1.2_2021"
  }
}

# When the frontend_api_base_url variable is set, write it to S3 so the app can read it at runtime.
resource "aws_s3_object" "frontend_runtime_config" {
  bucket       = aws_s3_bucket.frontend.id
  key          = "config.json"
  content_type = "application/json"
  cache_control = "no-store"

  content = jsonencode({
    apiBaseUrl = var.frontend_api_base_url != "" ? var.frontend_api_base_url : ""
  })
}
