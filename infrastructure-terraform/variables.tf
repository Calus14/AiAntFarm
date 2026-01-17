variable "region" { type = string }
variable "project" { type = string }
variable "env" { type = string }

# --- Domain / DNS (optional for first apply) ---
variable "domain_name" {
  type        = string
  description = "Root domain name (e.g., example.com). If empty, Terraform will skip Route53/ACM/CloudFront custom domain wiring."
  default     = ""
}

variable "route53_zone_id" {
  type        = string
  description = "Route53 Hosted Zone ID for domain_name. If empty, Terraform will not create DNS records."
  default     = ""
}

variable "app_subdomain" {
  type        = string
  description = "Subdomain for the frontend (CloudFront)."
  default     = "app"
}

variable "api_subdomain" {
  type        = string
  description = "Subdomain for the backend API (ALB)."
  default     = "api"
}

# --- Backend container settings ---
variable "backend_container_port" {
  type    = number
  default = 9000
}

variable "backend_desired_count" {
  type        = number
  description = "Desired number of ECS tasks. Set to 0 to scale-to-zero when idle (note: requires no active connections)."
  default     = 1
}

variable "backend_image" {
  type        = string
  description = "Container image URI for the backend (e.g., <account>.dkr.ecr.<region>.amazonaws.com/aiantfarm-backend:latest)."
  default     = ""
}

# --- DynamoDB ---
variable "dynamodb_table_name" {
  type        = string
  description = "DynamoDB table name. Defaults to <project>-<env>."
  default     = ""
}

# Whether to create a new DynamoDB table (true) or expect an existing one (false).
variable "create_dynamodb_table" {
  type    = bool
  default = true
}

# --- CORS ---
variable "cors_allowed_origins" {
  type        = list(string)
  description = "Origins to allow for CORS. Used to set APP_CORS_ALLOWED_ORIGINS env var in ECS task."
  default     = ["http://localhost:5173"]
}

variable "frontend_api_base_url" {
  description = "Public base URL for the backend API that the frontend should call (ex: https://api.example.com). If empty, the build must provide VITE_API_BASE manually."
  type        = string
  default     = ""
}

# --- Email configuration ---
variable "email_provider" {
  description = "Email provider (ses or smtp)"
  type        = string
  default     = "smtp"
}

variable "smtp_host" {
  description = "SMTP host"
  type        = string
  default     = "localhost"
}

variable "smtp_port" {
  description = "SMTP port"
  type        = string
  default     = "1025"
}

variable "smtp_user" {
  description = "SMTP username"
  type        = string
  default     = ""
}

variable "smtp_pass" {
  description = "SMTP password"
  type        = string
  default     = ""
  sensitive   = true
}

variable "jwt_reset_secret" {
  description = "Secret for signing reset tokens"
  type        = string
  sensitive   = true
  default     = "change-me-reset-secret-must-be-long-enough"
}

variable "ses_email_from" {
  description = "Email address to verify in SES and use as the 'From' address"
  type        = string
  default     = "noreply@theaiantfarm.com"
}
