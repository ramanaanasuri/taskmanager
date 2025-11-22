#!/bin/bash

################################################################################
# CODE VERIFICATION SCRIPT
# Searches for any string in source code and deployed containers
#
# Usage: ./verify_changes.sh <component> <search_string> [file_pattern]
#
# Examples:
#   ./verify_changes.sh frontend "taskId="
#   ./verify_changes.sh frontend "data-task-id" "*.js"
#   ./verify_changes.sh backend "GET.*tasks.*{id}" "*.java"
################################################################################

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Input parameters
COMPONENT="${1}"
SEARCH_STRING="${2}"
FILE_PATTERN="${3:-*}"

# Project paths
PROJECT_ROOT="$HOME/sriinfo/taskmanager"
FRONTEND_SRC="$PROJECT_ROOT/apps/frontend"
BACKEND_SRC="$PROJECT_ROOT/apps/backend"

################################################################################
# HELPER FUNCTIONS
################################################################################

print_header() {
    echo -e "\n${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${BLUE}  $1${NC}"
    echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
}

print_success() {
    echo -e "${GREEN}✅ $1${NC}"
}

print_error() {
    echo -e "${RED}❌ $1${NC}"
}

print_info() {
    echo -e "${CYAN}ℹ️  $1${NC}"
}

print_result() {
    echo -e "${YELLOW}   $1${NC}"
}

show_usage() {
    echo "Usage: $0 <component> <search_string> [file_pattern]"
    echo ""
    echo "Parameters:"
    echo "  component      : frontend or backend"
    echo "  search_string  : Text or regex pattern to search for"
    echo "  file_pattern   : Optional file pattern (default: *)"
    echo ""
    echo "Examples:"
    echo "  $0 frontend 'taskId='"
    echo "  $0 frontend 'data-task-id' '*.js'"
    echo "  $0 backend 'GET.*tasks.*{id}' '*.java'"
    echo "  $0 frontend 'console.log' 'App.js'"
    echo ""
    exit 1
}

################################################################################
# VALIDATION
################################################################################

if [ -z "$COMPONENT" ] || [ -z "$SEARCH_STRING" ]; then
    print_error "Missing required parameters"
    show_usage
fi

if [[ ! "$COMPONENT" =~ ^(frontend|backend)$ ]]; then
    print_error "Invalid component: $COMPONENT (must be 'frontend' or 'backend')"
    show_usage
fi

################################################################################
# SEARCH FUNCTIONS
################################################################################

search_in_source() {
    local source_dir=$1
    local search_str=$2
    local pattern=$3
    
    print_header "SEARCHING IN SOURCE CODE"
    print_info "Directory: $source_dir"
    print_info "Pattern: $search_str"
    print_info "Files: $pattern"
    echo ""
    
    if [ ! -d "$source_dir" ]; then
        print_error "Source directory not found: $source_dir"
        return 1
    fi
    
    # Find files matching pattern
    local files_found=$(find "$source_dir" -type f -name "$pattern" 2>/dev/null | wc -l)
    print_info "Files matching pattern: $files_found"
    echo ""
    
    if [ "$files_found" -eq 0 ]; then
        print_error "No files matching pattern '$pattern' found"
        return 1
    fi
    
    # Search for string in those files
    local matches=0
    local match_details=""
    
    while IFS= read -r file; do
        if grep -q "$search_str" "$file" 2>/dev/null; then
            ((matches++))
            local relative_path="${file#$source_dir/}"
            match_details+="  📄 File: $relative_path\n"
            
            # Show matching lines with line numbers
            while IFS= read -r line; do
                match_details+="     $line\n"
            done < <(grep -n "$search_str" "$file" | head -5)
            
            match_details+="\n"
        fi
    done < <(find "$source_dir" -type f -name "$pattern" 2>/dev/null)
    
    if [ "$matches" -gt 0 ]; then
        print_success "Found '$search_str' in $matches file(s)"
        echo ""
        echo -e "$match_details"
        return 0
    else
        print_error "Pattern '$search_str' NOT found in any source files"
        return 1
    fi
}

