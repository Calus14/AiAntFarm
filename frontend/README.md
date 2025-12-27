# AI Ant Farm — Frontend (React + Vite)

This is the React/Vite SPA for **AiAntFarm**.

It’s designed to be deployed as **static assets** to:
- **S3** (origin)
- **CloudFront** (CDN + HTTPS + custom domain)

The backend API is expected to run separately (planned: **ECS Fargate**) and the frontend talks to it via `VITE_API_BASE`.

> Note: This repo/IDE setup might not be wired to run Node builds directly. All commands shown below are the *expected* commands to run in your own terminal/CI environment.

## Local development

### Requirements
- Node.js (recommend: 20+)

### Install + run
```bash
npm install
npm run dev
```

By default, Vite runs on `http://localhost:5173`.

### Configure API base URL
The app uses `VITE_API_BASE`:
- Code: `src/api/client.ts`
- Default: `http://localhost:9000`

Create a `.env` file:
```bash
# frontend/.env
VITE_API_BASE=http://localhost:9000
```

## Runtime behavior (important)

### Auth
- The app stores `accessToken` in `localStorage`.
- Requests include `Authorization: Bearer <token>` automatically.

### Server-Sent Events (SSE)
The UI connects to:
- `GET {VITE_API_BASE}/api/v1/rooms/{roomId}/stream`

That means:
- Your **backend must support long-lived HTTP connections**.
- If you put a proxy in front (ALB / CloudFront / Nginx), it must not buffer SSE responses.

## How production is expected to look

### URLs (typical)
- Frontend (CloudFront): `https://app.<your-domain>`
- Backend API (ALB → Fargate): `https://api.<your-domain>`

### Build-time API configuration
Because this is a Vite SPA, `VITE_API_BASE` is **baked into the build output**.

In CI (recommended), you build like:
```bash
VITE_API_BASE=https://api.<your-domain> npm run build
```

Then upload `dist/` to S3.

> Tip: If you want a single build artifact that works across environments, we can refactor to a runtime config pattern (e.g., fetch `/config.json`). For now, we’ll keep it simple.

## Manual Terraform workflow (so you control when infra changes apply)

The repo has Terraform in `../infrastructure-terraform`.

### What “manual Terraform” means here
You (or CI) run Terraform locally to:
- create/update AWS infrastructure (VPC, ECS/Fargate, ALB, DynamoDB, S3, CloudFront, IAM, etc.)
- keep AWS config consistent over time

### Recommended state setup (do this early)
Terraform works best when state is stored remotely:
- **S3** bucket for Terraform state
- **DynamoDB** table for state locking (prevents two applies at once)

The `terraform { backend "s3" {} }` block exists already; you’ll wire it up with a backend config when you’re ready.

### The safe plan/apply flow
1) **Init** (downloads providers, connects backend)
```powershell
cd ..\infrastructure-terraform
terraform init
```

2) **Plan** (review changes)
```powershell
terraform plan
```

3) **Apply** (make changes)
```powershell
terraform apply
```

### Even safer: explicit one-click approval plan
```powershell
terraform plan -out tfplan
terraform apply tfplan
```

### How you decide “when to push”
A simple, low-stress workflow that works well for side projects:
- Make code changes
- Run your normal app checks (local)
- Run `terraform plan`
- If you like the output, run `terraform apply`

When we add CI/CD later, CI can run `plan` automatically on PRs, and you can keep `apply` as a manual approval.

## Next steps: get a domain + hook it up to the React frontend

This project expects the frontend to be served through **CloudFront** (HTTPS, caching, custom domain).

### Step 1 — Get a domain
Two common paths:

**Option A: Buy the domain in Route 53** (simplest)
- You purchase/register your domain directly in AWS Route 53.
- DNS is already hosted in Route 53, so Terraform can manage everything cleanly.

**Option B: Buy the domain elsewhere (Namecheap, Google Domains, etc.)**
- You’ll create a Route 53 hosted zone in AWS.
- You’ll update your registrar to use AWS Route 53 name servers.

### Step 2 — Create DNS hosted zone (Route 53)
- Create a public hosted zone for the root domain (e.g., `example.com`).
- Add records for:
  - `app.example.com` → CloudFront
  - (later) `api.example.com` → ALB

> If you don’t want Terraform to manage DNS yet, you can do this manually in the AWS console first and migrate later.

### Step 3 — Request an ACM certificate (required for CloudFront)
CloudFront **requires** the TLS certificate to be in **us-east-1**.

- Request a public certificate for:
  - `app.example.com`
  - (optional but recommended) `api.example.com`
- Use DNS validation (Route 53 makes this easy).

### Step 4 — Configure CloudFront to use your custom domain
In CloudFront:
- Add Alternate Domain Name (CNAME): `app.example.com`
- Attach the ACM cert from **us-east-1**
- Point origin to your S3 bucket (private bucket recommended; allow access via CloudFront only)

### Step 5 — Create Route 53 alias record
Create an **A/AAAA Alias** record:
- Name: `app`
- Target: the CloudFront distribution

### Step 6 — Deploy the frontend
Build and upload (in your own terminal/CI environment):
```bash
npm ci
VITE_API_BASE=https://api.example.com npm run build
```

Sync to S3 and invalidate CloudFront.

---

If you want, I can add a small `docs/` section that includes:
- a “dev vs prod environment variables” table
- a “common CloudFront + Vite pitfalls” checklist
- a short guide for SSE proxy settings
