# AI Ant Farm — Terraform

This folder will manage **all AWS infrastructure** for AiAntFarm.

## Target architecture (side-project friendly)

We’re aiming for a setup that is:
- cheap when idle
- easy to reason about
- supports long-lived connections (SSE)
- fully reproducible via Terraform

### Current plan: ECS Fargate (Spot) + ALB + CloudFront

**Frontend**
- S3 bucket for SPA static assets (`frontend/dist`)
- CloudFront distribution for HTTPS + caching + custom domain (`app.<domain>`)

**Backend**
- ECS Cluster
- ECS Service running the Spring Boot container on **Fargate Spot**
- Application Load Balancer (ALB) for stable ingress + health checks
  - ALB target group uses health check path (typically `/actuator/health`)

**API**
- DNS `api.<domain>` → ALB
- DNS `app.<domain>` → CloudFront

**Data + Secrets**
- DynamoDB single-table design (PK/SK)
- SSM Parameter Store for:
  - JWT secret
  - AI provider API keys
  - other runtime config

**Observability**
- CloudWatch logs for ECS task
- Alarm hooks later (SNS/email)

## What’s in the repo right now

The Terraform files are currently a skeleton:
- `main.tf` sets provider + remote state backend placeholder (`backend "s3" {}`)
- `variables.tf` defines minimal vars

We’ll expand this into real resources/modules as we implement the architecture.

## Manual Terraform workflow

### 1) Remote state (recommended)
Before you do real work, set up remote state:
- S3 bucket for `tfstate`
- DynamoDB table for state locking

Then configure your backend (either via `-backend-config` args or a `backend.hcl` file).

### 2) The safe plan/apply flow
```powershell
cd infrastructure-terraform
terraform init
terraform plan
terraform apply
```

Even safer:
```powershell
terraform plan -out tfplan
terraform apply tfplan
```

## Notes / gotchas

- CloudFront certs must be in **us-east-1** (ACM) even if your main region is elsewhere.
- For a Vite SPA, `VITE_API_BASE` is build-time. CI needs to set it during `npm run build`.
