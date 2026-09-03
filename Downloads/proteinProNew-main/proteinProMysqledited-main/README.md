# Protein Pro App Backend

Protein Pro App is a Java 17/Spring Boot microservices backend for profile registration, secure login, external protein-data queries, and user-owned bookmarks with comments. Authentication and User Profile each own a separate MySQL database; Bookmark owns MongoDB; Protein remains stateless and reads the configured external API. Kafka is a real external event bus, OpenFeign handles synchronous calls, Eureka handles discovery, Config Server centralizes configuration, and API Gateway is the public entry point.

This repository intentionally contains no Docker or Docker Compose files.

## Architecture

```text
Client
  |
  | HTTP + optional Bearer JWT
  v
API Gateway :8080 ----> Eureka :8761
  |        |       |       |
  v        v       v       v
Profile  Auth   Protein  Bookmark
 :8082  :8081   :8083    :8084
  |       |       |        |
 MySQL   MySQL   External  MongoDB
  |       ^      Protein     |
  | Feign |       API        |
  +------>|                  |
  |                          |
  +---------- Kafka :9092 ---+

Config Server :8888 supplies centralized configuration to every service.
```

All external application requests use `http://localhost:8080`. Calling protected downstream ports directly does not bypass authorization: Authentication, User Profile, and Bookmark validate the same signed JWT themselves.

## Modules and responsibilities

| Module | Port | Responsibility | Database | Kafka | Feign |
|---|---:|---|---|---|---|
| `config-server` | 8888 | Centralized native configuration | None | None | None |
| `discovery-server` | 8761 | Eureka registry/discovery | None | None | None |
| `api-gateway` | 8080 | Routes, load balancing, JWT validation, CORS | None | None | None |
| `authentication-service` | 8081 | BCrypt credentials, login/JWT, reset, logout semantics | MySQL `authentication_db` | Consumes profile creation; publishes login/reset events | None |
| `user-profile-service` | 8082 | Register and manage personal details | MySQL `user_profile_db` | Publishes create/update events | Authentication credential creation |
| `protein-service` | 8083 | Read/search the configured external Protein API | None | No invented event workflow | External Protein API |
| `bookmark-service` | 8084 | Authenticated user's bookmarks and comments | `proteinpro_bookmarks` | Publishes create/update/delete events | None |

## Technology versions

- Java 17+
- Maven 3.9+
- Spring Boot 3.3.5
- Spring Cloud 2023.0.3
- MySQL Community 8.0 recommended for compatibility with the Spring Boot-managed Flyway version
- MongoDB Community 8.0 for Bookmark
- Apache Kafka 3.9.2 in KRaft mode recommended

Spring's JDBC/JPA, MongoDB, and Kafka clients connect to external infrastructure; no production code embeds or simulates a database or broker.

## Database ownership and schema

Each stateful service owns its connection and never reads another service's database.

### User Profile — MySQL

- Database: `user_profile_db`
- Table: `user_profiles`
- Columns: `id`, `first_name`, `last_name`, `email`, `created_at`, `updated_at`
- Constraints: primary key `id`; unique `email`; required profile and timestamp columns
- Queries: ID lookup for `/me`; duplicate-email check during registration
- The existing UUID string ID is preserved as `VARCHAR(36)` across JWT, Feign, Kafka, Bookmark references, and API responses.
- Passwords are never accepted by the entity or returned by profile APIs.

### Authentication — MySQL

- Database: `authentication_db`
- Table: `credentials`
- Columns: `id`, `user_id`, `email`, `password_hash`, `active`, `created_at`, `updated_at`
- Constraints: primary key `id`; unique `user_id`; unique `email`; required credential and timestamp columns
- Queries: email lookup for login; user ID lookup for reset and profile-creation activation
- Only BCrypt hashes are persisted.

Both MySQL services use Spring Data JPA. Flyway owns schema creation through each service's `V1` migration, while Hibernate uses `ddl-auto=validate`; Hibernate does not create or update production tables.

