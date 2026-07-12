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

In NYC Civil Service terminology, a candidate being **"reached"** (technically called being "certified" to an agency) means their name was referred/sent to a hiring agency for consideration from the eligible list. A candidate can be reached multiple times by different agencies without being appointed.

To confirm whether a candidate who was **reached** was actually hired and is currently working in a civil service title, the application implements the following automated verification logic:

1. **Reachable Status Match & Extraction:**
   * The application fetches live data from the **Active Civil Service Certifications** dataset (`a9md-ynri`) matching the candidate's exam number.
   * It looks for an exact match of the candidate's list number (`listNo`) to determine if they were reached, and extracts the **reached date** and the **certified agency code**.

2. **Civil List (Payroll) Cross-Referencing:**
   * If a candidate is determined to be **reached**, it dynamically queries the **NYC Civil List (Payroll)** dataset (`ye3c-m4ga`) using the certified agency code and the candidate's name.
   * It checks for payroll entries where the calendar year is **after** the reached date's year.
   * If a match is found, the candidate's status is marked as **Appointed** (hired), and their official title, agency, and salary details are pulled from the payroll record and displayed to confirm active employment.

3. **Reachable Timeline Estimation (Linear Regression):**
   * If a candidate has not yet been reached, the application uses **ordinary least squares linear regression** to project when they will be reached based on historical request rates for their exam.
   * Data points are generated using `(x = certification sequence rank, y = days elapsed since the first request date)`.
   * If the regression predicts a past date (or returns `<= 0` days) but the candidate hasn't been reached, it falls back to a linear rate projection (ranks covered per day) based on actual history.
   * Skip detection is built-in: it ignores list number "skips" (where the gap between subsequent list numbers is greater than 1.5) to keep projections stable and prevent outlier contamination.
