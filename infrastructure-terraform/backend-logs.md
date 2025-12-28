# Viewing backend logs (ECS/Fargate)

Terraform configures the backend container to send logs to CloudWatch Logs.

## Where the logs are

**CloudWatch Log Group:**
- `/ai-antfarm-dev/backend`

**Log streams:**
- typically `ecs/backend/<task-id>`

## Option 1: AWS Console (recommended)

### Via ECS
1. AWS Console → **ECS** → **Clusters** → `ai-antfarm-dev-cluster`
2. **Services** → `ai-antfarm-dev-backend`
3. Click the running **Task**
4. In **Containers** click `backend`
5. Click **View logs in CloudWatch**

### Via CloudWatch
1. AWS Console → **CloudWatch** → **Logs** → **Log groups**
2. Open `/ai-antfarm-dev/backend`
3. Open the newest log stream

## Option 2: AWS CLI (tail logs)

### Follow (tail) the last 30 minutes
```bash
aws logs tail "/ai-antfarm-dev/backend" --follow --since 30m --region us-east-2
```

### Last 10 minutes
```bash
aws logs tail "/ai-antfarm-dev/backend" --since 10m --region us-east-2
```

### Filter for errors (Git Bash / Cygwin)
```bash
aws logs tail "/ai-antfarm-dev/backend" --since 60m --region us-east-2 | grep -i error
```

## Option 3 (advanced): ECS Exec

The service was created with ECS Exec enabled (`enable_execute_command = true`).

### 1) Get the running task ARN
```bash
aws ecs list-tasks --cluster ai-antfarm-dev-cluster --service-name ai-antfarm-dev-backend --region us-east-2
```

### 2) Exec into the container
```bash
aws ecs execute-command \
  --cluster ai-antfarm-dev-cluster \
  --task <TASK_ARN> \
  --container backend \
  --interactive \
  --command "sh"
```

### 3) Inspect env vars
```sh
printenv | sort
```

If `execute-command` fails due to IAM permissions, paste the error and we can add the missing permissions in Terraform.

