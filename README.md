# Medicine Batch Traceability and Recall Management System
Spring Boot 3 + JPA/Hibernate + MySQL. Login returns a token (X-Token header); role checks are done in code (no Spring Security framework, only BCrypt for password hashing). Needs Java 17, Maven, MySQL.

## Run
1. Set your MySQL password in `src/main/resources/application.properties`.
2. `mvn spring-boot:run`   (tables are created automatically)

## Roles: ADMIN (manufacturer), DISTRIBUTOR, PHARMACY
## Demo with curl
First log in each user to get a token, then send it as `-H "X-Token: <token>"` instead of `-u`:
```
curl -X POST localhost:8080/api/auth/login -H "Content-Type: application/json" -d '{"username":"abc","password":"pw"}'
```
In the commands below, replace `-u abc:pw` with `-H "X-Token: <abc token>"` (same idea for distA and phX).
```
curl -X POST localhost:8080/api/auth/register -H "Content-Type: application/json" -d '{"username":"abc","password":"pw","name":"ABC Pharma","role":"ADMIN"}'
curl -X POST localhost:8080/api/auth/register -H "Content-Type: application/json" -d '{"username":"distA","password":"pw","name":"Distributor A","role":"DISTRIBUTOR"}'
curl -X POST localhost:8080/api/auth/register -H "Content-Type: application/json" -d '{"username":"phX","password":"pw","name":"Pharmacy X","role":"PHARMACY"}'

# manufacturer creates batch, ships to distributor (ids: users 1,2,3; batch 1)
curl -u abc:pw -X POST localhost:8080/api/batches -H "Content-Type: application/json" -d '{"batchNo":"PCM-2026-001","medicineName":"Paracetamol 500mg","quantity":10000,"mfgDate":"2026-01-01","expiryDate":"2028-01-01"}'
curl -u abc:pw -X POST localhost:8080/api/shipments -H "Content-Type: application/json" -d '{"batchId":1,"receiverId":2,"quantity":6000}'
curl -u distA:pw localhost:8080/api/shipments/incoming
curl -u distA:pw -X POST localhost:8080/api/shipments/1/accept

# distributor ships to pharmacy, pharmacy accepts and sells
curl -u distA:pw -X POST localhost:8080/api/shipments -H "Content-Type: application/json" -d '{"batchId":1,"receiverId":3,"quantity":2500}'
curl -u phX:pw -X POST localhost:8080/api/shipments/2/accept
curl -u phX:pw -X POST localhost:8080/api/shipments/2/sell -H "Content-Type: application/json" -d '{"quantity":1800}'

# trace, recall, return, close
curl -u abc:pw localhost:8080/api/batches/1/trace
curl -u abc:pw -X POST localhost:8080/api/recalls -H "Content-Type: application/json" -d '{"batchId":1,"reason":"Failed lab test"}'
curl -u phX:pw localhost:8080/api/recalls/notices
curl -u phX:pw -X POST localhost:8080/api/shipments/2/return -H "Content-Type: application/json" -d '{"quantity":700}'
curl -u abc:pw localhost:8080/api/recalls/1/progress
curl -u abc:pw -X POST localhost:8080/api/recalls/1/close
```
Note: registering as ADMIN is open for demo purposes; in production only admins would create accounts.
