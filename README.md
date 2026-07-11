# Civil Service Search Application

A Spring Boot web application for searching NYC Civil Service Candidate lists and certifications, built using Thymeleaf, Vanilla CSS, and Hibernate Search Standalone (Lucene).

The application is fully self-contained, using a local directory index for searching without requiring any external relational database (like PostgreSQL or H2).

---

## Configuration (.env)
A `.env` file is located in the root directory to store critical NYC Open Data API configuration:
* `CIVILSERVICE_SYNC_APP_TOKEN`: NYC Open Data SODA3 API App Token.
* `CIVILSERVICE_SYNC_SECRET_TOKEN`: NYC Open Data SODA3 API Secret Token.

These environment variables are automatically loaded by Spring Boot on startup.

---

## Running Locally

### 1. Start the Application
Run the Spring Boot application locally:
```bash
# Windows
.\mvnw.cmd spring-boot:run

# Unix/macOS
./mvnw spring-boot:run
```
The application starts a web server on port `8083`. Open [http://localhost:8083](http://localhost:8083) in your browser.

### 2. Run Tests
Run the JUnit integration and unit tests:
```bash
# Windows
.\mvnw.cmd test

# Unix/macOS
./mvnw test
```

---

## Architecture & Synchronization

* **Lucene Indexing:** The application uses Standalone Hibernate Search with a local file-based Lucene directory to index and query NYC civil service candidate lists.
* **Automatic Startup Sync:** On startup, the application checks if the local index has fewer than 5,000 records. If it does, a full synchronization is triggered, downloading the entire dataset (active and terminated lists) from NYC Open Data, bypassing caching.
* **HTMX Out-of-Band (OOB) Swap:** Search queries are processed dynamically using HTMX. Instead of reloading or replacing the entire page, search results are swapped out-of-band into the results container (`#search-results`), preserving focus and cursor position in the search query input box for a smooth, single-page application experience.

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
