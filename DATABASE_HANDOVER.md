
# Fabric AI Advisor - Database Handover

## 1. Database

File: fabric_for_integration.db

Database engine: SQLite

Integrity check: ok

## 2. Database Tables

- fabric_database
- fabric_import_14_rows
- fabric_sources
- training_examples

## 3. Main Fabric Data

Main table: fabric_database

Number of fabric records: 14

This table contains fabric information used by
the rule-based recommendation system.

## 4. Data Verification

The current fabric records are marked as
unverified.

Fabric characteristics, prices, and source
information must be independently verified
before being treated as confirmed facts.

## 5. Integration Notes

The current application includes:

- Spring Boot REST API
- SQLite database
- Rule-based fabric recommendation
- Gemma 3 4B integration through Ollama

The AI question-answering endpoint is:

POST /api/ai/ask

The existing recommendation endpoint is:

GET /api/recommendations

These two functions currently operate independently.

The AI model does not yet directly query
SQLite for fabric recommendations.

## 6. Using the Database

Extract fabric_for_integration.db from the ZIP file.

Place it in the project root directory.

If the application expects the name fabric.db,
rename a copy to fabric.db.

Configure the SQLite JDBC connection to use
the correct database path on your computer.

Do not assume that the original absolute path
from the developer's machine will work.

## 7. Planned Development

- Natural language intent extraction
- AI-assisted fabric recommendation
- SQLite-grounded explanations
- Database-based response validation
- Optional llama.cpp integration

## 8. Important

This database is supplied for development,
integration, and testing.

The data has not been independently verified.
