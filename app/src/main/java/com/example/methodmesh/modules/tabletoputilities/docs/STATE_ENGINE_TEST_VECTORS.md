# Tabletop Utilities state-engine regression vectors

These are Development test vectors for conversion into repository unit tests.

## Counter bounds

1. HP 52, minimum 0, maximum 52; adjust -15 => 37.
2. HP 4; adjust -10 => 0.
3. HP 50; adjust +10 => 52.
4. EXP 100, minimum 0; adjust -500 => 0.
5. Generic unbounded counter 0; adjust -10 => -10.

## Character bootstrap

Workspace features: HP, TEMP_HP, EXP, DEATH_SAVES.

Add character with HP maximum 52.

Expected linked state:

- HP 52/52;
- Temp HP 0;
- EXP 0;
- death saves success=0, failure=0.

## Death saves

- set success 4 => stored as 3;
- set success -1 => stored as 0;
- set failure 4 => stored as 3;
- set failure -1 => stored as 0.

## Initiative + round effects

Initial:

- Elowen initiative 18;
- Goblin initiative 10;
- Bless active, remainingRounds=3, autoTick=true;
- initiative round=1.

Start initiative => Elowen current.

Next turn => Goblin current, round=1, Bless=3.

Next turn => Elowen current, round=2, Bless=2.

Two more wrapped rounds => Bless reaches 0 and becomes inactive.

## Session

- start with no name => caller should normally supply generated `Session N` name;
- cannot start a second active session;
- note requires non-blank note;
- finish requires an active session.

## Scores

Two score counters:

- Alice 320;
- Bob 295.

Record scores => two immutable `ScoreRecord`s.

Change Alice to 280, Bob to 340; record again.

Expected personal bests:

- Alice 320;
- Bob 340.

## Undo

Counter 37 -> 33 creates inverse `SetCounter(37)`.

Undo should:

- create a new event with `source=undo`;
- point `reverses_event_id` to the 37->33 event;
- restore counter to 37;
- leave original event in `audit.jsonl`;
- not choose the same original event again on the next undo.
