# Metro Interaction And Code Quality Improvement Plan

Date: 2026-05-16

This plan turns the interaction/code-quality audit into small, reviewable
implementation slices. It focuses on permission consistency, safer destructive
actions, GUI execution safety, and keeping command/GUI behavior aligned.

## Goals

- Make command and GUI permission behavior consistent.
- Avoid silent failures in player-facing flows.
- Require fresh authorization at action execution time, not only at menu entry.
- Keep destructive operations deliberate and recoverable.
- Reduce duplicated command/GUI business paths without broad refactors.
- Preserve current runtime behavior unless a phase explicitly changes it.

## Non-goals

- No broad UI redesign.
- No data schema changes.
- No Folia support claim changes.
- No release/version bump.
- No unrelated cleanup of large classes.

## Risk Model

- P1 fixes are R2/R3 because they affect permissions and teleport behavior.
- P2 fixes are R2 unless they touch persistence, economy, or ride lifecycle.
- Destructive action policy changes are R2 for UX and R3 when they affect data
  deletion or route clearing.

## Phase 1: Permission Consistency

### 1.1 Protect `/m stop tp`

Problem:

- `/m stop tp <stopId>` currently teleports without checking `metro.tp`.
- GUI teleport paths check `metro.tp`.
- `docs/regression-baseline.md` expects command teleport to fail without
  `metro.tp`.

Implementation:

- Add `@Permission("metro.tp")` to `StopCommand.tp`, or add an explicit
  `player.hasPermission("metro.tp")` check with localized feedback.
- Prefer explicit feedback if Cloud and Bukkit fallback command behavior should
  remain identical.
- Add tests for allowed and denied command teleport behavior.

Acceptance:

- Player without `metro.tp` cannot use `/m stop tp`.
- Player with `metro.tp` can teleport when stop point exists.
- Denied command path sends a localized no-permission message.
- GUI and command semantics match.

Suggested verification:

- `mvn "-Dtest=StopCommandServiceTest,CommandGuardTest" test` if command tests
  are added there.
- `mvn test`.

### 1.2 Update Permission Documentation

Problem:

- README currently describes `metro.tp` as GUI-oriented, while regression docs
  treat it as command and GUI permission.

Implementation:

- Update README and README_en permission table wording.
- Keep `plugin.yml` permission description aligned if needed.

Acceptance:

- Docs say `metro.tp` controls stop teleport through both command and GUI.

## Phase 2: Boarding Choice Feedback

### 2.1 Show `metro.use` Block Reason In Boarding Choice GUI

Problem:

- Multi-line boarding GUI can show a line as boardable for a player without
  `metro.use`.
- Clicking then closes the GUI and silently returns from `boardSelectedLine`.

Implementation:

- In `LineBoardingChoiceView.getBoardingBlockReason`, check `metro.use` before
  ticket checks.
- Add or reuse a localized message for missing ride permission.
- In `PlayerInteractListener.boardSelectedLine`, send the same localized message
  instead of silently returning.

Acceptance:

- GUI item uses barrier material or blocked lore for players without `metro.use`.
- Clicking without `metro.use` does not silently close without explanation.
- Existing ticket/Vault failure reasons remain unchanged.

Suggested verification:

- `mvn "-Dtest=LineBoardingChoiceControllerTest,PlayerInteractListenerTest,PlayerInteractListenerExtendedTest" test`
- `mvn test`.

## Phase 3: GUI Execution-Time Authorization

### 3.1 Recheck Ownership Before Settings Actions

Problem:

- GUI list entry checks whether the player can open settings.
- Settings controllers execute actions without rechecking ownership at click
  time.
- A stale inventory can remain open after ownership/admin changes.

Implementation:

- Add a small GUI guard helper, or reuse `OwnershipUtil`, in:
  - `LineSettingsController`
  - `StopSettingsController`
  - `ConfirmActionController`
