# P1: Review, learning plans and screen lifecycle

## Behavior

- A rating writes its review log, scheduling fields, algorithm state and affected plan progress in one SQLite transaction. The study screen advances only after that transaction succeeds; repeated taps while it runs are ignored.
- A plan contains the union of explicitly selected cards, selected decks and selected knowledge bases. Missing or deleted cards and decks do not contribute. The study mode selector and study session use the same membership rule.
- Plan progress counts distinct reviewed card IDs since the plan was created or last reset. Reset stores a boundary in `AppSettings` and does not change card scheduling or another plan. Explicitly completed plans remain complete until reset.
- Full data backups carry each plan's reset boundary. Configuration exports omit these keys, and configuration restore preserves local boundaries. Older backups omit the optional field; their progress is recomputed from the plan creation time when the next review affects the plan. An old reset date cannot be reconstructed from a backup that never recorded it.
- Voyager now owns screen model instances. A study screen has a unique navigation key, so separate sessions cannot share review state. The study timer resumes from the time accumulated before a temporary screen disposal.

## Verification status

Source and diff checks only. Compilation and test execution were intentionally deferred at the user's request. Added tests cover plan membership, review transaction rollback, repeated reviews, reset metadata and backup/config handling; they still need to be run on a machine with the project dependencies and Android SDK available.
