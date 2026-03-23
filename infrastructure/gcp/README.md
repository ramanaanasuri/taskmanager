# GCP Terraform Setup Guide -- Task Manager SaaS

Complete reference for deploying and operating the Task Manager on GCP using Terraform.

## Architecture
```
Browser → Route 53 → GCP Load Balancer (SSL) → GCE e2-small
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
| `main.tf` | All GCP resources -- GCE, LB, SSL cert, firewall, static IPs |
| `variables.tf` | Input variables with defaults |
| `outputs.tf` | Values printed after apply including DNS instructions |
| `terraform.tfvars.example` | Template -- copy to `terraform.tfvars` and fill in |
| `.gcp-config.example` | Template -- copy to `.gcp-config` and fill in after apply |
| `cloudshell-setup.sh` | Run at start of every GCP Cloud Shell session |
| `start-gcp.sh` | Start GCE instance |
| `stop-gcp.sh` | Stop GCE instance |

## Quick Start

### 1. Prerequisites
```bash
# Authenticate gcloud
gcloud auth application-default login
gcloud config set project YOUR-PROJECT-ID
```

### 2. Setup GCP Cloud Shell
```bash
bash ~/taskmanager/infrastructure/gcp/cloudshell-setup.sh
# Or after first run: gcpsetup
```

### 3. Create terraform.tfvars
```hcl
project_id = "your-gcp-project-id"
```

### 4. Apply
```bash
cd ~/taskmanager/infrastructure/gcp
terraform init
terraform plan
terraform apply
```

### 5. Update Route 53 DNS
After apply, create A records in Route 53 pointing to the global LB IP:
```
taskmanager.gcp.sriinfosoft.com     → <global_lb_ip>
api-taskmanager.gcp.sriinfosoft.com → <global_lb_ip>
```

### 6. App setup (SSH into instance)
```bash
# SSH via gcloud
gcloud compute ssh ranasuri@gce-for-fullstack-apps-learning --zone=us-west1-b

# Clone repo
mkdir -p /home/ranasuri/sriinfo
git clone -b feature/notifications-clean \
  https://github.com/ramanaanasuri/taskmanager.git \
  /home/ranasuri/sriinfo/taskmanager

# Copy .env via WinSCP or scp

# Build and start
cd /home/ranasuri/sriinfo/taskmanager
screen -S taskmanager
bash scripts/gcprun.sh
# Option 5: BUILD & start ALL services
```

## On-Demand Usage
```bash
# Stop instance
bash infrastructure/gcp/stop-gcp.sh

# Start instance (static IP -- no DNS update needed)
bash infrastructure/gcp/start-gcp.sh
```

## Key Facts

- Instance: e2-small (2 vCPU, 2GB RAM)
- OS: Debian 12
- Docker Compose: Plugin (`docker compose`)
- Swap: 2GB (added automatically on first boot)
- Static IP: Never changes -- no DNS update needed on restart
- Domains: `taskmanager.gcp.sriinfosoft.com` and `api-taskmanager.gcp.sriinfosoft.com`
- SSL: Google-managed certificate via Load Balancer

## Destroy
```bash
terraform destroy
```

## Troubleshooting

| Issue | Fix |
|-------|-----|
| gcloud not authenticated | `gcloud auth application-default login` |
| terraform not found | Run `gcpsetup` |
| SSL cert pending | Wait 10-30 min after DNS propagates |
| Build crashes | Swap is auto-added -- use screen session |
