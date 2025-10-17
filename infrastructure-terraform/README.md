# AI Ant Farm — Terraform Skeleton

This is a scaffold for:
- VPC (2 AZ, public/private)
- ALB + WAF
- EC2 ASG for backend
- S3 + CloudFront for frontend (later)
- DynamoDB table (single-table)
- IAM roles for EC2 + deploy
- Terraform backend (S3 + DynamoDB lock)

**Note:** Variables are placeholders. Do `terraform init` only after setting backend and providers.
