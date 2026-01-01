output "notes" {
  value = "Terraform deploys: VPC, ALB, ECS(Fargate Spot), ECR, S3+CloudFront, SSM params. DynamoDB uses existing table unless create_dynamodb_table=true."
}

output "backend_ecr_repository_url" {
  value = aws_ecr_repository.backend.repository_url
}

output "backend_alb_dns_name" {
  value = aws_lb.api.dns_name
}

output "backend_api_url" {
  value = (var.domain_name != "" && var.route53_zone_id != "") ? "https://${var.api_subdomain}.${var.domain_name}" : "http://${aws_lb.api.dns_name}"
}

output "frontend_s3_bucket" {
  value = aws_s3_bucket.frontend.bucket
}

output "frontend_cloudfront_domain" {
  value = aws_cloudfront_distribution.frontend.domain_name
}

output "frontend_app_url" {
  value = (var.domain_name != "" && var.route53_zone_id != "") ? "https://${var.domain_name}" : "https://${aws_cloudfront_distribution.frontend.domain_name}"
}

output "ssm_parameter_names" {
  value = {
    APP_JWT_SECRET    = aws_ssm_parameter.jwt_secret.name
    OPENAI_API_KEY    = aws_ssm_parameter.openai_api_key.name
    ANTHROPIC_API_KEY = aws_ssm_parameter.anthropic_api_key.name
  }
}

output "dynamodb_table_name" {
  value = local.dynamodb_table_name
}

output "backend_api_cloudfront_domain" {
  description = "CloudFront domain for the backend API edge distribution (HTTPS)."
  value       = aws_cloudfront_distribution.api.domain_name
}

output "backend_api_cloudfront_url" {
  description = "Full base URL for backend API edge distribution (HTTPS)."
  value       = "https://${aws_cloudfront_distribution.api.domain_name}"
}

output "frontend_cloudfront_distribution_id" {
  description = "CloudFront distribution ID for the frontend (use for invalidations)."
  value       = aws_cloudfront_distribution.frontend.id
}

output "backend_api_cloudfront_distribution_id" {
  description = "CloudFront distribution ID for the backend API edge distribution (use for invalidations)."
  value       = aws_cloudfront_distribution.api.id
}

output "ses_identity_arn" {
  description = "ARN of the verified SES identity"
  value       = length(aws_ses_email_identity.main) > 0 ? aws_ses_email_identity.main[0].arn : ""
}

output "cf_logs_bucket" {
  description = "S3 bucket for CloudFront logs"
  value       = aws_s3_bucket.cf_logs.bucket
}

output "frontend_distribution_id" {
  description = "CloudFront Distribution ID for frontend"
  value       = aws_cloudfront_distribution.frontend.id
}
