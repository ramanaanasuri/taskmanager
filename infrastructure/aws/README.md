# AWS Terraform Setup Guide -- Task Manager SaaS

Complete reference for deploying and operating the Task Manager on AWS using Terraform.

## Architecture
```
Browser → Route 53 → CloudFront (SSL) → EC2 t2.micro
                                              ↓
                                    ┌─────────────────┐
                                    │ Docker Compose   │
                                    │ ├── Nginx+React  │
                                    │ ├── Spring Boot  │
                                    │ └── MariaDB      │
                                    └─────────────────┘
```

## Files

| File | Purpose |
|------|---------|
| `main.tf` | All AWS resources -- EC2, CloudFront, ACM, Route53, security group |
| `variables.tf` | Input variables with defaults |
| `outputs.tf` | Values printed after apply |
| `terraform.tfvars.example` | Template -- copy to `terraform.tfvars` and fill in |
| `.aws-config.example` | Template -- copy to `.aws-config` and fill in after apply |
| `cloudshell-setup.sh` | Run at start of every CloudShell session |
| `start-aws.sh` | Start EC2 + auto-update CloudFront origins |
| `stop-aws.sh` | Stop EC2 to save compute cost |

## Quick Start

### 1. Setup CloudShell
```bash
bash ~/taskmanager/infrastructure/aws/cloudshell-setup.sh
# Or after first run: tfsetup
```

### 2. Create terraform.tfvars
```hcl
key_pair_name  = "your-key-pair-name"
admin_ssh_cidr = "YOUR_IP/32"  # curl https://checkip.amazonaws.com
```

### 3. Apply
```bash
cd ~/taskmanager/infrastructure/aws
terraform init
terraform plan
terraform apply
```

### 4. After apply -- fill in .aws-config
```bash
export TASKMANAGER_INSTANCE_ID="i-XXXXXXXXXXXXXXXXX"
export TASKMANAGER_FRONTEND_DIST_ID="EXXXXXXXXXXXXX"
export TASKMANAGER_BACKEND_DIST_ID="EXXXXXXXXXXXXX"
export TASKMANAGER_KEY_PATH="~/ranasuri-manteca.pem"
export AWS_DEFAULT_REGION="us-west-1"
```

### 5. App setup (SSH into instance)
```bash
# Clone repo
mkdir -p /home/ec2-user/sriinfo
git clone -b feature/notifications-clean \
  https://github.com/ramanaanasuri/taskmanager.git \
  /home/ec2-user/sriinfo/taskmanager

# Copy .env via WinSCP to /home/ec2-user/sriinfo/taskmanager/.env

# Replace URLs and build
cd /home/ec2-user/sriinfo/taskmanager
screen -S taskmanager
bash scripts/awsrun.sh
# Option 16: taskmanager.gcp.sriinfosoft.com → tm.sriinfosoft.com
# Option 16: api-taskmanager.gcp.sriinfosoft.com → api-tm.sriinfosoft.com
# Option 5:  BUILD & start ALL services
```

## On-Demand Usage
```bash
# Stop instance (saves compute cost)
bash infrastructure/aws/stop-aws.sh

# Start instance (auto-updates CloudFront origins)
bash infrastructure/aws/start-aws.sh
```

## Destroy
```bash
terraform destroy
```

## Key Facts

- Instance: t2.micro (free tier eligible 12 months)
- OS: Amazon Linux 2023
- Docker Compose: v2.32.4 standalone
- Swap: 2GB (added automatically on first boot)
- App directory: `/home/ec2-user/sriinfo/taskmanager`
- Domains: `tm.sriinfosoft.com` and `api-tm.sriinfosoft.com`
- SSL: CloudFront + ACM (us-east-1)

## Cost (Free Tier)

| Resource | Cost |
|----------|------|
| EC2 t2.micro | $0 (free tier) |
| EBS 20GB | $0 (free tier) |
| Public IPv4 | $3.36/mo (always billed) |
| CloudFront | ~$0 |
| ACM certs | $0 |
| **Total** | **~$3.86/mo** |

## Troubleshooting

| Issue | Fix |
|-------|-----|
| SSH timeout | Add CloudShell IP to security group |
| terraform not found | Run `tfsetup` |
| CNAMEAlreadyExists | Delete old CloudFront distributions first |
| Build crashes instance | Ensure 2GB swap, use screen session |
| CloudFront serves old content | Run `bash start-aws.sh` to update origins |
