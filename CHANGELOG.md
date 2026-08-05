# Changelog

## 1.1.1 (versionCode 3)

- Fixed transaction rows remaining on “Syncing...” until the main screen was recreated.
- The UI now refreshes immediately whenever a local transaction sync status changes to pending, syncing, synced, or failed.
- Kept Firestore reconciliation unchanged; this release only closes the live-status notification gap.

## 1.1.0 (versionCode 2)

- Fixed duplicate local transactions caused by concurrent Firebase snapshot ingestion.
- Added a unique Room index for Firestore transaction IDs and migration-time deduplication.
- Added authoritative server reconciliation, including removal of stale synced local rows.
- Protected pending local edits and deletes from stale/cache snapshots.
- Made queued Firebase writes idempotent and blocking until actual completion.
- Added recovery for interrupted sync operations and orphan unsynced transactions.
- Added deterministic balance-before and balance-after snapshots for new transactions.
- Added balance snapshot display to transaction rows.
- Added balance-before and balance-after columns to Excel, PDF, and CSV exports.
- Added two-device validation instructions and a Linux signed-release build script.