### Bookmark — MongoDB

- Database: `proteinpro_bookmarks`
- Collection: `bookmarks`
- Document: `id`, `userId`, `proteinId`, embedded `proteinData`, `comment`, `createdAt`, `updatedAt`
- Indexes: `userId`; unique compound `(userId, proteinId)`
- Queries: authenticated owner's list; `(id, userId)` ownership lookup; duplicate protein check

The embedded protein snapshot belongs to the bookmark aggregate and allows a saved item to retain the details the user selected.

## Kafka event traceability

All implemented payloads are strongly typed JSON. Event IDs are UUID strings, identifiers are `String`, and timestamps are ISO-8601 values. Passwords, hashes, and JWTs are excluded.

| Event name | Topic | Producer | Consumer / group | Key | Payload | Trigger | Failure handling |
|---|---|---|---|---|---|---|---|
| `USER_CREATED` | `user-created` | User Profile | Authentication / `authentication-service-user-events` | `userId` | `eventId,eventType,userId,email,occurredAt` | Successful profile persistence | Consumer retries 3 times with exponential backoff; no DLT invented |
| `USER_PROFILE_UPDATED` | `user-updated` | User Profile | No current consumer shown | `userId` | same profile-event shape | Successful profile update | Wait up to 5 seconds for real broker acknowledgement |
| `USER_LOGGED_IN` | `authentication-events` | Authentication | No current consumer shown | `userId` | `eventId,eventType,credentialId,userId,email,occurredAt` | Successful login | Wait up to 5 seconds for broker acknowledgement |
| `PASSWORD_RESET_REQUESTED` | `password-reset-events` | Authentication | No current consumer shown | `userId` | authentication-event shape | Successful authenticated reset | Wait up to 5 seconds for broker acknowledgement |
| `BOOKMARK_CREATED` | `bookmark-created` | Bookmark | No current consumer shown | `userId` | `eventId,eventType,bookmarkId,userId,proteinId,occurredAt` | Successful create | Wait up to 5 seconds for broker acknowledgement |
| `BOOKMARK_UPDATED` | `bookmark-updated` | Bookmark | No current consumer shown | `userId` | bookmark-event shape | Successful comment update | Wait up to 5 seconds for broker acknowledgement |
| `BOOKMARK_DELETED` | `bookmark-deleted` | Bookmark | No current consumer shown | `userId` | bookmark-event shape | Successful delete | Wait up to 5 seconds for broker acknowledgement |

The HLD also displays `protein-events`, but its event name, trigger, schema, key, producer action, consumer, group, and failure behavior are not clearly specified. It is deliberately not provisioned or used.

```text
User Profile --USER_CREATED--> Kafka/user-created --consume--> Authentication
User Profile --USER_PROFILE_UPDATED--> Kafka/user-updated --> future consumer
Authentication --login/reset events--> Kafka --> future consumer
Bookmark --create/update/delete events--> Kafka --> future consumer
```

## OpenFeign communication

1. `AuthenticationClient` resolves `authentication-service` through Eureka and sends `POST /internal/credentials` during registration. The call carries the user ID, normalized email, and password over the synchronous request; Authentication immediately BCrypt-hashes it. The endpoint requires `INTERNAL_API_KEY`. No password enters Kafka or User Profile persistence.
2. `ExternalProteinApiClient` calls `${PROTEIN_API_BASE_URL}/proteindata`, forwarding the exact case-sensitive query parameters from the client. Connection and read timeouts are centralized.

## API Gateway routes

| Public path | Eureka destination | Access |
|---|---|---|
| `/api/auth/**` | `AUTHENTICATION-SERVICE` | Login public; reset/logout protected |
| `/api/profiles/**` | `USER-PROFILE-SERVICE` | Registration public; `/me` protected |
| `/api/proteins/**` | `PROTEIN-SERVICE` | Guest-readable |
| `/api/bookmarks/**` | `BOOKMARK-SERVICE` | Protected |

