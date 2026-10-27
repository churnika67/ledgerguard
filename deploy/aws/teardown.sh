#!/usr/bin/env bash
# Removes every resource setup.sh created, in dependency order (the instance has to go first --
# nothing else can be deleted while it's still using the subnet/security group/role). Reads
# every resource id from ids.env, the same file setup.sh wrote them to.
#
# You run this yourself; nothing here runs automatically. The S3 bucket step asks for an extra
# explicit confirmation since it permanently deletes whatever statements have been generated.

set -euo pipefail

REGION="us-east-1"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
IDS_FILE="$SCRIPT_DIR/ids.env"

if [ ! -f "$IDS_FILE" ]; then
    echo "No $IDS_FILE found -- nothing to tear down (or setup.sh was never run)."
    exit 0
fi
# shellcheck disable=SC1090
source "$IDS_FILE"

echo "== 1. Terminate EC2 instance =="
if [ -n "${INSTANCE_ID:-}" ]; then
    aws ec2 terminate-instances --region "$REGION" --instance-ids "$INSTANCE_ID" >/dev/null 2>&1 || true
    echo "  waiting for termination..."
    aws ec2 wait instance-terminated --region "$REGION" --instance-ids "$INSTANCE_ID" 2>/dev/null || true
fi

echo "== 2. Delete key pair =="
if [ -n "${KEY_NAME:-}" ]; then
    aws ec2 delete-key-pair --region "$REGION" --key-name "$KEY_NAME" 2>/dev/null || true
    rm -f "$SCRIPT_DIR/${KEY_NAME}.pem"
fi

echo "== 3. CloudWatch log group =="
aws logs delete-log-group --region "$REGION" --log-group-name /ledgerguard/ledger-service \
    2>/dev/null || true

echo "== 4. IAM instance profile + role =="
if [ -n "${INSTANCE_PROFILE_NAME:-}" ] && [ -n "${ROLE_NAME:-}" ]; then
    aws iam remove-role-from-instance-profile --instance-profile-name "$INSTANCE_PROFILE_NAME" \
        --role-name "$ROLE_NAME" 2>/dev/null || true
    aws iam delete-instance-profile --instance-profile-name "$INSTANCE_PROFILE_NAME" 2>/dev/null || true
fi
if [ -n "${ROLE_NAME:-}" ]; then
    aws iam detach-role-policy --role-name "$ROLE_NAME" \
        --policy-arn arn:aws:iam::aws:policy/CloudWatchAgentServerPolicy 2>/dev/null || true
    if [ -n "${POLICY_ARN:-}" ]; then
        aws iam detach-role-policy --role-name "$ROLE_NAME" --policy-arn "$POLICY_ARN" 2>/dev/null || true
    fi
    aws iam delete-role --role-name "$ROLE_NAME" 2>/dev/null || true
fi
if [ -n "${POLICY_ARN:-}" ]; then
    aws iam delete-policy --policy-arn "$POLICY_ARN" 2>/dev/null || true
fi

echo "== 5. S3 bucket =="
if [ -n "${BUCKET_NAME:-}" ]; then
    read -r -p "Permanently delete bucket '$BUCKET_NAME' and every statement in it? [y/N] " confirm
    if [ "$confirm" = "y" ] || [ "$confirm" = "Y" ]; then
        aws s3 rm "s3://${BUCKET_NAME}" --recursive 2>/dev/null || true
        aws s3api delete-bucket --bucket "$BUCKET_NAME" --region "$REGION" 2>/dev/null || true
    else
        echo "  skipped -- bucket left in place."
    fi
fi

echo "== 6. Security group =="
if [ -n "${SG_ID:-}" ]; then
    aws ec2 delete-security-group --region "$REGION" --group-id "$SG_ID" 2>/dev/null || true
fi

echo "== 7. Route table =="
if [ -n "${RTB_ID:-}" ] && [ -n "${SUBNET_ID:-}" ]; then
    ASSOC_ID=$(aws ec2 describe-route-tables --region "$REGION" --route-table-ids "$RTB_ID" \
        --query "RouteTables[0].Associations[?SubnetId=='${SUBNET_ID}'].RouteTableAssociationId" \
        --output text 2>/dev/null || true)
    if [ -n "$ASSOC_ID" ] && [ "$ASSOC_ID" != "None" ]; then
        aws ec2 disassociate-route-table --region "$REGION" --association-id "$ASSOC_ID" 2>/dev/null || true
    fi
    aws ec2 delete-route-table --region "$REGION" --route-table-id "$RTB_ID" 2>/dev/null || true
fi

echo "== 8. Internet gateway =="
if [ -n "${IGW_ID:-}" ] && [ -n "${VPC_ID:-}" ]; then
    aws ec2 detach-internet-gateway --region "$REGION" --internet-gateway-id "$IGW_ID" \
        --vpc-id "$VPC_ID" 2>/dev/null || true
    aws ec2 delete-internet-gateway --region "$REGION" --internet-gateway-id "$IGW_ID" 2>/dev/null || true
fi

echo "== 9. Subnet =="
if [ -n "${SUBNET_ID:-}" ]; then
    aws ec2 delete-subnet --region "$REGION" --subnet-id "$SUBNET_ID" 2>/dev/null || true
fi

echo "== 10. VPC =="
if [ -n "${VPC_ID:-}" ]; then
    aws ec2 delete-vpc --region "$REGION" --vpc-id "$VPC_ID" 2>/dev/null || true
fi

echo ""
echo "Teardown complete. Double-check the EC2 and VPC consoles in us-east-1 to confirm nothing"
echo "billable is left running, then you can delete $IDS_FILE."
