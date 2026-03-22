#!/bin/bash
# ============================================================
# cloudshell-setup.sh - Run this after every CloudShell reset
#
# Usage: bash infrastructure/aws/cloudshell-setup.sh
# ============================================================

echo "Setting up CloudShell environment..."

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

# Set SSH for GitHub
cd ~/taskmanager
git remote set-url origin git@github.com:ramanaanasuri/taskmanager.git
echo "GitHub remote set to SSH"

# Navigate to aws terraform directory
cd ~/taskmanager/infrastructure/aws
terraform init -upgrade > /dev/null
echo "Terraform initialized"

echo ""
echo "Setup complete! You are ready to run:"
echo "  terraform plan"
echo "  terraform apply"
echo "  terraform destroy"
