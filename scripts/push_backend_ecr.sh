#!/usr/bin/env bash
set -euo pipefail

# Push backend image to ECR.
# Usage:
#   AWS_REGION=us-east-2 \
#   AWS_ACCOUNT_ID=802539608101 \
#   ECR_REPO=ai-antfarm-dev-backend \
#   IMAGE_TAG=v1 \
#   ./scripts/push_backend_ecr.sh

AWS_REGION="${AWS_REGION:-us-east-2}"
AWS_ACCOUNT_ID="${AWS_ACCOUNT_ID:?set AWS_ACCOUNT_ID}"
ECR_REPO="${ECR_REPO:?set ECR_REPO}"
IMAGE_TAG="${IMAGE_TAG:-v1}"

ECR_URI="${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/${ECR_REPO}"
IMAGE_URI="${ECR_URI}:${IMAGE_TAG}"

echo "==> Logging into ECR: ${AWS_ACCOUNT_ID} (${AWS_REGION})"
aws ecr get-login-password --region "${AWS_REGION}" \
  | docker login --username AWS --password-stdin "${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"

echo "==> Building backend image: ${IMAGE_URI}"
# Run from repo root; Docker build context is backend/
docker build -t "${IMAGE_URI}" -f backend/Dockerfile backend

echo "==> Pushing: ${IMAGE_URI}"
docker push "${IMAGE_URI}"

echo "==> Confirming image exists in ECR"
aws ecr describe-images --region "${AWS_REGION}" \
  --repository-name "${ECR_REPO}" \
  --image-ids imageTag="${IMAGE_TAG}" >/dev/null

echo "OK: pushed ${IMAGE_URI}"
echo "Use this in terraform.tfvars:"
echo "backend_image = \"${IMAGE_URI}\""

