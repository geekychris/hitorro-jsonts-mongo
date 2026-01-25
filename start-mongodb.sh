#!/bin/bash

# Start MongoDB using Docker Compose
# This script provides a simple way to start MongoDB for development

set -e

echo "Starting MongoDB with Docker Compose..."
echo ""

# Check if Docker is running
if ! docker info > /dev/null 2>&1; then
    echo "Error: Docker is not running. Please start Docker Desktop first."
    exit 1
fi

# Parse command line arguments
WITH_ADMIN=false
while [[ $# -gt 0 ]]; do
    case $1 in
        --with-admin)
            WITH_ADMIN=true
            shift
            ;;
        --help|-h)
            echo "Usage: ./start-mongodb.sh [OPTIONS]"
            echo ""
            echo "Options:"
            echo "  --with-admin    Also start Mongo Express admin interface on http://localhost:8081"
            echo "  --help, -h      Show this help message"
            echo ""
            echo "Examples:"
            echo "  ./start-mongodb.sh                  Start MongoDB only"
            echo "  ./start-mongodb.sh --with-admin     Start MongoDB + Mongo Express"
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            echo "Run './start-mongodb.sh --help' for usage information"
            exit 1
            ;;
    esac
done

# Start Docker Compose
if [ "$WITH_ADMIN" = true ]; then
    echo "Starting MongoDB with Mongo Express admin interface..."
    docker-compose --profile with-admin up -d
    echo ""
    echo "✅ MongoDB is running on mongodb://localhost:27017"
    echo "✅ Mongo Express admin interface: http://localhost:8081"
    echo "   Username: admin"
    echo "   Password: admin"
else
    docker-compose up -d mongodb
    echo ""
    echo "✅ MongoDB is running on mongodb://localhost:27017"
    echo ""
    echo "To also start the web admin interface, run:"
    echo "   ./start-mongodb.sh --with-admin"
fi

echo ""
echo "Default database: testdb"
echo ""
echo "To stop MongoDB, run:"
echo "   docker-compose down"
echo ""
echo "To stop and remove all data, run:"
echo "   docker-compose down -v"
