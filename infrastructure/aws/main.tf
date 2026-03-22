# ============================================================
# main.tf — AWS Task Manager Infrastructure
#
# Mirrors existing working AWS setup exactly:
# - EC2 t2.micro (free tier eligible)
# - Amazon Linux 2023
# - Security group matching EC2Access-for-Services
# - CloudFront for SSL termination (same as existing setup)
# - ACM certificates
# - Route 53 DNS records
#
# Usage:
#   terraform init
#   terraform plan
#   terraform apply
#
# Prerequisites:
#   - AWS CLI configured with terraform-taskmanager profile
#   - Route 53 hosted zone for sriinfosoft.com
# ============================================================

terraform {
  required_version = ">= 1.5"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

provider "aws" {
  region = var.region
}

provider "aws" {
  alias  = "us_east_1"
  region = "us-east-1"
}

# ============================================================
# 1. DATA SOURCES
# ============================================================

data "aws_route53_zone" "sriinfosoft" {
  name         = "sriinfosoft.com."
  private_zone = false
}

# ============================================================
# 2. SECURITY GROUP (mirrors EC2Access-for-Services exactly)
# ============================================================

resource "aws_security_group" "taskmanager" {
  name        = "taskmanager-sg"
  description = "Task Manager - mirrors EC2Access-for-Services"

  ingress {
    description = "HTTP"
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    description = "HTTPS"
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    description = "Backend API"
    from_port   = 8080
    to_port     = 8080
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    description = "Dev port"
    from_port   = 5000
    to_port     = 5000
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    description = "SSH from admin"
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = [var.admin_ssh_cidr, "13.52.6.112/29"]
  }

  ingress {
    description = "ICMP"
    from_port   = -1
    to_port     = -1
    protocol    = "icmp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = { Name = "taskmanager-sg", Project = "taskmanager" }
}

# ============================================================
# 3. EC2 INSTANCE (mirrors existing t2.micro setup)
# ============================================================

resource "aws_instance" "taskmanager" {
  ami                    = var.ami_id
  instance_type          = var.instance_type
  key_name               = var.key_pair_name
  vpc_security_group_ids = [aws_security_group.taskmanager.id]

  root_block_device {
    volume_type           = "gp3"
    volume_size           = 20
    delete_on_termination = true
    tags = { Name = "taskmanager-root", Project = "taskmanager" }
  }

  # Mirrors GCP metadata_startup_script -- Docker only
  user_data = <<-EOF
    #!/bin/bash
    set -e

    # Add 2GB swap (required for Docker build on t2.micro)
    dd if=/dev/zero of=/swapfile bs=128M count=16
    chmod 600 /swapfile
    mkswap /swapfile
    swapon /swapfile
    echo '/swapfile swap swap defaults 0 0' >> /etc/fstab

    # Install Docker
    dnf install -y docker git
    systemctl enable docker
    systemctl start docker
    usermod -aG docker ec2-user

    # Install docker-compose v2.32.4 standalone
    curl -SL "https://github.com/docker/compose/releases/download/v2.32.4/docker-compose-linux-x86_64" \
      -o /usr/local/bin/docker-compose
    chmod +x /usr/local/bin/docker-compose

    echo "Docker installed successfully"
  EOF

  tags = { Name = var.instance_name, Project = "taskmanager" }
}

# ============================================================
# 4. ACM CERTIFICATES (us-east-1 required for CloudFront)
# ============================================================

resource "aws_acm_certificate" "frontend" {
  provider          = aws.us_east_1
  domain_name       = var.frontend_domain
  validation_method = "DNS"
  lifecycle { create_before_destroy = true }
  tags = { Name = "taskmanager-frontend-cert", Project = "taskmanager" }
}

resource "aws_acm_certificate" "backend" {
  provider          = aws.us_east_1
  domain_name       = var.backend_domain
  validation_method = "DNS"
  lifecycle { create_before_destroy = true }
  tags = { Name = "taskmanager-backend-cert", Project = "taskmanager" }
}

resource "aws_route53_record" "frontend_cert_validation" {
  for_each = {
    for dvo in aws_acm_certificate.frontend.domain_validation_options : dvo.domain_name => {
      name   = dvo.resource_record_name
      record = dvo.resource_record_value
      type   = dvo.resource_record_type
    }
  }
  allow_overwrite = true
  name            = each.value.name
  records         = [each.value.record]
  ttl             = 60
  type            = each.value.type
  zone_id         = data.aws_route53_zone.sriinfosoft.zone_id
}

resource "aws_route53_record" "backend_cert_validation" {
  for_each = {
    for dvo in aws_acm_certificate.backend.domain_validation_options : dvo.domain_name => {
      name   = dvo.resource_record_name
      record = dvo.resource_record_value
      type   = dvo.resource_record_type
    }
  }
  allow_overwrite = true
  name            = each.value.name
  records         = [each.value.record]
  ttl             = 60
  type            = each.value.type
  zone_id         = data.aws_route53_zone.sriinfosoft.zone_id
}

resource "aws_acm_certificate_validation" "frontend" {
  provider                = aws.us_east_1
  certificate_arn         = aws_acm_certificate.frontend.arn
  validation_record_fqdns = [for record in aws_route53_record.frontend_cert_validation : record.fqdn]
}

resource "aws_acm_certificate_validation" "backend" {
  provider                = aws.us_east_1
  certificate_arn         = aws_acm_certificate.backend.arn
  validation_record_fqdns = [for record in aws_route53_record.backend_cert_validation : record.fqdn]
}

# ============================================================
# 5. CLOUDFRONT DISTRIBUTIONS (SSL termination -- same as
#    existing AWS setup with taskmanager.sriinfosoft.com)
# ============================================================

resource "aws_cloudfront_distribution" "frontend" {
  enabled             = true
  is_ipv6_enabled     = true
  comment             = "taskmanager frontend"
  default_root_object = "index.html"
  aliases             = [var.frontend_domain]

  origin {
    domain_name = aws_instance.taskmanager.public_dns
    origin_id   = "taskmanager-frontend-origin"
    custom_origin_config {
      http_port              = 80
      https_port             = 443
      origin_protocol_policy = "http-only"
      origin_ssl_protocols   = ["TLSv1.2"]
    }
  }

  default_cache_behavior {
    allowed_methods        = ["DELETE", "GET", "HEAD", "OPTIONS", "PATCH", "POST", "PUT"]
    cached_methods         = ["GET", "HEAD"]
    target_origin_id       = "taskmanager-frontend-origin"
    viewer_protocol_policy = "redirect-to-https"
    compress               = true
    forwarded_values {
      query_string = true
      headers      = ["Origin", "Authorization"]
      cookies { forward = "all" }
    }
    min_ttl     = 0
    default_ttl = 0
    max_ttl     = 0
  }

  custom_error_response {
    error_code            = 404
    response_code         = 200
    response_page_path    = "/index.html"
    error_caching_min_ttl = 0
  }

  custom_error_response {
    error_code            = 403
    response_code         = 200
    response_page_path    = "/index.html"
    error_caching_min_ttl = 0
  }

  restrictions {
    geo_restriction { restriction_type = "none" }
  }

  viewer_certificate {
    acm_certificate_arn      = aws_acm_certificate_validation.frontend.certificate_arn
    ssl_support_method       = "sni-only"
    minimum_protocol_version = "TLSv1.2_2021"
  }

  tags = { Name = "taskmanager-frontend-cf", Project = "taskmanager" }
}

resource "aws_cloudfront_distribution" "backend" {
  enabled         = true
  is_ipv6_enabled = true
  comment         = "taskmanager backend api"
  aliases         = [var.backend_domain]

  origin {
    domain_name = aws_instance.taskmanager.public_dns
    origin_id   = "taskmanager-backend-origin"
    custom_origin_config {
      http_port              = 8080
      https_port             = 443
      origin_protocol_policy = "http-only"
      origin_ssl_protocols   = ["TLSv1.2"]
    }
  }

  default_cache_behavior {
    allowed_methods        = ["DELETE", "GET", "HEAD", "OPTIONS", "PATCH", "POST", "PUT"]
    cached_methods         = ["GET", "HEAD"]
    target_origin_id       = "taskmanager-backend-origin"
    viewer_protocol_policy = "redirect-to-https"
    compress               = true
    forwarded_values {
      query_string = true
      headers      = ["Origin", "Authorization", "Content-Type", "Accept"]
      cookies { forward = "all" }
    }
    min_ttl     = 0
    default_ttl = 0
    max_ttl     = 0
  }

  restrictions {
    geo_restriction { restriction_type = "none" }
  }

  viewer_certificate {
    acm_certificate_arn      = aws_acm_certificate_validation.backend.certificate_arn
    ssl_support_method       = "sni-only"
    minimum_protocol_version = "TLSv1.2_2021"
  }

  tags = { Name = "taskmanager-backend-cf", Project = "taskmanager" }
}

# ============================================================
# 6. ROUTE 53 RECORDS
# ============================================================

resource "aws_route53_record" "frontend" {
  zone_id = data.aws_route53_zone.sriinfosoft.zone_id
  name    = var.frontend_domain
  type    = "A"
  alias {
    name                   = aws_cloudfront_distribution.frontend.domain_name
    zone_id                = aws_cloudfront_distribution.frontend.hosted_zone_id
    evaluate_target_health = false
  }
}

resource "aws_route53_record" "backend" {
  zone_id = data.aws_route53_zone.sriinfosoft.zone_id
  name    = var.backend_domain
  type    = "A"
  alias {
    name                   = aws_cloudfront_distribution.backend.domain_name
    zone_id                = aws_cloudfront_distribution.backend.hosted_zone_id
    evaluate_target_health = false
  }
}
