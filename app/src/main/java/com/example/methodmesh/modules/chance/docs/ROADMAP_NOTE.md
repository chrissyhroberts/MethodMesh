# Chance roadmap note

The v1.05 migration deliberately preserves the current scope. The following ideas are **not implemented** and should not be read as current capability behavior.

## Nearby-device tabletop sessions

A future tabletop/session layer could use Bluetooth or another local peer transport to exchange private/public game events between nearby MethodMesh devices. Poker is a useful demonstrator because it combines private hands, public/community state and player actions.

If pursued, this should not be hidden inside the randomisation engine. Chance should continue to own random outcomes/dealing; a separate session/transport layer should own participants, state synchronisation and game-event exchange.

## Stateful decks and games

Persistent decks, discard piles, betting state, HP/mana/initiative, characters and campaign records are deliberately outside the current stateless Chance contract. They belong in a stateful tabletop/session module rather than being smuggled into this v1.05 migration.
