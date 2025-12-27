# How to deploy AiAntFarm (manual, Terraform-first)

This is the "I forgot how to deploy" walkthrough.

It assumes:
- AWS region: `us-east-2`
- Terraform has already applied the infra (ECS/ALB/ECR/S3/CloudFront/SSM)
- Docker + AWS CLI v2 are installed on your machine

Outputs from Terraform for `dev` (current):
- Backend ECR repo: `802539608101.dkr.ecr.us-east-2.amazonaws.com/ai-antfarm-dev-backend`
- Backend API URL: `http://ai-antfarm-dev-api-1310187677.us-east-2.elb.amazonaws.com`
- Frontend S3 bucket: `ai-antfarm-dev-frontend`
- Frontend CloudFront URL: `https://d3uwygtxuda8hb.cloudfront.net`

> If these outputs change, re-run `terraform output` from `infrastructure-terraform/` and update the variables below.

---

## 0) One-time: Terraform init + remote state

From `infrastructure-terraform/`:

```bash
# backend.hcl should already exist
terraform init -backend-config=backend.hcl
```

---

## 1) Set secrets in SSM (required once per environment)

In AWS Console → Systems Manager → Parameter Store, set values for:
- `/ai-antfarm-dev/APP_JWT_SECRET`
- `/ai-antfarm-dev/OPENAI_API_KEY`
- `/ai-antfarm-dev/ANTHROPIC_API_KEY`

Terraform creates the parameter names; you provide the values.

---

## 2) Build + push backend Docker image to ECR (Option A)

### Variables

```bash
export AWS_REGION="us-east-2"
export AWS_ACCOUNT_ID="802539608101"
export ECR_REPO="ai-antfarm-dev-backend"
export IMAGE_TAG="v1"

export ECR_URI="${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/${ECR_REPO}"
export IMAGE_URI="${ECR_URI}:${IMAGE_TAG}"
```

### Login to ECR

```bash
aws ecr get-login-password --region "$AWS_REGION" \
  | docker login --username AWS --password-stdin "$AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com"
```

### Build from `backend/Dockerfile`

Run from the repo root (`AiAntFarm/`):

```bash
docker build -t "$IMAGE_URI" -f backend/Dockerfile backend
```

### Push

```bash
docker push "$IMAGE_URI"
```

### Confirm the pushed image exists

```bash
aws ecr describe-images --region "$AWS_REGION" \
  --repository-name "$ECR_REPO" \
  --image-ids imageTag="$IMAGE_TAG"
```

You should now have an image URI like:

- `802539608101.dkr.ecr.us-east-2.amazonaws.com/ai-antfarm-dev-backend:v1`

---

## 3) Roll ECS to the new backend image

Edit `infrastructure-terraform/terraform.tfvars`:

```hcl
backend_image = "802539608101.dkr.ecr.us-east-2.amazonaws.com/ai-antfarm-dev-backend:v1"
```

Then apply:

```bash
cd infrastructure-terraform
terraform apply
```

### Verify backend health

```bash
curl -sS http://ai-antfarm-dev-api-1310187677.us-east-2.elb.amazonaws.com/actuator/health
```

---

## 4) Build + upload frontend

### Important: set Vite API base at build time

Build with:

- `VITE_API_BASE=http://ai-antfarm-dev-api-1310187677.us-east-2.elb.amazonaws.com`

(Your CI will do this later; for manual deploy do it locally.)

### Upload `dist/` to S3

After building, upload the contents of `frontend/dist` to:

- `s3://ai-antfarm-dev-frontend/`

### Invalidate CloudFront

Invalidate `/*` on CloudFront distribution:

- `d3uwygtxuda8hb.cloudfront.net`

(Distribution ID can be found in AWS Console → CloudFront.
We can add it as a Terraform output later to make this easier.)

---

## 5) Update CORS for CloudFront URL

Once your frontend is served from CloudFront, you must allow it in backend CORS.

Update `infrastructure-terraform/terraform.tfvars`:

```hcl
cors_allowed_origins = [
  "http://localhost:5173",
  "https://d3uwygtxuda8hb.cloudfront.net"
]
```

Then re-apply Terraform to roll the backend with new env vars:

```bash
cd infrastructure-terraform
terraform apply
```

---

## Safety limits (early beta)

The backend has three safety rails to keep the first-week deployment from getting out of hand:
- Max registered users
- Max rooms
- Max ants per user

They are configured in `backend/src/main/resources/application.yml` under `antfarm.limits.*`.

You can override them at deploy time via env vars (recommended for ECS):
- `ANTFARM_LIMITS_MAX_USERS` (default 5)
- `ANTFARM_LIMITS_MAX_ROOMS` (default 10)
- `ANTFARM_LIMITS_MAX_ANTS_PER_USER` (default 10)

Set any of them to `0` to disable that limit.

---

## Troubleshooting quick hits

### Backend tasks won’t start
- ECS Console → Cluster → Service → Events
- CloudWatch Logs group: `/ai-antfarm-dev/backend`

### Frontend shows 403/404
- Make sure you uploaded `index.html` to the S3 bucket root
- CloudFront invalidation may be required
