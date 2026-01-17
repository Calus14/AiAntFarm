#!/bin/bash
set -e

echo "========================================"
echo "   AiAntFarm Backend Deployment"
echo "========================================"

# Configuration
AWS_REGION="${AWS_REGION:-us-east-2}"
export AWS_REGION
export AWS_DEFAULT_REGION="$AWS_REGION"
IMAGE_TAG="v$(date +%s)"
export IMAGE_TAG

# 1. Get Infrastructure Details
echo "[1/5] Fetching infrastructure details from Terraform..."
cd infrastructure-terraform
ECR_REPO_URL=$(terraform output -raw backend_ecr_repository_url)
cd ..

if [ -z "$ECR_REPO_URL" ]; then
  echo "Error: Could not fetch backend_ecr_repository_url from Terraform outputs."
  exit 1
fi

# Extract registry domain (e.g., 123456789012.dkr.ecr.us-east-2.amazonaws.com)
REGISTRY_DOMAIN=$(echo "$ECR_REPO_URL" | cut -d'/' -f1)
FULL_IMAGE_URI="$ECR_REPO_URL:$IMAGE_TAG"

echo "      ECR Repo: $ECR_REPO_URL"
echo "      New Tag:  $IMAGE_TAG"

# 2. Login to ECR
echo "[2/5] Logging in to ECR..."
aws ecr get-login-password --region "$AWS_REGION" | docker login --username AWS --password-stdin "$REGISTRY_DOMAIN"

# 3. Build Docker Image
echo "[3/5] Building backend Docker image..."
# Run from repo root
docker build -t "$FULL_IMAGE_URI" -f backend/Dockerfile backend

# 4. Push to ECR
echo "[4/5] Pushing image to ECR..."
docker push "$FULL_IMAGE_URI"

# 5. Deploy to ECS via Terraform
echo "[5/5] Updating ECS service via Terraform..."
cd infrastructure-terraform

# Update terraform.tfvars so subsequent manual applies don't revert to an old image
# Use | as delimiter because the URI contains /
sed -i "s|backend_image = \".*\"|backend_image = \"$FULL_IMAGE_URI\"|" terraform.tfvars

# -auto-approve skips the "yes" prompt
terraform apply -auto-approve

echo "========================================"
echo "   Backend Deployment Complete!"
echo "   Image: $FULL_IMAGE_URI"
echo "========================================"