- Recheck `canManageLine`, `canManageStop`, or the relevant ownership rule
  before every mutating action.
- If authorization fails, send localized permission feedback and close or return
  to the safest parent view.

Acceptance:

- Stale GUI cannot rename, delete, clear route, set point, clone, or toggle
  protection after permission is revoked.
- Non-mutating navigation remains usable.

Suggested verification:

- Add focused tests for denied execution in settings and confirm controllers.
- `mvn "-Dtest=ConfirmActionControllerTest,LineListControllerTest,StopListControllerTest" test`
- `mvn test`.

### 3.2 Route Confirm Controller Through Services

Problem:

- `ConfirmActionController` calls managers directly for delete and route clear.
- Command paths use service classes.

Implementation:

- Use `LineCommandService` and `StopCommandService` in confirm actions where
  available.
- Keep behavior unchanged while centralizing write status mapping.
- Avoid broad manager refactors in this phase.

Acceptance:

- GUI and command use the same write status semantics for matching operations.
- Existing success/failure messages remain stable.

## Phase 4: Destructive Command Safety

### 4.1 Define Command Confirmation Policy

Problem:

- GUI destructive actions are confirmed.
- Command destructive actions execute immediately:
  - `/m line delete`
  - `/m line clearroute`
  - `/m stop delete`
  - `/m portal delete`

Design decision:

- Use explicit confirmation syntax for commands, not chat-state confirmation.
- Recommended pattern:
  - `/m line delete <lineId> confirm`
  - `/m stop delete <stopId> confirm`
  - `/m portal delete <portalId> confirm`
  - `/m line clearroute <lineId> confirm`

Implementation:

- Add optional `[confirm]` argument to destructive commands.
- Without `confirm`, show a localized warning and exact command to rerun.
- With `confirm`, execute current behavior.
- Keep GUI confirmation unchanged.

Acceptance:

- Accidental destructive command entry does not mutate data.
- Documentation and help text show the confirmation requirement.
- Existing GUI destructive flow is unchanged.

Suggested verification:

- Add command-layer or service-adjacent tests for unconfirmed vs confirmed
  behavior.
- Run `mvn test`.

## Phase 5: Config And API Boundary Cleanup

### 5.1 Route `MetroAPI.isEconomyEnabled()` Through `ConfigFacade`

Problem:

- Most read-only API settings use `config()`.
- `isEconomyEnabled()` directly reads `plugin.getConfig()`.

Implementation:

- Add or use a `ConfigFacade` economy accessor.
- Update `MetroAPI.isEconomyEnabled()`.
- Keep behavior identical.

Acceptance:

- API read-only settings consistently use `ConfigFacade`.
- Existing `MetroAPITest` still passes.

Suggested verification:

- `mvn "-Dtest=MetroAPITest,ConfigFacadeTest" test`

## Phase 6: Documentation And Regression Updates

Update when previous phases land:

- README and README_en permission table.
- Command help text and language keys for destructive confirmation.
- `docs/regression-baseline.md` with:
  - `/m stop tp` denied path.
  - Boarding GUI no-`metro.use` feedback.
  - Stale GUI permission recheck scenario.
  - Destructive command confirmation path.
- `docs/release-notes-template.md` if these changes are planned for release.

## Recommended Implementation Order

1. Phase 1.1 and 1.2: teleport permission consistency.
2. Phase 2.1: no silent boarding GUI failures.
3. Phase 3.1: execution-time GUI authorization.
4. Phase 3.2: route confirm actions through services.
5. Phase 4.1: destructive command confirmation policy.
6. Phase 5.1: config boundary cleanup.
7. Phase 6: documentation and release evidence.

## Definition Of Done

- `mvn test` passes after each phase.
- `mvn verify` passes before release or before merging multiple phases.
- New denial paths have tests.
- User-visible messages are localized.
- Command, GUI, README, `plugin.yml`, and regression docs agree.
- Any skipped manual runtime check is named in the PR or final handoff.
