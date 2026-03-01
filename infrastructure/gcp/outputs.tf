# ============================================================
# outputs.tf — Values printed after terraform apply
# ============================================================

output "instance_name" {
  description = "GCE instance name"
  value       = google_compute_instance.taskmanager_vm.name
}

output "instance_zone" {
  description = "GCE instance zone"
  value       = google_compute_instance.taskmanager_vm.zone
}

output "regional_ip" {
  description = "Static IP attached to GCE instance"
  value       = google_compute_address.regional_ip.address
}

output "global_lb_ip" {
  description = "Global IP for the HTTPS load balancer (point DNS here)"
  value       = google_compute_global_address.global_ip.address
}

output "ssl_cert_name" {
  description = "Google-managed SSL certificate name"
  value       = google_compute_managed_ssl_certificate.taskmanager_cert.name
}

output "ssl_cert_domains" {
  description = "Domains covered by the SSL certificate"
  value       = google_compute_managed_ssl_certificate.taskmanager_cert.managed[0].domains
}

output "ssh_command" {
  description = "SSH into the instance"
  value       = "gcloud compute ssh ${var.ssh_user}@${var.instance_name} --zone=${var.zone}"
}

output "dns_instructions" {
  description = "DNS records to create in Route 53"
  value       = <<-EOT
    Create these A records in Route 53 (sriinfosoft.com hosted zone):
      ${var.frontend_domain}  ->  ${google_compute_global_address.global_ip.address}
      ${var.backend_domain}   ->  ${google_compute_global_address.global_ip.address}
  EOT
}
