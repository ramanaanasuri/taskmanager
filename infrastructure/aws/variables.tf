variable "region" {
  description = "AWS region"
  type        = string
  default     = "us-west-1"
}

variable "instance_name" {
  description = "EC2 instance name"
  type        = string
  default     = "taskmanager-aws"
}

variable "instance_type" {
  description = "EC2 instance type (t2.micro = free tier)"
  type        = string
  default     = "t2.micro"
}

variable "ami_id" {
  description = "Amazon Linux 2023 AMI -- same as existing instance"
  type        = string
  default     = "ami-004374a3d56f732a6"
}

variable "key_pair_name" {
  description = "EC2 Key Pair name"
  type        = string
}

variable "admin_ssh_cidr" {
  description = "Your IP for SSH (e.g. 1.2.3.4/32)"
  type        = string
}

variable "frontend_domain" {
  description = "Frontend domain"
  type        = string
  default     = "tm.sriinfosoft.com"
}

variable "backend_domain" {
  description = "Backend API domain"
  type        = string
  default     = "api-tm.sriinfosoft.com"
}
