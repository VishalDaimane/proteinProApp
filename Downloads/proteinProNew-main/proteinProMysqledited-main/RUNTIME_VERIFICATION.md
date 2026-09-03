# Runtime verification

This record describes commands actually executed against the current MySQL-migrated source. Unavailable external systems are listed separately and are not credited as tested.

## Build and unit tests

- Java: Temurin 17.0.9.
- Maven: Apache Maven 3.9.9 from the temporary workspace because Maven is not installed globally.
- Full reactor `mvn test`: passed after the persistence conversion.
- Full reactor `mvn package`: passed and created all seven executable service JARs.
- Final post-documentation clean test/package result and exact test count are recorded at the end of this file.

## Real MySQL verification

- Oracle MySQL Community Server 8.4.11 Windows ZIP was downloaded from the official MySQL CDN solely into the temporary `work` directory.
- Archive size: `281191914` bytes; locally calculated SHA-256: `A492371D687D2BAB088B0062581144A0044B8964BAEFDF4FAA579292B423D25C`.
- A real server started on `127.0.0.1:3306` with independently provisioned `authentication_db` and `user_profile_db` databases and scoped service users.
- Authentication's Hikari pool connected, Flyway applied `V1__create_credentials.sql`, and Hibernate 6.5.3 validated one JPA repository/entity.
- Profile's Hikari pool connected, Flyway applied `V1__create_user_profiles.sql`, and Hibernate validated one JPA repository/entity.
- Both `flyway_schema_history` tables reported version 1 with `success=1`.
- Information Schema confirmed primary keys and the named unique email/user-ID constraints.
- The Authentication service user was denied a query against `user_profile_db`; the Profile service user was denied a query against `authentication_db`.

MySQL 8.4 compatibility disclosure: the migrations and JPA validation succeeded, but the Spring Boot-managed Flyway version warned that MySQL 8.4 is newer than its tested range (through 8.1). The runbook recommends MySQL 8.0 for this fixed Spring Boot 3.3.5 dependency set rather than hiding or suppressing that warning.

## Registration, Authentication, Profile, and security

The following flow ran through API Gateway and Eureka:

```text
REGISTER_ID_MATCH=True
LOGIN_TOKEN_TYPE=Bearer
JWT_EXPIRES=1800
PROFILE_GET_EMAIL_MATCH=True
PROFILE_UPDATE_LAST_NAME=Byron
PASSWORD_RESET_STATUS=204
NEW_PASSWORD_LOGIN=True
DUPLICATE_REGISTRATION_STATUS=409
```

- Profile called Authentication through the existing Feign client and internal API key.
- The same UUID user ID was present in `user_profiles.id` and `credentials.user_id`.
- SQL verification found one Authentication row and one Profile row for the test identity.
- The credential hash length was 60 and its prefix was a BCrypt prefix; no raw password was stored.
- Authentication initially persisted the credential inactive, then the real Kafka consumer activated it from `USER_CREATED` before login.
- Existing JWT generation, claims, expiration, Gateway validation, and downstream validation remained functional.
- No SQL error, stack trace, credential, or connection string was returned in REST errors.

## Real Kafka verification

- Apache Kafka 3.9.2 ran as a real single-node KRaft broker with cluster ID `YisRIelVSIKZZ_w6sNwiUw`.
- Actual topics listed after migration: `user-created`, `user-updated`, `authentication-events`, `password-reset-events`, `bookmark-created`, `bookmark-updated`, `bookmark-deleted`, plus `__consumer_offsets`.
- Actual consumer group listed: `authentication-service-user-events`.
- Profile published the real JSON `USER_CREATED` event and waited for broker acknowledgement.
- Authentication deserialized and processed it, logging `Activated credentials for userId=...`.
- Login, password reset, and Bookmark create/update/delete event sends received broker acknowledgements.
- Kafka was neither removed nor mocked.

The broker was restarted from the prior test data during orchestration, so clients briefly rediscovered the group coordinator. The final consumer assignment, event processing, and all tested producer acknowledgements succeeded.

## MongoDB and unchanged services

- MongoDB Community 8.0.29 ran on `127.0.0.1:27017`.
- Bookmark logged a real MongoDB driver connection and found one `MongoRepository`; no JPA repository was introduced there.
- Through Gateway, Bookmark create, list, comment update, and delete succeeded and published their existing Kafka events.
- A direct Bookmark request without JWT returned 401.
- Protein started with no database auto-configuration, registered with Eureka, and attempted its unchanged external Feign request.
- Because the external API was not running at the configured URL, Protein returned the designed 502 rather than fake data.

Representative results:

```text
BOOKMARK_CREATED=True
BOOKMARK_LIST_CONTAINS=True
BOOKMARK_UPDATED=True
BOOKMARK_DELETE_STATUS=204
BOOKMARK_DIRECT_NO_JWT=401
PROTEIN_GATEWAY_STATUS=502
```

## Config Server, Eureka, Gateway, and health

- Config Server served the new MySQL/JPA/Flyway configuration to Authentication and Profile.
- Eureka reported these applications `UP`: `API-GATEWAY`, `AUTHENTICATION-SERVICE`, `USER-PROFILE-SERVICE`, `PROTEIN-SERVICE`, and `BOOKMARK-SERVICE`.
- Gateway routed the tested Profile, Authentication, Bookmark, and Protein requests.
- Health results during the full post-migration run:

```text
HEALTH_8888=UP
HEALTH_8761=UP
HEALTH_8080=UP
HEALTH_8081=UP
HEALTH_8082=UP
HEALTH_8083=UP
HEALTH_8084=UP
```

## Not verified or not performed

- Historical MongoDB-to-MySQL data copying: no production dump or approved migration input was supplied. Source data must be preserved until a separately reviewed migration succeeds.
- Successful external Protein API data retrieval: no non-Docker external API process was supplied or running.
- Profile deletion: the existing service has no deletion endpoint, so none was invented.
- React/UI behavior: no frontend repository was supplied.
- Container deployment: explicitly excluded; no Docker or Compose artifact was created.

## Final clean verification

- `mvn clean test`: exit 0.
- `mvn clean package`: exit 0.
- Tests: 10 run, 0 failures, 0 errors, 0 skipped.
- Seven executable service JARs were recreated.
- Effective dependency audit:
  - Authentication: JPA 3.3.5, MySQL Connector/J 8.3.0, Flyway Core/MySQL 10.10.0; no MongoDB dependency.
  - User Profile: JPA 3.3.5, MySQL Connector/J 8.3.0, Flyway Core/MySQL 10.10.0; no MongoDB dependency.
  - Bookmark: MongoDB starter 3.3.5; no JPA/MySQL/Flyway dependency.
  - Protein: no JPA, MySQL, MongoDB, or Flyway dependency.
- Source scans found no MongoDB code/configuration in Authentication or User Profile, no relational persistence in Bookmark/Protein/Gateway/Eureka/Config Server, no Docker/Compose artifact, and no temporary runtime secret in the deliverable.
