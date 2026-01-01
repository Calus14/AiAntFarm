#!/bin/bash

# Set variables based on your terraform outputs/variables
CLUSTER_NAME="ai-antfarm-dev-cluster"
SERVICE_NAME="ai-antfarm-dev-backend"
REGION="us-east-2"

echo "==================================================="
echo "Checking ECS Service Status: $SERVICE_NAME"
echo "==================================================="

# Get Service Details
aws ecs describe-services \
    --cluster "$CLUSTER_NAME" \
    --services "$SERVICE_NAME" \
    --region "$REGION" \
    --query "services[0].{
        Status: status,
        DesiredCount: desiredCount,
        RunningCount: runningCount,
        PendingCount: pendingCount,
        Deployments: deployments[*].{
            Status: status,
            RolloutState: rolloutState,
            UpdatedAt: updatedAt
        }
    }" \
    --output table

echo ""
echo "==================================================="
echo "Recent Service Events (Last 10)"
echo "==================================================="
aws ecs describe-services \
    --cluster "$CLUSTER_NAME" \
    --services "$SERVICE_NAME" \
    --region "$REGION" \
    --query "services[0].events[0:10].{Time: createdAt, Message: message}" \
    --output table

echo ""
echo "==================================================="
echo "Running Tasks"
echo "==================================================="
TASK_ARNS=$(aws ecs list-tasks --cluster "$CLUSTER_NAME" --service-name "$SERVICE_NAME" --region "$REGION" --query "taskArns" --output text)

if [ -z "$TASK_ARNS" ]; then
    echo "NO RUNNING TASKS FOUND!"
else
    aws ecs describe-tasks \
        --cluster "$CLUSTER_NAME" \
        --tasks $TASK_ARNS \
        --region "$REGION" \
        --query "tasks[*].{
            ID: taskArn,
            LastStatus: lastStatus,
            Health: healthStatus,
            StartedAt: startedAt,
            StoppedReason: stoppedReason
        }" \
        --output table
fi

