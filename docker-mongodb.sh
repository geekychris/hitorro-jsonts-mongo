#!/bin/bash

# Simple script to run MongoDB in Docker without docker-compose
# Useful for quick testing or when docker-compose is not available

set -e

CONTAINER_NAME="hitorro-mongodb"
MONGO_PORT="27017"
VOLUME_NAME="hitorro-mongodb-data"

# Parse command line arguments
COMMAND="start"
WITH_AUTH=false

while [[ $# -gt 0 ]]; do
    case $1 in
        start)
            COMMAND="start"
            shift
            ;;
        stop)
            COMMAND="stop"
            shift
            ;;
        restart)
            COMMAND="restart"
            shift
            ;;
        logs)
            COMMAND="logs"
            shift
            ;;
        shell)
            COMMAND="shell"
            shift
            ;;
        remove)
            COMMAND="remove"
            shift
            ;;
        --with-auth)
            WITH_AUTH=true
            shift
            ;;
        --help|-h)
            echo "Usage: ./docker-mongodb.sh [COMMAND] [OPTIONS]"
            echo ""
            echo "Commands:"
            echo "  start      Start MongoDB container (default)"
            echo "  stop       Stop MongoDB container"
            echo "  restart    Restart MongoDB container"
            echo "  logs       View MongoDB logs"
            echo "  shell      Connect to MongoDB shell"
            echo "  remove     Stop and remove container + data"
            echo ""
            echo "Options:"
            echo "  --with-auth    Enable authentication (user: admin, password: password)"
            echo "  --help, -h     Show this help message"
            echo ""
            echo "Examples:"
            echo "  ./docker-mongodb.sh start              Start MongoDB"
            echo "  ./docker-mongodb.sh start --with-auth  Start MongoDB with auth"
            echo "  ./docker-mongodb.sh logs               View logs"
            echo "  ./docker-mongodb.sh shell              Open mongo shell"
            echo "  ./docker-mongodb.sh stop               Stop MongoDB"
            echo "  ./docker-mongodb.sh remove             Remove everything"
            exit 0
            ;;
        *)
            echo "Unknown command or option: $1"
            echo "Run './docker-mongodb.sh --help' for usage information"
            exit 1
            ;;
    esac
done

case $COMMAND in
    start)
        # Check if container already exists
        if docker ps -a --format '{{.Names}}' | grep -q "^${CONTAINER_NAME}$"; then
            echo "Container ${CONTAINER_NAME} already exists."
            if docker ps --format '{{.Names}}' | grep -q "^${CONTAINER_NAME}$"; then
                echo "✅ MongoDB is already running on mongodb://localhost:${MONGO_PORT}"
                exit 0
            else
                echo "Starting existing container..."
                docker start ${CONTAINER_NAME}
                echo "✅ MongoDB started on mongodb://localhost:${MONGO_PORT}"
                exit 0
            fi
        fi

        echo "Starting new MongoDB container..."
        
        if [ "$WITH_AUTH" = true ]; then
            docker run -d \
              --name ${CONTAINER_NAME} \
              -p ${MONGO_PORT}:27017 \
              -e MONGO_INITDB_ROOT_USERNAME=admin \
              -e MONGO_INITDB_ROOT_PASSWORD=password \
              -e MONGO_INITDB_DATABASE=testdb \
              -v ${VOLUME_NAME}:/data/db \
              mongo:latest
            
            echo "✅ MongoDB started on mongodb://localhost:${MONGO_PORT}"
            echo "   Connection string: mongodb://admin:password@localhost:${MONGO_PORT}/testdb?authSource=admin"
            echo "   Username: admin"
            echo "   Password: password"
        else
            docker run -d \
              --name ${CONTAINER_NAME} \
              -p ${MONGO_PORT}:27017 \
              -e MONGO_INITDB_DATABASE=testdb \
              -v ${VOLUME_NAME}:/data/db \
              mongo:latest
            
            echo "✅ MongoDB started on mongodb://localhost:${MONGO_PORT}"
            echo "   Connection string: mongodb://localhost:${MONGO_PORT}/testdb"
            echo "   No authentication required"
        fi
        
        echo ""
        echo "Default database: testdb"
        ;;
    
    stop)
        if docker ps --format '{{.Names}}' | grep -q "^${CONTAINER_NAME}$"; then
            docker stop ${CONTAINER_NAME}
            echo "✅ MongoDB stopped"
        else
            echo "MongoDB is not running"
        fi
        ;;
    
    restart)
        if docker ps -a --format '{{.Names}}' | grep -q "^${CONTAINER_NAME}$"; then
            docker restart ${CONTAINER_NAME}
            echo "✅ MongoDB restarted"
        else
            echo "Error: Container ${CONTAINER_NAME} does not exist"
            echo "Run './docker-mongodb.sh start' to create it"
            exit 1
        fi
        ;;
    
    logs)
        if docker ps -a --format '{{.Names}}' | grep -q "^${CONTAINER_NAME}$"; then
            docker logs -f ${CONTAINER_NAME}
        else
            echo "Error: Container ${CONTAINER_NAME} does not exist"
            exit 1
        fi
        ;;
    
    shell)
        if docker ps --format '{{.Names}}' | grep -q "^${CONTAINER_NAME}$"; then
            echo "Connecting to MongoDB shell..."
            docker exec -it ${CONTAINER_NAME} mongosh
        else
            echo "Error: MongoDB is not running"
            echo "Run './docker-mongodb.sh start' first"
            exit 1
        fi
        ;;
    
    remove)
        echo "⚠️  Warning: This will remove the MongoDB container and all data!"
        read -p "Are you sure? (y/N) " -n 1 -r
        echo
        if [[ $REPLY =~ ^[Yy]$ ]]; then
            if docker ps -a --format '{{.Names}}' | grep -q "^${CONTAINER_NAME}$"; then
                docker rm -f ${CONTAINER_NAME}
            fi
            if docker volume ls --format '{{.Name}}' | grep -q "^${VOLUME_NAME}$"; then
                docker volume rm ${VOLUME_NAME}
            fi
            echo "✅ MongoDB container and data removed"
        else
            echo "Operation cancelled"
        fi
        ;;
esac
