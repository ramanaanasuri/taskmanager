#!/bin/bash
# ============================================================
# cloudshell-setup.sh - Run this in GCP Cloud Shell
#
# GCP Cloud Shell has gcloud pre-installed.
# This script installs Terraform and configures git.
#
# Usage: bash infrastructure/gcp/cloudshell-setup.sh
# ============================================================

echo "Setting up GCP Cloud Shell environment..."

# Install Terraform
curl -fsSL https://releases.hashicorp.com/terraform/1.7.5/terraform_1.7.5_linux_amd64.zip \
  -o /tmp/terraform.zip
unzip -o /tmp/terraform.zip -d /tmp
sudo mv /tmp/terraform /usr/local/bin/
echo "Terraform: $(terraform --version | head -1)"

# Set git identity
git config --global user.email "ranasuri@gmail.com"
git config --global user.name "ramanaanasuri"
echo "Git identity set"

# Set GitHub SSH remote
cd ~/taskmanager 2>/dev/null || {
  echo "Repo not found -- cloning..."
  git clone -b feature/notifications-clean \
    https://github.com/ramanaanasuri/taskmanager.git ~/taskmanager
}
git remote set-url origin git@github.com:ramanaanasuri/taskmanager.git
echo "GitHub remote set to SSH"

# Initialize Terraform
cd ~/taskmanager/infrastructure/gcp
terraform init -upgrade > /dev/null
echo "Terraform initialized"

# Auto-source .gcp-config if exists
if [ -f ~/taskmanager/infrastructure/gcp/.gcp-config ]; then
  source ~/taskmanager/infrastructure/gcp/.gcp-config
  echo "GCP config loaded: Instance=$GCP_INSTANCE_NAME Project=$GCP_PROJECT_ID"
else
  echo "WARNING: .gcp-config not found -- create it from .gcp-config.example"
fi

# Add gcpsetup alias
grep -q "gcpsetup" ~/.bashrc || \
  echo "alias gcpsetup='bash ~/taskmanager/infrastructure/gcp/cloudshell-setup.sh'" >> ~/.bashrc
grep -q "gcpsetup" ~/.bash_profile || \
  echo "alias gcpsetup='bash ~/taskmanager/infrastructure/gcp/cloudshell-setup.sh'" >> ~/.bash_profile

echo ""
echo "Setup complete! You are ready to run:"
echo "  terraform plan"
echo "  terraform apply"
echo "  terraform destroy"
echo "  bash start-gcp.sh"
echo "  bash stop-gcp.sh"
