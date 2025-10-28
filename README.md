# RepliedTest

## Prerequisites
- **Java 21**
- **Maven 3.9+**
- **Docker** for Neo4j
- Open ports: **8080** (API), **7687/7474** (Neo4j)

## Clone
```bash
git clone <repo>
cd <repo>
```
## Start Neo4j (docker-compose already present)
```bash
docker compose up -d
```

## Build & Run
```bash
mvn clean install -U
mvn spring-boot:run
```

## Verify
Health: http://localhost:8080/actuator/health should return "UP"

Swagger UI: http://localhost:8080/swagger-ui.html for API docs and testing


