#!/usr/bin/env bash
# Creates every AWS resource ledger-service needs to run on one free-tier t3.micro: a VPC, a
# public subnet, an internet gateway, a route table, a security group, an S3 bucket, and an IAM
# role -- then launches the instance. Nothing here runs unless you run this script yourself.
#
# Idempotent: every resource is looked up by its Name tag (or, for the bucket/policy/role, by
# name directly) before being created, so re-running this after a partial failure picks up where
# it left off instead of erroring on "already exists" or creating duplicates.
#
# Writes every resource id it creates to ids.env (gitignored) as it goes, so a later run -- or
# verify.md, or teardown.sh -- can `source ids.env` to find them.
#
# Prerequisites: AWS CLI v2 configured (`aws configure`) with a user that has permission to
# create VPCs/EC2/IAM/S3 resources, on the AWS Free Tier, in us-east-1.

set -euo pipefail

REGION="us-east-1"
AZ="us-east-1a"
NAME_PREFIX="ledgerguard"
BUCKET_NAME="${STATEMENTS_BUCKET:?Set STATEMENTS_BUCKET to a globally-unique S3 bucket name first, e.g. export STATEMENTS_BUCKET=ledgerguard-statements-yourname-1234}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
IDS_FILE="$SCRIPT_DIR/ids.env"
touch "$IDS_FILE"
# shellcheck disable=SC1090
source "$IDS_FILE"

save_id() {
    local key="$1" value="$2"
    if grep -q "^${key}=" "$IDS_FILE" 2>/dev/null; then
        sed -i.bak "s/^${key}=.*/${key}=${value}/" "$IDS_FILE" && rm -f "${IDS_FILE}.bak"
    else
        echo "${key}=${value}" >> "$IDS_FILE"
    fi
    echo "  -> ${key}=${value}"
}

echo "== 1. VPC =="
VPC_ID="${VPC_ID:-}"
if [ -z "$VPC_ID" ]; then
    VPC_ID=$(aws ec2 describe-vpcs --region "$REGION" \
        --filters "Name=tag:Name,Values=${NAME_PREFIX}-vpc" \
        --query 'Vpcs[0].VpcId' --output text)
fi
if [ "$VPC_ID" = "None" ] || [ -z "$VPC_ID" ]; then
    VPC_ID=$(aws ec2 create-vpc --region "$REGION" --cidr-block 10.0.0.0/16 \
        --tag-specifications "ResourceType=vpc,Tags=[{Key=Name,Value=${NAME_PREFIX}-vpc}]" \
        --query 'Vpc.VpcId' --output text)
fi
save_id VPC_ID "$VPC_ID"

echo "== 2. Public subnet =="
SUBNET_ID="${SUBNET_ID:-}"
if [ -z "$SUBNET_ID" ]; then
    SUBNET_ID=$(aws ec2 describe-subnets --region "$REGION" \
        --filters "Name=tag:Name,Values=${NAME_PREFIX}-public-subnet" \
        --query 'Subnets[0].SubnetId' --output text)
fi
if [ "$SUBNET_ID" = "None" ] || [ -z "$SUBNET_ID" ]; then
    SUBNET_ID=$(aws ec2 create-subnet --region "$REGION" --vpc-id "$VPC_ID" \
        --cidr-block 10.0.1.0/24 --availability-zone "$AZ" \
        --tag-specifications "ResourceType=subnet,Tags=[{Key=Name,Value=${NAME_PREFIX}-public-subnet}]" \
        --query 'Subnet.SubnetId' --output text)
fi
aws ec2 modify-subnet-attribute --region "$REGION" --subnet-id "$SUBNET_ID" --map-public-ip-on-launch
save_id SUBNET_ID "$SUBNET_ID"

echo "== 3. Internet gateway =="
IGW_ID="${IGW_ID:-}"
if [ -z "$IGW_ID" ]; then
    IGW_ID=$(aws ec2 describe-internet-gateways --region "$REGION" \
        --filters "Name=tag:Name,Values=${NAME_PREFIX}-igw" \
        --query 'InternetGateways[0].InternetGatewayId' --output text)
fi
if [ "$IGW_ID" = "None" ] || [ -z "$IGW_ID" ]; then
    IGW_ID=$(aws ec2 create-internet-gateway --region "$REGION" \
        --tag-specifications "ResourceType=internet-gateway,Tags=[{Key=Name,Value=${NAME_PREFIX}-igw}]" \
        --query 'InternetGateway.InternetGatewayId' --output text)
