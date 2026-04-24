# Metro Runtime Baseline Checklist

This checklist is used to validate behavior after refactors and hotfixes.

## Scope

- Boarding flow (`right-click rail` -> minecart spawn -> auto board)
- Waiting/departure flow (`waiting title/actionbar/sound` -> movement starts)
- Arrival flow (`arrive title/sound` -> stop transition)
- Terminal flow (`terminal title` -> forced dismount -> cleanup)
- Manual exit flow (`vehicle exit` -> scoreboard/title cleanup -> despawn rules)

## Preconditions

- At least one line with 3+ stops configured.
- Every stop has valid corners and a stop point.
- `metro.use` is granted for test player.
- `metro.tp` is granted for command/gui teleport checks.

## Manual Regression Steps

1. Right-click a rail in a non-terminal stop; verify only one minecart is pending/spawned.
2. Verify waiting countdown and waiting sound appear before departure.
3. Verify minecart departs automatically after `settings.cart_departure_delay`.
4. Verify station entry shows arrival info and station-arrival sound.
5. Verify terminal stop ejects passenger and removes minecart.
6. Verify exiting minecart mid-route clears title/actionbar/scoreboard.
7. Verify `/m stop tp <stop_id>` works with `metro.tp` and fails without it.
8. Verify GUI teleport behavior matches command permission semantics.
9. Run `/m reload`; verify new config defaults are present and plugin remains functional.

## Debug Log Categories

- `settings.debug.train_state_transitions`
- `settings.debug.interaction_flow`

Enable with:

```yml
settings:
  debug:
    enabled: true
```

