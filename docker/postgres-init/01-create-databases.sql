-- ledger-service and notification-service each own their own database (and their own Flyway
-- schema_history), even though they share one Postgres container in this compose file.
CREATE DATABASE ledgerguard_notifications;
