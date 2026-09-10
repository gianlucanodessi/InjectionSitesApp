`android.insofia-backup` is generated before tests by the unchanged Android
`BackupManager.kt` (see `Interop` and `scripts/prepare_interop.py`). It contains
three synthetic records. Its public test-only password is `Test-InSofina-à🔐`.
The Unicode password deliberately tests UTF-8 PBKDF2 interoperability.

CI exports the decoded state using Swift, then reads that file with the unchanged
Android codec, checking IDs, event time, creation time, settings and avatar.
No real records, credentials or account secrets are involved.