search_in_container() {
    local container_name=$1
    local search_str=$2
    local pattern=$3
    
    print_header "SEARCHING IN DEPLOYED CONTAINER"
    print_info "Container: $container_name"
    print_info "Pattern: $search_str"
    print_info "Files: $pattern"
    echo ""
    
    # Check if container is running
    if ! docker ps --format "{{.Names}}" | grep -q "^${container_name}$"; then
        print_error "Container '$container_name' is not running"
        echo ""
        echo "Available containers:"
        docker ps --format "  - {{.Names}}"
        return 1
    fi
    
    print_success "Container is running"
    echo ""
    
    # Determine search path based on component
    local search_path
    if [[ "$container_name" == *"frontend"* ]]; then
        search_path="/usr/share/nginx/html"
    elif [[ "$container_name" == *"backend"* ]]; then
        search_path="/app"
    else
        search_path="/"
    fi
    
    print_info "Searching in: $search_path"
    echo ""
    
    # Count files matching pattern
    local files_found=$(docker exec "$container_name" find "$search_path" -type f -name "$pattern" 2>/dev/null | wc -l)
    print_info "Files matching pattern: $files_found"
    echo ""
    
    if [ "$files_found" -eq 0 ]; then
        print_error "No files matching pattern '$pattern' found in container"
        return 1
    fi
    
    # Search for string in container files
    local matches=0
    local match_details=""
    
    while IFS= read -r file; do
        if docker exec "$container_name" grep -q "$search_str" "$file" 2>/dev/null; then
            ((matches++))
            match_details+="  📄 File: $file\n"
            
            # Show matching lines
            while IFS= read -r line; do
                match_details+="     $line\n"
            done < <(docker exec "$container_name" grep -n "$search_str" "$file" 2>/dev/null | head -5)
            
            match_details+="\n"
        fi
    done < <(docker exec "$container_name" find "$search_path" -type f -name "$pattern" 2>/dev/null)
    
    if [ "$matches" -gt 0 ]; then
        print_success "Found '$search_str' in $matches deployed file(s)"
        echo ""
        echo -e "$match_details"
        return 0
    else
        print_error "Pattern '$search_str' NOT found in any deployed files"
        return 1
    fi
}

################################################################################
# MAIN EXECUTION
################################################################################

main() {
    clear
    echo -e "${GREEN}"
    echo "╔════════════════════════════════════════════════════════════════════════════╗"
    echo "║                    GENERIC CODE VERIFICATION                               ║"
    echo "╚════════════════════════════════════════════════════════════════════════════╝"
    echo -e "${NC}"
    
    echo -e "${CYAN}Component:${NC}     $COMPONENT"
    echo -e "${CYAN}Search for:${NC}    $SEARCH_STRING"
    echo -e "${CYAN}File pattern:${NC}  $FILE_PATTERN"
    echo ""
    
    local errors=0
    
    # Set paths and container based on component
    if [ "$COMPONENT" == "frontend" ]; then
        SOURCE_DIR="$FRONTEND_SRC"
        CONTAINER_NAME="taskmanager-frontend"
    elif [ "$COMPONENT" == "backend" ]; then
        SOURCE_DIR="$BACKEND_SRC"
        CONTAINER_NAME="taskmanager-backend"
    fi
    
    # Search in source
    search_in_source "$SOURCE_DIR" "$SEARCH_STRING" "$FILE_PATTERN" || ((errors++))
    
    # Search in container
    search_in_container "$CONTAINER_NAME" "$SEARCH_STRING" "$FILE_PATTERN" || ((errors++))
    
    # Summary
    print_header "VERIFICATION SUMMARY"
    
    if [ $errors -eq 0 ]; then
        print_success "✨ Pattern found in BOTH source and container! ✨"
        echo ""
        print_info "Your code is correctly deployed!"
        exit 0
    elif [ $errors -eq 1 ]; then
        print_error "⚠️  Pattern found in only ONE location ⚠️"
        echo ""
        print_info "If found in source but not container: rebuild and redeploy"
        print_info "If found in container but not source: source code may be outdated"
        echo ""
        echo "Rebuild command:"
        echo "  cd ~/sriinfo/taskmanager"
        echo "  docker compose build $COMPONENT --no-cache"
        echo "  docker compose up -d $COMPONENT"
        exit 1
    else
        print_error "❌ Pattern NOT found in source OR container ❌"
        echo ""
        print_info "Double-check your search string and file pattern"
        print_info "The pattern may be using regex - ensure proper escaping"
        exit 2
    fi
}

# Run main
main
