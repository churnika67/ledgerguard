# Deploying ledger-service to AWS (free tier, $0)

This deploys **only `ledger-service`** to a single EC2 instance, inside its own VPC, with S3
access via an IAM instance role (no access keys anywhere) and logs shipped to CloudWatch.
Everything here is scoped to stay at **$0**: one `t3.micro` (free-tier eligible, 750 hrs/month for
12 months), no NAT Gateway, no load balancer, no RDS, no ECS/EKS, no unattached Elastic IP.
Neon Postgres (already in use) stays the database -- nothing database-related changes here.

You can do this two ways:
- **Console steps below** -- slower, but you see and understand every resource as you create it.
  Good the first time, and good for being able to explain each piece in an interview.
- **`setup.sh`** -- same resources, scripted. See `../deploy/aws/setup.sh`.

Either way, **you run every AWS-changing command yourself** -- nothing here creates or deletes
anything on your account automatically.

## 0. Account safety first

1. Confirm you're on the **AWS Free Tier** (Billing console -> top right -> check your plan).
2. Enable **MFA** on your root account: IAM console -> top right account menu -> Security
   credentials -> Assign MFA device.
3. Set a **zero-spend budget alert** so you're emailed the moment anything would cost money:
   Billing console -> Budgets -> Create budget -> Zero spend budget. This uses AWS's built-in
   template and emails you if forecast spend ever exceeds $0.01.

## 1. VPC

Console: **VPC** -> **Your VPCs** -> **Create VPC**.
- Resources to create: "VPC only".
- IPv4 CIDR: `10.0.0.0/16`.
- Name: `ledgerguard-vpc`.
- Leave everything else default. Create.

## 2. Public subnet

Console: **VPC** -> **Subnets** -> **Create subnet**.
- VPC: `ledgerguard-vpc`.
- Subnet name: `ledgerguard-public-subnet`.
- Availability Zone: **us-east-1a**.
- IPv4 CIDR: `10.0.1.0/24`.
- Create, then select the new subnet -> **Actions** -> **Edit subnet settings** -> enable
  **Auto-assign public IPv4 address** -> Save. (Without this, the instance gets no public IP and
  you can't reach it at all -- this is the step people most often forget.)

## 3. Internet gateway

Console: **VPC** -> **Internet gateways** -> **Create internet gateway**.
- Name: `ledgerguard-igw`. Create.
- Select it -> **Actions** -> **Attach to VPC** -> `ledgerguard-vpc`.

## 4. Route table

Console: **VPC** -> **Route tables** -> **Create route table**.
- Name: `ledgerguard-public-rt`.
- VPC: `ledgerguard-vpc`. Create.
- Select it -> **Routes** tab -> **Edit routes** -> **Add route**:
  destination `0.0.0.0/0`, target the internet gateway (`ledgerguard-igw`). Save.
- **Subnet associations** tab -> **Edit subnet associations** -> check
  `ledgerguard-public-subnet`. Save.

This route is what makes the subnet "public" -- without it, traffic from the instance has
nowhere to go except within the VPC itself.

## 5. Security group

Console: **VPC** -> **Security groups** -> **Create security group**.
- Name: `ledgerguard-sg`. VPC: `ledgerguard-vpc`.
- Inbound rules:
  - Type **SSH**, port 22, source **My IP** (the console fills in your current public IP --
    never use `0.0.0.0/0` here, that's an open invitation to bots scanning for weak SSH).
  - Type **Custom TCP**, port **8080** (ledger-service's port), source **My IP** for now (you can
    widen this to `0.0.0.0/0` later once you want to demo it publicly -- that's a deliberate,
    explainable choice, not an oversight).
- Outbound rules: leave the default "all traffic allowed" -- the instance needs this to reach
  Neon Postgres, S3, and CloudWatch, all of which are outside the VPC.
- Create.

## 6. S3 bucket

Console: **S3** -> **Create bucket**.
- Name: globally unique, e.g. `ledgerguard-statements-<your-initials>-<random digits>`.
- Region: **us-east-1** (must match the region you deploy everything else in).
- **Block all public access**: leave this **ON** (the default) -- the bucket should never be
  publicly readable; the app reaches it via the IAM role, not a public URL.
- Create.

## 7. IAM role for EC2

Console: **IAM** -> **Policies** -> **Create policy** -> JSON tab, paste:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": ["s3:PutObject", "s3:GetObject", "s3:ListBucket"],
      "Resource": [
        "arn:aws:s3:::YOUR-BUCKET-NAME",
        "arn:aws:s3:::YOUR-BUCKET-NAME/*"
      ]
    }
  ]
}
```

Replace `YOUR-BUCKET-NAME` with the bucket from step 6. Note `ListBucket` applies to the bucket
ARN itself (no `/*`), while `PutObject`/`GetObject` apply to objects inside it (`/*`) -- that's
not a typo, it's how S3 ARNs distinguish "the bucket" from "things in the bucket".

Name the policy `ledgerguard-statements-s3-policy`. Create policy.

Then: **IAM** -> **Roles** -> **Create role**.
- Trusted entity type: **AWS service**. Use case: **EC2**.
- Attach policies: `ledgerguard-statements-s3-policy` (the one you just made) and the AWS-managed
  **CloudWatchAgentServerPolicy** (lets the CloudWatch agent on the instance ship logs/metrics --
  this is the AWS-managed policy made for exactly this, not a hand-written grant).
- Name: `ledgerguard-ec2-role`. Create role.

This role is the **only** way the app on EC2 will ever get AWS credentials -- there is no access
key anywhere in the code, Docker image, or any file you commit.

## 8. The EC2 instance

Console: **EC2** -> **Launch instance**.
- Name: `ledgerguard-ledger-service`.
- AMI: **Amazon Linux 2023** (free tier eligible).
- Instance type: **t3.micro** (free tier eligible -- double check the "Free tier eligible" badge
  next to it before continuing).
- Key pair: create a new one, e.g. `ledgerguard-key`, download the `.pem` file. **This file isn't
  committed anywhere** -- `deploy/aws/*.pem` is gitignored; keep it somewhere safe on your Mac.
- Network settings -> Edit:
  - VPC: `ledgerguard-vpc`.
  - Subnet: `ledgerguard-public-subnet`.
  - Auto-assign public IP: **Enable** (should already default from the subnet setting, but
    confirm it here too).
  - Firewall (security groups): select existing -> `ledgerguard-sg`.
- Advanced details -> **IAM instance profile**: `ledgerguard-ec2-role`.
- Storage: leave the default 8 GiB gp3 (within the free tier's 30 GiB/month).
- Launch instance.

Once it's running, note its **public IPv4 address** from the instance list -- you'll need it for
`run-on-ec2.sh` and `verify.md`.

## What this costs

With exactly these resources (one `t3.micro`, no NAT Gateway, no load balancer, no RDS, the
default 8 GiB of gp3 storage, S3 standard storage for a handful of small CSV files, and
CloudWatch Logs within its free tier of 5 GB ingestion/month): **$0/month**, for the first 12
months under the AWS Free Tier. The zero-spend budget from step 0 will email you immediately if
that ever stops being true.

## Tearing it down

See `teardown.sh`, or delete in reverse order through the console: terminate the EC2 instance
first (everything else can't be deleted while it's running), then the IAM role/policy, the S3
bucket (empty it first), the security group, route table, internet gateway (detach, then
delete), subnet, and finally the VPC.
