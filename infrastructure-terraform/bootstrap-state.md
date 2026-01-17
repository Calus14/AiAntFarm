# Terraform remote state bootstrap (what to enter at `terraform init`)

Your Terraform config uses:

```hcl
terraform {
  backend "s3" {}
}
```

That means **Terraform needs you to tell it where remote state lives** when you run `terraform init`.

## Option A (recommended): use remote state (S3 + DynamoDB lock)

### What you need to create first (one-time)
Create these in **us-east-2**:

1) **S3 bucket** for Terraform state
- Example name: `ai-antfarm-tfstate-<your-account-id>`
- Must be globally unique.

2) **DynamoDB table** for state locking
- Example name: `ai-antfarm-tflock`
- Partition key: `LockID` (String)

> If you want Terraform to create these too, do it as a separate "bootstrap" stack (best practice).

### What to type at `terraform init`
When it prompts:

- **bucket**: the S3 bucket you created for tfstate
  - Example: `ai-antfarm-tfstate-802539608101`

- **key**: a path within the bucket for this stack’s state file
  - Example: `ai-antfarm/dev/terraform.tfstate`

- **region**: `us-east-2`

- **dynamodb_table** (if prompted / used by your init config):
  - Example: `ai-antfarm-tflock`

- **encrypt**: `true`

## Option B (fastest to unblock): local state

If you don’t want remote state yet, you can temporarily remove the S3 backend stanza or provide backend config that points to a bucket.

I *don’t* recommend this long-term.

## Suggested concrete values for you

Given your AWS account looks like `8025...` from screenshots, do:

- bucket: `ai-antfarm-tfstate-802539608101`
- key: `ai-antfarm/dev/terraform.tfstate`
- region: `us-east-2`
- dynamodb_table: `ai-antfarm-tflock`

If you already created `AiAntFarmS3` as a bucket name, note:
- S3 bucket names must be globally unique.
- `AiAntFarmS3` often fails if someone else already has it.

## Common error: "S3 bucket ... does not exist"

Terraform **cannot create the state bucket** as part of this stack, because it needs the bucket *before* it can even initialize.

If you see:
- `NoSuchBucket` / `S3 bucket ... does not exist`

Then you must first create the bucket in AWS (Console is fine):
1) Go to **S3 → Create bucket**
2) Name: the exact bucket name you referenced in `backend.hcl`
3) Region: **us-east-2**
4) Enable:
   - Block all public access
   - Bucket Versioning (recommended)
   - Default encryption (recommended)

After the bucket exists, re-run `terraform init -backend-config=backend.hcl`.

Optionally, for state locking:
1) Go to **DynamoDB → Create table**
2) Table name: `ai-antfarm-tflock`
3) Partition key: `LockID` (String)
4) Billing: on-demand

Then add to `backend.hcl`:
```hcl
dynamodb_table = "ai-antfarm-tflock"
encrypt        = true
```

## I can generate a ready-to-copy backend config file

If you tell me:
- your chosen tfstate bucket name
- your chosen lock table name

…I’ll generate a `backend.hcl` file you can use like:

```powershell
terraform init -backend-config=backend.hcl
```
