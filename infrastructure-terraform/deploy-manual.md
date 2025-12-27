# Manual deployment checklist (Terraform-first)

This doc is written for the workflow you asked for:
- you run Terraform manually
- you decide when infra changes apply
- you can still deploy app code (backend container + frontend static assets) when you want

## What Terraform will create
- VPC + subnets
- ALB (public) forwarding to ECS service
- ECS cluster + service (Fargate Spot preferred)
- ECR repo for backend image
- S3 bucket + CloudFront distro for frontend
- DynamoDB (optional create)
- SSM Parameter Store parameter *names* for secrets (placeholders)

## One-time prerequisites you must provide

### 1) AWS credentials
Terraform needs AWS credentials and permissions to create infrastructure.

### 2) (Optional but recommended) Terraform remote state
- S3 bucket for state
- DynamoDB table for lock

### 3) Backend container image
Terraform creates an ECR repo, but **it cannot build/push your Docker image by itself**.
You will push your backend image to the ECR repo and then point Terraform at it via `backend_image`.

### 4) Secrets
Terraform creates SSM parameters with placeholder values.
You’ll update these in AWS after the first apply:
- `APP_JWT_SECRET`
- `OPENAI_API_KEY`
- `ANTHROPIC_API_KEY`

## Suggested rollout sequence

### Phase 1 — Apply infra without custom domain
Goal: get *something* reachable quickly.

Create a `terraform.tfvars` in `infrastructure-terraform/` (example):

```hcl
region                 = "us-east-2"
project                = "ai-antfarm"
env                    = "dev"

# No domain yet
domain_name            = ""
route53_zone_id        = ""

# Use existing DynamoDB table
create_dynamodb_table  = false
dynamodb_table_name    = "AiAntFarmTable"

# Backend image will be set after you push to ECR
backend_image          = ""

# CORS: allow local dev now; we’ll add CloudFront later
cors_allowed_origins   = ["http://localhost:5173"]
}
```

Result:
- Backend reachable via ALB DNS name (HTTP)
- Frontend reachable via CloudFront distribution domain

### Phase 2 — Push backend image
1. Build container locally (`backend/Dockerfile` exists)
2. Push to the Terraform-created ECR repo
3. Update `backend_image` in `terraform.tfvars` and re-apply

### Phase 3 — Upload frontend assets
1. Build frontend (with correct `VITE_API_BASE`)
2. Upload the `dist/` directory to the Terraform-created S3 bucket
3. Invalidate CloudFront cache

### Phase 4 — Add a domain
When domain + hosted zone are ready:
- Set `domain_name` and `route53_zone_id`
- Terraform will request certificates + create DNS records

## Fix for HTTPS frontend -> HTTP backend (mixed content)

When the frontend is served from CloudFront (HTTPS) and the backend is only available as an HTTP ALB URL, browsers will block requests as **mixed active content**.

Terraform now creates a second CloudFront distribution in front of the backend ALB (an HTTPS "API edge").

### What to do

1) Deploy/apply Terraform (this creates the API edge distribution)
2) Grab the output:
   - `backend_api_cloudfront_url` (example: `https://xxxx.cloudfront.net`)
3) Set in `terraform.tfvars`:

```hcl
# Make the SPA call the HTTPS API edge
frontend_api_base_url = "https://<backend_api_cloudfront_domain>"
```

4) `terraform apply`
5) Re-upload your frontend build to S3 and invalidate CloudFront.

The frontend loads `/config.json` at runtime and uses `apiBaseUrl` from that file, so you don’t need to bake the API URL into the JS bundle.

## Info I still need from you to get you fully deployed

1) Confirmed: Region is **us-east-2**.

2) Confirmed: DynamoDB is the existing table:
- Table name: **AiAntFarmTable**
- PK/SK: `pk` / `sk` (String)
- TTL: enabled, attribute **ttlEpochSeconds**
- Billing: on-demand
- PITR: off

3) Domain is deferred until after the first deploy.

4) Next required output from you (after Phase 1 apply)
Paste the Terraform outputs:
- `backend_ecr_repository_url`
- `backend_api_url`
- `frontend_s3_bucket`
- `frontend_app_url`

Once you paste those, I’ll give you the *exact* next instructions for pushing the backend image, setting SSM secrets, and uploading the frontend so it’s actually usable.
