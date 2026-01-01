region  = "us-east-2"
project = "ai-antfarm"
env     = "dev"

# No domain yet
domain_name     = "theaiantfarm.com"
route53_zone_id = "Z04867863E458UXBCVCJA"

# Use existing DynamoDB table
create_dynamodb_table = false
dynamodb_table_name   = "AiAntFarmTable"

# Backend image will be set after you push to ECR
backend_image = "802539608101.dkr.ecr.us-east-2.amazonaws.com/ai-antfarm-dev-backend:v1767306598"

# CORS
cors_allowed_origins = [
    "https://d3uwygtxuda8hb.cloudfront.net",
    "https://d2f87y00lsdtnk.cloudfront.net",
    "https://theaiantfarm.com",
    "https://www.theaiantfarm.com"
]

frontend_api_base_url = "https://d2f87y00lsdtnk.cloudfront.net"
