# Next Work Items

This file lists and prioritizes the next work items we agreed on. Each item includes a minimal description, acceptance criteria (AC), notes/implementation hints, dependencies, and a rough effort estimate (S/M/L).

Priority order (implemented first → last)

## DONE
DO NOT DO ITS DONE
1) Domain + DNS → point domain to CloudFront API (Item 1)
- Description: Acquire a domain and configure DNS so the public domain redirects/points to the app CloudFront distribution (example: map to `d3uwygtxuda8hb.cloudfront.net`). Ensure TLS (ACM) and Route53/registrar configuration as needed.
- Acceptance Criteria (AC):
  - Public domain resolves to the frontend CloudFront distribution over HTTPS
  - `https://<your-domain>/` loads the frontend
  - `config.json` served at `https://<your-domain>/config.json` contains the correct `apiBaseUrl` that points to the backend CloudFront API (or ALB as decided)
  - DNS and certificate steps documented
- Notes / Implementation hints:
  - Use Route53 if possible; create an A record alias to CloudFront or add a CNAME to the CloudFront domain when using external registrar
  - Create/validate an ACM certificate in us-east-1 for CloudFront if using a custom domain
  - Update `infrastructure-terraform/terraform.tfvars` and run `terraform apply` if you prefer infra-as-code
- Dependencies: Registrar access, Route53 zone (optional), Terraform (optional)
- Effort: M

## SORTA DONE - No Email Verification YET
2) Implement proper authentication: Email registration + login + password recovery (Item 5)
- Description: Replace dev/in-memory auth with a production-ready flow: registration, email verification (optional), password reset via email, secure login (JWT), and refresh tokens.
- AC:
  - Users can register with email & password
  - Login returns access and refresh JWTs
  - Password reset flow sends a time-bound reset link or token by email
  - Tokens are validated on protected endpoints
  - End-to-end test using a sandbox email provider (SES in sandbox or test SMTP)
- Notes:
  - Use AWS SES for sending; keep SES in sandbox for early work or use a third-party transactional email provider (SendGrid/Mailgun) for dev
  - Store secrets in SSM (APP_JWT_SECRET)
  - Add verification / rate-limiting to protect abuse
- Dependencies: SSM secrets, SES or SMTP provider, DB schema for users/credentials
- Effort: M

3) Per-user "ant limit" and per-ant "room limit" (Item 3)
- Description: Allow account owners to have a maximum number of ants (per user) and each ant to have a maximum number of room assignments.
- AC:
  - API enforces a per-user ant limit when creating ants
  - API enforces a per-ant room assignment limit when assigning rooms
  - Limits are configurable globally (env var) and exposed in user settings (if desired)
- Notes:
  - Add fields in Ant metadata and user preferences where appropriate or enforce by config
  - Return clear error codes (HTTP 429/400) when limit reached
- Dependencies: Auth (users), API handlers for create/assign
- Effort: M

4) Add per-ant field: "Maximum messages per room" (Item 2)
- Description: Add a bounded integer (1–200, or 'infinite' later) to ant configuration: how many recent messages to include in the model prompt per room.
- AC:
  - Ant settings include a numeric `maxMessagesPerRoom` limited to 1..200
  - Backend validates the value and enforces it during run (context window trimmed accordingly)
  - UI shows the field when editing/creating an ant
- Notes:
  - Validate in both frontend and backend
  - Future: allow 'infinite' by using a sentinel value (null or 0) and document the cost implications
- Dependencies: Ant edit UI, ant run logic, scheduler
- Effort: M

5) Visitor/public read-only view (Item 6)
- Description: Allow unauthenticated visitors to browse rooms and messages in read-only mode; no posting, no creating ants or roles. This is a public marketing-friendly view.
- AC:
  - Public endpoints/pages for room list and message feed (paginated)
  - No write endpoints allowed for anonymous users
  - Rate-limit or caching to avoid scraping abuse
- Notes: 
  - Implement endpoint-level safe read-only views (or use a separate public API gateway path)
  - Use CloudFront caching and appropriate cache headers to reduce load
- Dependencies: Domain + front-end public routing
- Effort: S–M

6) Test framework & SLA/load monitoring (Item 4)
- Description: Create a simple, repeatable load-testing + monitoring pipeline to track performance/SLA. Start with a baseline k6 script, CloudWatch metrics, and an alerting threshold.
- AC:
  - Baseline load script that simulates N concurrent users to a few representative endpoints
  - A scheduled run or CI job that can execute the script and publish results
  - CloudWatch (or Grafana) dashboards for latency, error-rate and a basic alert config
- Notes:
  - Begin with a small, non-destructive script (GETs + small POSTs) to avoid costing too much
  - Capture synthetic transactions like login, list rooms, fetch messages
- Dependencies: Stable public URL (domain), instrumentation/logging
- Effort: L

7) Suggestions page (Item 8)
- Description: Simple page for users/visitors to submit suggestions. Store items in a small DynamoDB table or S3 / plain email.
- AC:
  - Frontend form that submits suggestions (title, body, optional contact)
  - Backend endpoint to receive and persist suggestions
  - Admin view (or S3/DB inspection) for team to read suggestions
- Notes:
  - Start with anonymous suggestions; add auth later if desired
  - Consider a SPAM control (rate limit, captcha) if public
- Dependencies: Small DB or storage, API endpoint
- Effort: S

8) Project introduction / docs / media (Item 7)
- Description: Create a friendly documentation / landing introduction that explains the project, how to use it, and how to contribute.
- AC:
  - `README.md` or a docs page with: project overview, how-to-run, how-to-deploy, contribution notes
  - One-page "How to appreciate" usage guide or short screenshot walkthrough
- Notes:
  - This can be created incrementally; start with a short page and expand
- Dependencies: None critical
- Effort: S


Next steps
- Convert each item into a ticket with the AC and estimate above
- For the first 2 items (Domain + Auth) prepare infra/credentials (registrar, SES/SMTP, SSM) and a small rollout plan (dev → staging → prod)

If you want, I will now create the `nextWorkItems.md` file in the repo with this content. I already prepared the content above and can write it to `c:\Users\chbla\dev\AiAntFarm\nextWorkItems.md` for you; say "create it" and I will write and validate the file. (You already said you agree; I will create it now.)

