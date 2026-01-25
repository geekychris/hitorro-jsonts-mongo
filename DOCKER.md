# MongoDB Docker Setup

This directory includes Docker configurations and scripts to easily run MongoDB locally for development and testing.

## Quick Start

### Option 1: Using Docker Compose (Recommended)

**Start MongoDB:**
```bash
./start-mongodb.sh
```

**Start MongoDB with Web Admin Interface:**
```bash
./start-mongodb.sh --with-admin
```

This starts:
- MongoDB on `mongodb://localhost:27017`
- Mongo Express admin UI on `http://localhost:8081` (username: `admin`, password: `admin`)

**Stop MongoDB:**
```bash
./stop-mongodb.sh
```

**Stop and Remove All Data:**
```bash
./stop-mongodb.sh --remove-data
```

### Option 2: Using Simple Docker Script

**Start MongoDB:**
```bash
./docker-mongodb.sh start
```

**Start with Authentication:**
```bash
./docker-mongodb.sh start --with-auth
```

**View Logs:**
```bash
./docker-mongodb.sh logs
```

**Connect to MongoDB Shell:**
```bash
./docker-mongodb.sh shell
```

**Stop MongoDB:**
```bash
./docker-mongodb.sh stop
```

**Remove Container and Data:**
```bash
./docker-mongodb.sh remove
```

## Connection Strings

### Without Authentication (Default)
```
mongodb://localhost:27017/testdb
```

**Java Code:**
```java
JVSReactiveMongoClient client = JVSReactiveMongoClient.builder()
    .connectionString("mongodb://localhost:27017")
    .database("testdb")
    .build();
```

### With Authentication
```
mongodb://admin:password@localhost:27017/testdb?authSource=admin
```

**Java Code:**
```java
JVSReactiveMongoClient client = JVSReactiveMongoClient.builder()
    .connectionString("mongodb://admin:password@localhost:27017/testdb?authSource=admin")
    .database("testdb")
    .build();
```

## Docker Compose Configuration

The `docker-compose.yml` file includes:

- **MongoDB** - Latest MongoDB server
  - Port: 27017
  - Default database: testdb
  - Persistent data volume
  
- **Mongo Express** (optional, use `--with-admin` flag)
  - Port: 8081
  - Web-based MongoDB admin interface
  - Access: http://localhost:8081
  - Username: `admin`
  - Password: `admin`

## Manual Docker Commands

If you prefer to use Docker directly:

**Start MongoDB (no authentication):**
```bash
docker run -d \
  --name hitorro-mongodb \
  -p 27017:27017 \
  -e MONGO_INITDB_DATABASE=testdb \
  -v hitorro-mongodb-data:/data/db \
  mongo:latest
```

**Start MongoDB (with authentication):**
```bash
docker run -d \
  --name hitorro-mongodb \
  -p 27017:27017 \
  -e MONGO_INITDB_ROOT_USERNAME=admin \
  -e MONGO_INITDB_ROOT_PASSWORD=password \
  -e MONGO_INITDB_DATABASE=testdb \
  -v hitorro-mongodb-data:/data/db \
  mongo:latest
```

**Stop:**
```bash
docker stop hitorro-mongodb
```

**Remove container:**
```bash
docker rm -f hitorro-mongodb
```

**Remove volume (deletes data):**
```bash
docker volume rm hitorro-mongodb-data
```

## Useful Commands

**View running containers:**
```bash
docker ps
```

**View MongoDB logs:**
```bash
docker logs hitorro-mongodb
# or follow logs
docker logs -f hitorro-mongodb
```

**Connect to MongoDB shell:**
```bash
docker exec -it hitorro-mongodb mongosh
```

**Inside mongosh:**
```javascript
// Show databases
show dbs

// Use testdb
use testdb

// Show collections
show collections

// Find all documents in a collection
db.users.find()

// Count documents
db.users.countDocuments()

// Drop collection
db.users.drop()
```

## Troubleshooting

**Port 27017 already in use:**
```bash
# Check what's using the port
lsof -i :27017

# If it's another MongoDB instance, stop it
brew services stop mongodb-community
# or
docker stop hitorro-mongodb
```

**Docker not running:**
```bash
# On macOS, start Docker Desktop
open -a Docker
```

**Cannot connect to MongoDB:**
```bash
# Check if container is running
docker ps | grep hitorro-mongodb

# Check logs for errors
docker logs hitorro-mongodb

# Restart container
docker restart hitorro-mongodb
```

**Reset everything:**
```bash
# Stop and remove container
docker rm -f hitorro-mongodb

# Remove data volume
docker volume rm hitorro-mongodb-data

# Or use docker-compose
docker-compose down -v
```

## Data Persistence

Data is stored in Docker volumes:
- **Docker Compose**: `mongodb_data` volume
- **Simple Docker**: `hitorro-mongodb-data` volume

This means your data persists even when you stop/restart containers. To completely remove data, use:
```bash
./stop-mongodb.sh --remove-data
# or
./docker-mongodb.sh remove
```