The Gateway removes client-supplied identity headers, validates the JWT, and writes validated identity headers before routing. Protected downstream services independently validate the original Bearer token and derive ownership from its `userId` claim.

## Authentication and JWT flow

1. Registration calls User Profile through the Gateway.
2. User Profile generates the consistent user ID and calls Authentication through Feign.
3. Authentication stores a BCrypt hash in an inactive MySQL credential row.
4. User Profile saves the password-free MySQL profile row and publishes `USER_CREATED`.
5. Authentication consumes the real Kafka event and activates the matching credential.
6. Login checks the active credential and BCrypt password, returns a signed JWT containing subject=email and `userId`, and publishes `USER_LOGGED_IN` without the token.
7. The Gateway and downstream protected services reject missing, invalid, or expired JWTs with 401.
8. Password reset requires a current JWT and replaces only the BCrypt hash. Logout is stateless: the client discards its token; inactivity timeout is enforced by JWT expiry (30 minutes by default).

## Environment variables

Copy the names from `.env.example`; do not commit a populated `.env`. Spring reads the process environment and does not auto-load `.env` files.

| Variable | Required | Purpose |
|---|---|---|
| `JWT_SECRET` | Yes | Same random secret in Gateway, Authentication, Profile, Bookmark; at least 32 characters |
| `INTERNAL_API_KEY` | Yes | Protects the internal Feign credential endpoint |
| `AUTH_MYSQL_URL` | Yes | JDBC URL for Authentication's `authentication_db` |
| `AUTH_MYSQL_USERNAME` | Yes | Authentication-only MySQL user |
| `AUTH_MYSQL_PASSWORD` | Yes | Authentication MySQL password |
| `USER_PROFILE_MYSQL_URL` | Yes | JDBC URL for Profile's `user_profile_db` |
| `USER_PROFILE_MYSQL_USERNAME` | Yes | Profile-only MySQL user |
| `USER_PROFILE_MYSQL_PASSWORD` | Yes | Profile MySQL password |
| `BOOKMARK_MONGODB_URI` | Yes | Bookmark-owned MongoDB URI |
| `KAFKA_BOOTSTRAP_SERVERS` | No | Defaults to `localhost:9092` |
| `EUREKA_URL` | No | Defaults to `http://localhost:8761/eureka/` |
| `EUREKA_INSTANCE_HOSTNAME` | No | Defaults to `localhost` for the required local-machine runtime |
| `CONFIG_SERVER_URL` | No | Defaults to `http://localhost:8888` |
| `PROTEIN_API_BASE_URL` | No | Defaults to `http://localhost:3232` |
| `FRONTEND_ORIGIN` | No | Defaults to `http://localhost:3000` |
| `JWT_EXPIRATION_SECONDS` | No | Defaults to `1800` |

Example PowerShell session:

```powershell
$env:JWT_SECRET = 'replace-with-at-least-32-random-characters'
$env:INTERNAL_API_KEY = 'replace-with-a-random-internal-service-key'
$env:AUTH_MYSQL_URL = 'jdbc:mysql://localhost:3306/authentication_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC'
$env:AUTH_MYSQL_USERNAME = 'proteinpro_auth'
$env:AUTH_MYSQL_PASSWORD = 'replace-with-local-auth-db-password'
$env:USER_PROFILE_MYSQL_URL = 'jdbc:mysql://localhost:3306/user_profile_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC'
$env:USER_PROFILE_MYSQL_USERNAME = 'proteinpro_profile'
$env:USER_PROFILE_MYSQL_PASSWORD = 'replace-with-local-profile-db-password'
$env:BOOKMARK_MONGODB_URI = 'mongodb://localhost:27017/proteinpro_bookmarks'
$env:KAFKA_BOOTSTRAP_SERVERS = 'localhost:9092'
$env:PROTEIN_API_BASE_URL = 'http://localhost:3232'
```

## Local infrastructure without Docker

### MySQL

Install MySQL Community Server locally. Connect as a MySQL administrator and create two databases and two scoped users; substitute strong local passwords:

