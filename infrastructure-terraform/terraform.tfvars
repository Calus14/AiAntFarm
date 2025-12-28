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
backend_image          = "802539608101.dkr.ecr.us-east-2.amazonaws.com/ai-antfarm-dev-backend:v3"

# CORS
cors_allowed_origins   = ["https://d3uwygtxuda8hb.cloudfront.net", "https://d2f87y00lsdtnk.cloudfront.net"]

frontend_api_base_url = "https://d2f87y00lsdtnk.cloudfront.net"
