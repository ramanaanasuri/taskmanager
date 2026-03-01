# ============================================================
# main.tf — GCP Task Manager Infrastructure (exact replica)
#
# Recreates the old GCP account setup with identical names:
#   - GCE instance: gce-for-fullstack-apps-learning
#   - Static IPs: fullstack-apps-gcp-ip (regional + global)
#   - Firewall rules: default-allow-http, default-allow-https
#   - Load balancer: taskmanager-https-frontend
#   - SSL cert: taskmanager-ssl-cert (Google-managed)
#
# Usage:
#   terraform init
#   terraform plan
#   terraform apply
#
# Prerequisites:
#   - gcloud auth application-default login
#   - A GCP project with billing enabled
# ============================================================

terraform {
  required_version = ">= 1.5"
  required_providers {
    google = {
      source  = "hashicorp/google"
      version = "~> 5.0"
    }
  }
}

provider "google" {
  project = var.project_id
  region  = var.region
  zone    = var.zone
}

# ============================================================
# 1. ENABLE REQUIRED APIs
# ============================================================

resource "google_project_service" "compute" {
  service            = "compute.googleapis.com"
  disable_on_destroy = false
}

resource "google_project_service" "certificate_manager" {
  service            = "certificatemanager.googleapis.com"
  disable_on_destroy = false
}

# ============================================================
# 2. NETWORK — Using default VPC (same as old account)
# ============================================================
# The default VPC and subnets are auto-created in every GCP
# project. We reference them with data sources instead of
# creating new ones.

data "google_compute_network" "default" {
  name = "default"
}

data "google_compute_subnetwork" "default" {
  name   = "default"
  region = var.region
}

# ============================================================
# 3. FIREWALL RULES
# ============================================================

resource "google_compute_firewall" "allow_http" {
  name    = "default-allow-http"
  network = data.google_compute_network.default.name

  allow {
    protocol = "tcp"
    ports    = ["80"]
  }

  source_ranges = ["0.0.0.0/0"]
  target_tags   = ["http-server"]
  priority      = 1000
}

resource "google_compute_firewall" "allow_https" {
  name    = "default-allow-https"
  network = data.google_compute_network.default.name

  allow {
    protocol = "tcp"
    ports    = ["443"]
  }

  source_ranges = ["0.0.0.0/0"]
  target_tags   = ["https-server"]
  priority      = 1000
}

# Note: default-allow-ssh, default-allow-icmp, default-allow-internal
# are auto-created by GCP in the default VPC. No need to define them.

# ============================================================
# 4. STATIC IPs
# ============================================================

# Regional static IP — attached to the GCE instance
resource "google_compute_address" "regional_ip" {
  name         = "fullstack-apps-gcp-ip"
  region       = var.region
  address_type = "EXTERNAL"
  network_tier = "PREMIUM"
}

# Global static IP — used by the HTTPS load balancer
resource "google_compute_global_address" "global_ip" {
  name         = "fullstack-apps-gcp-global-ip"
  address_type = "EXTERNAL"
}

# ============================================================
# 5. GCE INSTANCE
# ============================================================

resource "google_compute_instance" "taskmanager_vm" {
  name         = var.instance_name
  machine_type = var.machine_type
  zone         = var.zone

  tags = ["http-server", "https-server"]

  boot_disk {
    auto_delete = true
    initialize_params {
      image = var.boot_disk_image
      size  = var.boot_disk_size
      type  = "pd-ssd"
    }
  }

  network_interface {
    network    = data.google_compute_network.default.name
    subnetwork = data.google_compute_subnetwork.default.name

    access_config {
      nat_ip       = google_compute_address.regional_ip.address
      network_tier = "PREMIUM"
    }
  }

  metadata = {
    enable-osconfig = "TRUE"
  }

  service_account {
    scopes = [
      "https://www.googleapis.com/auth/devstorage.read_only",
      "https://www.googleapis.com/auth/logging.write",
      "https://www.googleapis.com/auth/monitoring.write",
      "https://www.googleapis.com/auth/service.management.readonly",
      "https://www.googleapis.com/auth/servicecontrol",
      "https://www.googleapis.com/auth/trace.append",
    ]
  }

  # Install Docker on first boot
  metadata_startup_script = <<-EOF
    #!/bin/bash
    set -e

    # Install Docker
    apt-get update
    apt-get install -y ca-certificates curl gnupg
    install -m 0755 -d /etc/apt/keyrings
    curl -fsSL https://download.docker.com/linux/debian/gpg | gpg --dearmor -o /etc/apt/keyrings/docker.gpg
    chmod a+r /etc/apt/keyrings/docker.gpg
    echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/debian $(. /etc/os-release && echo $VERSION_CODENAME) stable" > /etc/apt/sources.list.d/docker.list
    apt-get update
    apt-get install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin

    # Add SSH user to docker group
    usermod -aG docker ${var.ssh_user} || true

    echo "Docker installed successfully"
  EOF

  depends_on = [google_project_service.compute]
}

