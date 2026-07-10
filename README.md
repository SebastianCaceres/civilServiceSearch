# Civil Service Search Application

A Spring Boot web application for searching NYC Civil Service Candidate lists and certifications, built using Spring Data JPA, Thymeleaf, Bootstrap, and Hibernate Search (Lucene).

## Configuration (.env)
A `.env` file is located in the root directory to store critical/secret properties:
* `SPRING_DATASOURCE_URL`: PostgreSQL JDBC connection URL.
* `SPRING_DATASOURCE_USERNAME`: Database username.
* `SPRING_DATASOURCE_PASSWORD`: Database password.
* `CIVILSERVICE_SYNC_APP_TOKEN`: NYC Open Data SODA3 API App Token.
* `CIVILSERVICE_SYNC_SECRET_TOKEN`: NYC Open Data SODA3 API Secret Token.

These are automatically injected into `application.properties` with fallback defaults for local execution outside Docker.

---

## Running with Docker Compose

### 1. Build and Run Entire Application
To build the Spring Boot application container and run it alongside PostgreSQL in the same Docker network:
```bash
docker compose up -d --build
```
This will:
1. Spin up the PostgreSQL container and wait for its healthcheck to pass.
2. Build the Spring Boot multi-stage Docker image and start the container on port `8080`.
3. Auto-load configuration properties from the local `.env` file.

### 2. Stop Containers
To shut down the running containers:
```bash
docker compose down
```

---

## Running Locally (Outside Docker)

### 1. Start the Database only
If you prefer running the database in Docker but debugging/running the Java code locally:
```bash
docker compose up -d postgres
```

### 2. Start Spring Boot
Run the Spring Boot application locally:
```bash
./mvnw spring-boot:run
```

### 3. Run Tests
Run the JUnit test suite (which runs completely isolated in-memory against a mock H2 instance):
```bash
./mvnw test
```

---

## NYC Civil Service Terminology & Ingestion Logic

In NYC Civil Service terminology, a **"certification"** (often referred to as a certification list or cert) simply means the candidate's name was referred/sent to a hiring agency for consideration. A candidate can indeed be certified multiple times to different agencies (or the same agency) without being hired.

To confirm that a candidate was actually hired and is currently in a civil service title, you should look for the following fields and values:

1. **Disposition / Action Description (`disposition_desc` or `action_desc`)**:
   * This is the definitive field that indicates the outcome of a certification.
   * You are looking for a value of **"APPOINTED"** (specifically "Appointed - Permanent" or "Appointed - Probationary").
   * Other values like "Declined", "Failed to Appear", "Failed Medical", "No Response", or "Not Reached" indicate the candidate was reached/certified but not placed in the title.
2. **Adjusted Final Average / Score (`adj_fa`) vs. List Status**:
   * If a candidate's status on the eligible list changes to **"Terminated"** (with a filled `termination_date`), it often indicates they were appointed from the list, though it could also mean the list expired or the candidate was removed for other reasons (such as declining multiple times).
3. **Agency/Title Match**:
   * Comparing the `list_agency_code` / `list_agency_desc` (which represents the agency they were certified to) against the final disposition status of "Appointed" confirms which city agency they are officially serving in under that civil service title.
