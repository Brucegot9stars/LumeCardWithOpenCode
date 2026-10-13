# P2 follow-up and external verification

This phase changes ID generation, plan mutations, review progress calculation and CI coverage. No compile or test command was run in the development environment, as requested. The source and staged-diff checks were completed here; the external environment must report actual build and test results.

## External checks

1. Run `./gradlew :shared:desktopTest :composeApp:desktopTest :composeApp:compileKotlinDesktop :composeApp:assembleDebug --no-daemon --console=plain` on a Linux machine with an Android SDK. The PR workflow runs these tasks.
2. Run `gradlew.bat :shared:desktopTest :composeApp:desktopTest` on Windows, then use the release workflow to verify packaging when ready.
3. Inspect resolved `material3`, `material3-desktop` and `lifecycle-runtime` versions in `:composeApp` and `:shared`. The application pins are intentionally retained for rich-editor text rendering; they are now limited to `:composeApp`. Do not remove them solely to make the version numbers match the Compose plugin.
4. Check a PR targeting `main` or `develop`: PR Validation should run once for the Android build and both desktop test suites. Android CI now runs on pushes to `main` and `develop` only.

## Behavior checks

- Create many cards/reviews and verify newly generated IDs keep their full suffix; restoring old backups must keep old IDs unchanged.
- Score a card, add another card to its plan, and verify automatically completed progress reopens. Complete a plan manually, add a card, and verify it stays manually complete until reset. Repeat after a full data backup and restore.
- Delete a card, a deck or a knowledge base from each available UI path; plan totals and status should update, and injected database failures should leave content and plans unchanged.
- Change the default plan and simulate a database failure while setting the new one; the old default must remain. Also test deleting the current default.
- Verify study mode selection with no new cards, no due cards and a zero daily limit. Confirm the dashboard's due-plan count matches the cards available for those plans.
