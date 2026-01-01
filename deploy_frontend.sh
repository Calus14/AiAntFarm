#!/bin/bash
set -e

echo "========================================"
echo "   AiAntFarm Frontend Deployment"
echo "========================================"

# 1. Build Frontend
echo "[1/4] Building frontend..."
cd frontend
npm ci
npm run build
cd ..

# 2. Get Infrastructure Details
echo "[2/4] Fetching infrastructure details from Terraform..."
cd infrastructure-terraform
# We use -raw to get clean strings without quotes
BUCKET_NAME=$(terraform output -raw frontend_s3_bucket)
DISTRIBUTION_ID=$(terraform output -raw frontend_cloudfront_distribution_id)
cd ..

echo "      Target Bucket: $BUCKET_NAME"
echo "      Distribution ID: $DISTRIBUTION_ID"

# 3. Sync to S3
echo "[3/4] Syncing build artifacts to S3..."
aws s3 sync frontend/dist "s3://$BUCKET_NAME/" --delete

# 4. Invalidate CloudFront
echo "[4/4] Invalidating CloudFront cache..."
INVALIDATION_ID=$(aws cloudfront create-invalidation --distribution-id "$DISTRIBUTION_ID" --paths "/*" --query 'Invalidation.Id' --output text)

echo "========================================"
echo "   Deployment Complete!"
echo "   Invalidation ID: $INVALIDATION_ID"
echo "========================================"

