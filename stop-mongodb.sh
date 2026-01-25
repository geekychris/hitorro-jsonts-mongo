#!/bin/bash

# Stop MongoDB Docker containers
# This script provides options to stop with or without removing data

set -e

echo "Stopping MongoDB..."
echo ""

# Parse command line arguments
REMOVE_DATA=false
while [[ $# -gt 0 ]]; do
    case $1 in
        --remove-data)
            REMOVE_DATA=true
            shift
            ;;
        --help|-h)
            echo "Usage: ./stop-mongodb.sh [OPTIONS]"
            echo ""
            echo "Options:"
            echo "  --remove-data   Stop and remove all MongoDB data (volumes)"
            echo "  --help, -h      Show this help message"
            echo ""
            echo "Examples:"
            echo "  ./stop-mongodb.sh                Stop MongoDB (keep data)"
            echo "  ./stop-mongodb.sh --remove-data  Stop MongoDB and delete all data"
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            echo "Run './stop-mongodb.sh --help' for usage information"
            exit 1
            ;;
    esac
done

if [ "$REMOVE_DATA" = true ]; then
    echo "⚠️  Warning: This will remove all MongoDB data!"
    read -p "Are you sure? (y/N) " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        docker-compose down -v
        echo "✅ MongoDB stopped and all data removed"
    else
        echo "Operation cancelled"
        exit 0
    fi
else
    docker-compose down
    echo "✅ MongoDB stopped (data preserved)"
    echo ""
    echo "To remove all data, run:"
    echo "   ./stop-mongodb.sh --remove-data"
fi