fi
ATTACHED=$(aws ec2 describe-internet-gateways --region "$REGION" --internet-gateway-ids "$IGW_ID" \
    --query "InternetGateways[0].Attachments[?VpcId=='${VPC_ID}'] | length(@)")
if [ "$ATTACHED" = "0" ]; then
    aws ec2 attach-internet-gateway --region "$REGION" --vpc-id "$VPC_ID" --internet-gateway-id "$IGW_ID"
fi
save_id IGW_ID "$IGW_ID"

echo "== 4. Route table =="
RTB_ID="${RTB_ID:-}"
if [ -z "$RTB_ID" ]; then
    RTB_ID=$(aws ec2 describe-route-tables --region "$REGION" \
        --filters "Name=tag:Name,Values=${NAME_PREFIX}-public-rt" \
        --query 'RouteTables[0].RouteTableId' --output text)
fi
if [ "$RTB_ID" = "None" ] || [ -z "$RTB_ID" ]; then
    RTB_ID=$(aws ec2 create-route-table --region "$REGION" --vpc-id "$VPC_ID" \
        --tag-specifications "ResourceType=route-table,Tags=[{Key=Name,Value=${NAME_PREFIX}-public-rt}]" \
        --query 'RouteTable.RouteTableId' --output text)
fi
aws ec2 create-route --region "$REGION" --route-table-id "$RTB_ID" \
    --destination-cidr-block 0.0.0.0/0 --gateway-id "$IGW_ID" 2>/dev/null || true
aws ec2 associate-route-table --region "$REGION" --subnet-id "$SUBNET_ID" --route-table-id "$RTB_ID" \
    2>/dev/null || true
save_id RTB_ID "$RTB_ID"

echo "== 5. Security group =="
MY_IP="$(curl -s https://checkip.amazonaws.com)/32"
SG_ID="${SG_ID:-}"
if [ -z "$SG_ID" ]; then
    SG_ID=$(aws ec2 describe-security-groups --region "$REGION" \
        --filters "Name=group-name,Values=${NAME_PREFIX}-sg" "Name=vpc-id,Values=${VPC_ID}" \
        --query 'SecurityGroups[0].GroupId' --output text)
fi
if [ "$SG_ID" = "None" ] || [ -z "$SG_ID" ]; then
    SG_ID=$(aws ec2 create-security-group --region "$REGION" --group-name "${NAME_PREFIX}-sg" \
        --description "ledger-service: SSH + app port from my IP only" --vpc-id "$VPC_ID" \
        --query 'GroupId' --output text)
fi
aws ec2 authorize-security-group-ingress --region "$REGION" --group-id "$SG_ID" \
    --protocol tcp --port 22 --cidr "$MY_IP" 2>/dev/null || true
aws ec2 authorize-security-group-ingress --region "$REGION" --group-id "$SG_ID" \
    --protocol tcp --port 8080 --cidr "$MY_IP" 2>/dev/null || true
save_id SG_ID "$SG_ID"
save_id MY_IP "$MY_IP"

echo "== 6. S3 bucket =="
if ! aws s3api head-bucket --bucket "$BUCKET_NAME" 2>/dev/null; then
    aws s3api create-bucket --bucket "$BUCKET_NAME" --region "$REGION"
fi
aws s3api put-public-access-block --bucket "$BUCKET_NAME" --public-access-block-configuration \
    BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true
save_id BUCKET_NAME "$BUCKET_NAME"

echo "== 7. IAM policy + role =="
POLICY_NAME="${NAME_PREFIX}-statements-s3-policy"
POLICY_JSON=$(cat <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": ["s3:PutObject", "s3:GetObject", "s3:ListBucket"],
      "Resource": ["arn:aws:s3:::${BUCKET_NAME}", "arn:aws:s3:::${BUCKET_NAME}/*"]
    }
  ]
}
EOF
)
ACCOUNT_ID=$(aws sts get-caller-identity --query 'Account' --output text)
POLICY_ARN="arn:aws:iam::${ACCOUNT_ID}:policy/${POLICY_NAME}"
if ! aws iam get-policy --policy-arn "$POLICY_ARN" >/dev/null 2>&1; then
    POLICY_ARN=$(aws iam create-policy --policy-name "$POLICY_NAME" \
        --policy-document "$POLICY_JSON" --query 'Policy.Arn' --output text)
fi
save_id POLICY_ARN "$POLICY_ARN"

