# ============================================================
# variables.tf — Configurable values for GCP Task Manager
# ============================================================

variable "project_id" {
  description = "GCP project ID"
  type        = string
}

variable "region" {
  description = "GCP region for regional resources"
  type        = string
  default     = "us-west1"
}

variable "zone" {
  description = "GCP zone for the VM instance"
  type        = string
  default     = "us-west1-b"
}

variable "instance_name" {
  description = "Name of the GCE instance"
  type        = string
  default     = "gce-for-fullstack-apps-learning"
}

variable "machine_type" {
  description = "GCE machine type"
  type        = string
  default     = "e2-small"
}

variable "boot_disk_size" {
  description = "Boot disk size in GB"
  type        = number
  default     = 30
}

variable "boot_disk_image" {
  description = "Boot disk OS image"
  type        = string
  default     = "debian-cloud/debian-12"
}

variable "frontend_domain" {
  description = "Frontend domain for SSL cert"
  type        = string
  default     = "taskmanager.gcp.sriinfosoft.com"
}

variable "backend_domain" {
  description = "Backend API domain for SSL cert"
  type        = string
  default     = "api-taskmanager.gcp.sriinfosoft.com"
}

variable "ssh_user" {
  description = "SSH username for the instance"
  type        = string
  default     = "ranasuri"
}