```sql
CREATE DATABASE authentication_db
  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE user_profile_db
  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE USER 'proteinpro_auth'@'localhost'
  IDENTIFIED BY 'replace-with-local-auth-db-password';
CREATE USER 'proteinpro_profile'@'localhost'
  IDENTIFIED BY 'replace-with-local-profile-db-password';

GRANT ALL PRIVILEGES ON authentication_db.*
  TO 'proteinpro_auth'@'localhost';
GRANT ALL PRIVILEGES ON user_profile_db.*
  TO 'proteinpro_profile'@'localhost';
FLUSH PRIVILEGES;
```

The grants intentionally prevent either service user from reading the other service's database. On first startup, Flyway creates the owned table and its `flyway_schema_history`; subsequent startups validate the migration before Hibernate validates the entity mapping.

### MongoDB for Bookmark

Install MongoDB Community 8.0 as a Windows service or use the official ZIP installation. Create a local data directory, then start the server:

```powershell
New-Item -ItemType Directory -Force C:\proteinpro-data\mongodb
mongod --dbpath C:\proteinpro-data\mongodb --bind_ip localhost --port 27017
```

With the default URI, MongoDB creates `proteinpro_bookmarks` when Bookmark first writes a document. Authentication and Profile must not receive MongoDB configuration.

### Kafka 3.9.2 KRaft

Download and extract the official Scala 2.13 binary. From its directory:

```powershell
$kafkaRoot = (Get-Location).Path
$clusterId = & "$kafkaRoot\bin\windows\kafka-storage.bat" random-uuid
& "$kafkaRoot\bin\windows\kafka-storage.bat" format -t $clusterId -c "$kafkaRoot\config\kraft\server.properties"
& "$kafkaRoot\bin\windows\kafka-server-start.bat" "$kafkaRoot\config\kraft\server.properties"
```

The services create the seven implemented topics with one partition and replication factor one, appropriate for the required local single broker. Verify them with:

```powershell
& "$kafkaRoot\bin\windows\kafka-topics.bat" --bootstrap-server localhost:9092 --list
& "$kafkaRoot\bin\windows\kafka-consumer-groups.bat" --bootstrap-server localhost:9092 --list
```

### External Protein API

The supplied PDF defines only `GET /proteindata`, case-sensitive query filtering, and `POST /proteindata`, and gives only a Docker command for launching its specific data source. Docker is forbidden by the controlling prompt, and no standalone API package/data file was supplied. Point `PROTEIN_API_BASE_URL` at a locally or remotely running compatible API. The backend exposes only the HLD-required read/search behavior; it does not expose the external create endpoint.

## Build and startup

Build all modules:

```powershell
mvn clean test
mvn clean package
```

Start in this order, each in a separate terminal after setting the environment variables:

1. MySQL
2. MongoDB
3. Kafka
4. Config Server
5. Discovery Server
6. Authentication Service
7. User Profile Service
8. Protein Service
9. Bookmark Service
10. API Gateway

```powershell
java -jar config-server\target\config-server-1.0.0.jar
java -jar discovery-server\target\discovery-server-1.0.0.jar
java -jar authentication-service\target\authentication-service-1.0.0.jar
java -jar user-profile-service\target\user-profile-service-1.0.0.jar
java -jar protein-service\target\protein-service-1.0.0.jar
java -jar bookmark-service\target\bookmark-service-1.0.0.jar
java -jar api-gateway\target\api-gateway-1.0.0.jar
```

Health checks are under each service's `/actuator/health`. Eureka's dashboard is `http://localhost:8761`.

## API examples

### Register and login

```powershell
$profile = Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/profiles/register `
  -ContentType application/json `
  -Body '{"firstName":"Ada","lastName":"Lovelace","email":"ada@example.com","password":"password123"}'

# USER_CREATED activation is asynchronous; wait until Authentication consumes it.
$login = Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/auth/login `
  -ContentType application/json `
  -Body '{"email":"ada@example.com","password":"password123"}'
$headers = @{ Authorization = "Bearer $($login.accessToken)" }
```