ROLE_NAME="${NAME_PREFIX}-ec2-role"
TRUST_JSON='{"Version":"2012-10-17","Statement":[{"Effect":"Allow","Principal":{"Service":"ec2.amazonaws.com"},"Action":"sts:AssumeRole"}]}'
if ! aws iam get-role --role-name "$ROLE_NAME" >/dev/null 2>&1; then
    aws iam create-role --role-name "$ROLE_NAME" --assume-role-policy-document "$TRUST_JSON"
fi
aws iam attach-role-policy --role-name "$ROLE_NAME" --policy-arn "$POLICY_ARN"
aws iam attach-role-policy --role-name "$ROLE_NAME" \
    --policy-arn arn:aws:iam::aws:policy/CloudWatchAgentServerPolicy

INSTANCE_PROFILE_NAME="${NAME_PREFIX}-ec2-instance-profile"
if ! aws iam get-instance-profile --instance-profile-name "$INSTANCE_PROFILE_NAME" >/dev/null 2>&1; then
    aws iam create-instance-profile --instance-profile-name "$INSTANCE_PROFILE_NAME"
    aws iam add-role-to-instance-profile --instance-profile-name "$INSTANCE_PROFILE_NAME" \
        --role-name "$ROLE_NAME"
    echo "  (waiting ~10s for the instance profile to propagate before it's usable at launch)"
    sleep 10
fi
save_id ROLE_NAME "$ROLE_NAME"
save_id INSTANCE_PROFILE_NAME "$INSTANCE_PROFILE_NAME"

echo "== 8. Key pair =="
KEY_NAME="${NAME_PREFIX}-key"
KEY_FILE="$SCRIPT_DIR/${KEY_NAME}.pem"
if ! aws ec2 describe-key-pairs --region "$REGION" --key-names "$KEY_NAME" >/dev/null 2>&1; then
    aws ec2 create-key-pair --region "$REGION" --key-name "$KEY_NAME" \
        --query 'KeyMaterial' --output text > "$KEY_FILE"
    chmod 400 "$KEY_FILE"
    echo "  -> private key saved to $KEY_FILE (gitignored -- keep this safe, it's not recoverable)"
else
    echo "  key pair '$KEY_NAME' already exists in AWS -- reusing it. If you don't still have"
    echo "  the .pem file from when it was created, delete the key pair in the console and"
    echo "  re-run this script to generate a new one."
fi
save_id KEY_NAME "$KEY_NAME"

echo "== 9. EC2 instance =="
INSTANCE_ID="${INSTANCE_ID:-}"
if [ -z "$INSTANCE_ID" ]; then
    INSTANCE_ID=$(aws ec2 describe-instances --region "$REGION" \
        --filters "Name=tag:Name,Values=${NAME_PREFIX}-ledger-service" \
                  "Name=instance-state-name,Values=pending,running,stopped" \
        --query 'Reservations[0].Instances[0].InstanceId' --output text)
fi
if [ "$INSTANCE_ID" = "None" ] || [ -z "$INSTANCE_ID" ]; then
    AMI_ID=$(aws ssm get-parameter --region "$REGION" \
        --name /aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-x86_64 \
        --query 'Parameter.Value' --output text)
    INSTANCE_ID=$(aws ec2 run-instances --region "$REGION" \
        --image-id "$AMI_ID" --instance-type t3.micro --key-name "$KEY_NAME" \
        --subnet-id "$SUBNET_ID" --security-group-ids "$SG_ID" \
        --iam-instance-profile "Name=${INSTANCE_PROFILE_NAME}" \
        --associate-public-ip-address \
        --tag-specifications "ResourceType=instance,Tags=[{Key=Name,Value=${NAME_PREFIX}-ledger-service}]" \
        --query 'Instances[0].InstanceId' --output text)
fi
save_id INSTANCE_ID "$INSTANCE_ID"

echo "  waiting for the instance to reach 'running'..."
aws ec2 wait instance-running --region "$REGION" --instance-ids "$INSTANCE_ID"
PUBLIC_IP=$(aws ec2 describe-instances --region "$REGION" --instance-ids "$INSTANCE_ID" \
    --query 'Reservations[0].Instances[0].PublicIpAddress' --output text)
save_id PUBLIC_IP "$PUBLIC_IP"

echo ""
echo "Done. Everything is in $IDS_FILE. Instance public IP: $PUBLIC_IP"
echo "Next: deploy/aws/run-on-ec2.sh (copies and runs it on the instance over SSH)."
