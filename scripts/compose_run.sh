#!/bin/bash

# Docker Compose run script for Market Price Server

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

cd "$PROJECT_DIR"

# Check if .env file exists
if [ ! -f .env ]; then
    echo "Error: .env file not found in $PROJECT_DIR"
    echo "Please create .env file with required configuration."
    exit 1
fi

# Parse command
COMMAND="${1:-up}"

case "$COMMAND" in
    up)
        echo "Starting services with Docker Compose..."
        docker compose up -d --build
        echo ""
        echo "Services started successfully!"
        echo "Price Server API: http://localhost:$(grep HTTP_PORT .env | cut -d'=' -f2)"
        echo "ClickHouse HTTP: http://localhost:$(grep CLICKHOUSE_HTTP_PORT .env | cut -d'=' -f2)"
        echo ""
        echo "View logs: docker compose logs -f"
        echo "Stop services: ./scripts/compose_run.sh down"
        ;;
    down)
        echo "Stopping services..."
        docker compose down
        echo "Services stopped."
        ;;
    restart)
        echo "Restarting services..."
        docker compose restart
        echo "Services restarted."
        ;;
    logs)
        docker compose logs -f
        ;;
    ps)
        docker compose ps
        ;;
    build)
        echo "Building services..."
        docker compose build
        echo "Build completed."
        ;;
    clean)
        echo "Stopping and removing all containers, networks, and volumes..."
        docker compose down -v
        echo "Cleanup completed."
        ;;
    *)
        echo "Usage: $0 {up|down|restart|logs|ps|build|clean}"
        echo ""
        echo "Commands:"
        echo "  up      - Start all services (default)"
        echo "  down    - Stop all services"
        echo "  restart - Restart all services"
        echo "  logs    - View logs (follow mode)"
        echo "  ps      - Show running services"
        echo "  build   - Build Docker images"
        echo "  clean   - Stop services and remove volumes"
        exit 1
        ;;
esac