### Profile and password

```powershell
Invoke-RestMethod -Uri http://localhost:8080/api/profiles/me -Headers $headers
Invoke-RestMethod -Method Put -Uri http://localhost:8080/api/profiles/me -Headers $headers `
  -ContentType application/json -Body '{"firstName":"Ada","lastName":"Byron"}'
Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/auth/password-reset -Headers $headers `
  -ContentType application/json -Body '{"newPassword":"newPassword123"}'
```

### Protein query

```powershell
Invoke-RestMethod 'http://localhost:8080/api/proteins?source=whey&vegetarian=true'
```

The external resource's exact JSON property names and values were not supplied; use the case-sensitive fields returned by your configured API.

### Bookmarks

```powershell
$bookmark = Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/bookmarks -Headers $headers `
  -ContentType application/json `
  -Body '{"proteinId":"1","proteinData":{"id":1,"source":"whey"},"comment":"Compare later"}'
Invoke-RestMethod -Uri http://localhost:8080/api/bookmarks -Headers $headers
Invoke-RestMethod -Method Put -Uri "http://localhost:8080/api/bookmarks/$($bookmark.id)/comment" -Headers $headers `
  -ContentType application/json -Body '{"comment":"Preferred option"}'
Invoke-RestMethod -Method Delete -Uri "http://localhost:8080/api/bookmarks/$($bookmark.id)" -Headers $headers
```

## Testing and verification

- Unit tests cover JWT validation/expiry, BCrypt login success/failure, registration boundaries, external query forwarding, and bookmark ownership.
- Run `mvn clean test` for the entire reactor.
- Runtime evidence and exact unverified items are recorded in `RUNTIME_VERIFICATION.md`.
- The complete file-by-file change record is in `MYSQL_MIGRATION_REPORT.md`.
- Confirm MongoDB was removed only from Authentication and Profile with:

```powershell
rg -n -i 'MongoRepository|MongoTemplate|MongoClient|@Document|spring\.data\.mongodb|MONGODB_URI' authentication-service user-profile-service
```

## Troubleshooting

- Config import failure: start Config Server first and confirm `http://localhost:8888/authentication-service/default`.
- Service not routed: check Eureka and confirm the uppercase service name is registered.
- `401 Unauthorized`: send `Authorization: Bearer <token>` and ensure every service uses the same `JWT_SECRET`.
- Registration returns 502: Authentication is unavailable through Eureka or the internal API keys differ.
- Login fails immediately after registration: wait for the `user-created` Kafka consumer to activate the credential; check broker and consumer logs.
- MySQL connection refused: start MySQL, create both databases/users, and verify the six `*_MYSQL_*` variables.
- Flyway validation failure: do not edit an applied migration; add a new versioned migration for the next schema change.
- Mongo connection refused: start `mongod` and verify `BOOKMARK_MONGODB_URI`.
- Kafka connection refused: start the KRaft broker on `localhost:9092` and list topics.
- Protein returns 502: the configured external API is not reachable at `/proteindata`.

## Known limitations required by source ambiguity

- No Protein Kafka workflow: its event contract is not clearly specified in the HLD.
- No numeric rate limiter: the HLD names rate limiting but provides no limits, algorithm, or backing store.
- Password reset is an authenticated password change. A forgotten-password identity proof/token flow is not specified.
- Logout is stateless client token disposal; a server-side JWT revocation store is not specified.
- The supplied external Protein API has no permitted non-Docker local launcher or dataset, so this repository cannot bundle it.
- CI/CD and containerization were optional in the PDF and are outside this phase.
- Registration crosses two MySQL databases, Feign, and Kafka without a distributed transaction. Credentials stay inactive until the profile event arrives, preventing an orphan credential from logging in; an outbox/saga contract was not specified.
- Existing Authentication/Profile MongoDB data is not copied automatically. No production dump or approved source-to-target data mapping was supplied; preserve any real source databases until a separately reviewed data-migration run succeeds.