# ============================================================
# 6. HEALTH CHECK
# ============================================================

resource "google_compute_health_check" "taskmanager_health" {
  name               = "taskmanager-health-check"
  check_interval_sec = 10
  timeout_sec        = 5
  healthy_threshold  = 2
  unhealthy_threshold = 3

  http_health_check {
    port         = 80
    request_path = "/"
  }
}

# ============================================================
# 7. INSTANCE GROUP (unmanaged, single VM)
# ============================================================

resource "google_compute_instance_group" "taskmanager_group" {
  name      = "taskmanager-instance-group"
  zone      = var.zone
  instances = [google_compute_instance.taskmanager_vm.self_link]

  named_port {
    name = "http"
    port = 80
  }

  named_port {
    name = "backend"
    port = 8080
  }
}

# ============================================================
# 8. BACKEND SERVICE
# ============================================================

resource "google_compute_backend_service" "taskmanager_backend" {
  name                  = "taskmanager-backend-service"
  protocol              = "HTTP"
  port_name             = "http"
  timeout_sec           = 30
  load_balancing_scheme = "EXTERNAL_MANAGED"

  health_checks = [google_compute_health_check.taskmanager_health.self_link]

  backend {
    group           = google_compute_instance_group.taskmanager_group.self_link
    balancing_mode  = "UTILIZATION"
    max_utilization = 0.8
  }
}

# ============================================================
# 9. URL MAP
# ============================================================

resource "google_compute_url_map" "taskmanager_url_map" {
  name            = "taskmanager-url-map"
  default_service = google_compute_backend_service.taskmanager_backend.self_link
}

# HTTP-to-HTTPS redirect URL map
resource "google_compute_url_map" "taskmanager_http_redirect" {
  name = "taskmanager-http-redirect"

  default_url_redirect {
    https_redirect         = true
    redirect_response_code = "MOVED_PERMANENTLY_DEFAULT"
    strip_query            = false
  }
}

# ============================================================
# 10. SSL CERTIFICATE (Google-managed)
# ============================================================

resource "google_compute_managed_ssl_certificate" "taskmanager_cert" {
  name = "taskmanager-ssl-cert"

  managed {
    domains = [
      var.frontend_domain,
      var.backend_domain,
    ]
  }
}

# ============================================================
# 11. TARGET PROXIES
# ============================================================

# HTTPS proxy (port 443)
resource "google_compute_target_https_proxy" "taskmanager_https" {
  name             = "taskmanager-https-lb-target-proxy"
  url_map          = google_compute_url_map.taskmanager_url_map.self_link
  ssl_certificates = [google_compute_managed_ssl_certificate.taskmanager_cert.self_link]
}

# HTTP proxy (port 80, redirects to HTTPS)
resource "google_compute_target_http_proxy" "taskmanager_http" {
  name    = "taskmanager-https-frontend-target-proxy"
  url_map = google_compute_url_map.taskmanager_http_redirect.self_link
}

# ============================================================
# 12. FORWARDING RULES
# ============================================================

# HTTPS forwarding rule (port 443)
resource "google_compute_global_forwarding_rule" "taskmanager_https" {
  name                  = "taskmanager-https-frontend"
  ip_address            = google_compute_global_address.global_ip.address
  ip_protocol           = "TCP"
  port_range            = "443"
  target                = google_compute_target_https_proxy.taskmanager_https.self_link
  load_balancing_scheme = "EXTERNAL_MANAGED"
}

# HTTP forwarding rule (port 80, redirects to HTTPS)
resource "google_compute_global_forwarding_rule" "taskmanager_http" {
  name                  = "taskmanager-https-frontend-forwarding-rule"
  ip_address            = google_compute_global_address.global_ip.address
  ip_protocol           = "TCP"
  port_range            = "80"
  target                = google_compute_target_http_proxy.taskmanager_http.self_link
  load_balancing_scheme = "EXTERNAL_MANAGED"
}
