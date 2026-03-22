output "instance_id" {
  description = "EC2 instance ID"
  value       = aws_instance.taskmanager.id
}

output "instance_public_ip" {
  description = "EC2 public IP"
  value       = aws_instance.taskmanager.public_ip
}

output "ssh_command" {
  description = "SSH into the instance"
  value       = "ssh -i ~/.ssh/${var.key_pair_name}.pem ec2-user@${aws_instance.taskmanager.public_ip}"
}

output "frontend_url" {
  description = "Frontend URL"
  value       = "https://${var.frontend_domain}"
}

output "backend_url" {
  description = "Backend API URL"
  value       = "https://${var.backend_domain}"
}

output "cloudfront_frontend_domain" {
  description = "Raw CloudFront domain for frontend"
  value       = aws_cloudfront_distribution.frontend.domain_name
}

output "cloudfront_backend_domain" {
  description = "Raw CloudFront domain for backend"
  value       = aws_cloudfront_distribution.backend.domain_name
}

output "dns_instructions" {
  description = "DNS records created in Route 53"
  value       = <<-EOT
    Route 53 A records created:
    ${var.frontend_domain} -> ${aws_cloudfront_distribution.frontend.domain_name}
    ${var.backend_domain}  -> ${aws_cloudfront_distribution.backend.domain_name}
  EOT
}
