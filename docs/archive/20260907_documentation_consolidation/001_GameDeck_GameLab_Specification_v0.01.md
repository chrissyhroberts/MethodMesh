# GameDeck & GameLab Specification

**Version:** 0.01\
**Status:** Concept / architecture specification\
**Date:** 2026-09-05

> **Every game is a rules system that can be parameterised, simulated
> and understood.**
>
> **Never a bad map. Never the same case twice.**

------------------------------------------------------------------------

## 1. Purpose

GameDeck is a lightweight, extensible games platform in which games are
expressed, wherever practical, as specifications over shared platform
capabilities rather than as unrelated standalone applications.

GameLab is the generative, simulation and optimisation layer beneath
GameDeck. It models the rules of games, explores their parameter spaces,
simulates play, validates generated scenarios, measures multidimensional
difficulty, evolves scenarios and adapts future challenges to an
evolving player profile.

Together they provide:

-   classic board, card, tile, word, puzzle, arcade and adventure games;
-   procedural generation of valid, mechanically interesting scenarios;
-   CPU opponents;
-   local and peer-to-peer multiplayer;
-   persistent player records, high scores, saves and replays;
-   a cross-game character sheet and skill progression model;
-   adaptive difficulty that responds to demonstrated abilities without
    changing rules secretly during play;
-   playground and sandbox simulations that need not have conventional
    win conditions;
-   optional retro/emulation engines;
-   optional lightweight Doom-compatible play;
-   support for generic physical controllers, including a small ESP32
    Bluetooth gamepad.

The system should remain lightweight. Shared engines and declarative
specifications are preferred to repeated bespoke implementations.

------------------------------------------------------------------------

## 2. Core design principles

### 2.1 Games are rules systems

Every supported game has, explicitly or implicitly:

-   state;
-   legal actions;
-   state transitions;
-   objectives;
-   constraints;
-   information available to each player;
-   randomness, where applicable;
-   scoring;
-   terminal conditions.

GameDeck should represent these concepts explicitly wherever practical.

A game implementation should therefore normally separate:

1.  **rules** --- what is permitted;
2.  **parameters** --- what can vary without changing the identity of
    the game;
3.  **scenario** --- a particular initial state or generated challenge;
4.  **presentation** --- how state and actions are rendered;
5.  **agents** --- human, CPU or simulated players;
6.  **player model** --- the persistent model of the person playing.

### 2.2 Never a bad map

Procedural generation must not mean arbitrary random generation.

Generated scenarios should be validated through rules, constraint
checking and, where useful, simulation before being offered for play.

Depending on the game, validation may include:

-   solvability;
-   absence of unintended deadlocks;
-   absence of unavoidable failure states at initialization;
-   acceptable first-player advantage;
-   target win probability;
-   target session length;
-   absence of mandatory blind guesses unless explicitly requested;
-   adequate strategic alternatives;
-   acceptable luck sensitivity;
-   adequate recovery opportunities;
-   meaningful separation between weak and strong agents.

### 2.3 Never the same case twice

Where game mechanics permit procedural generation, GameLab should
generate effectively unlimited valid scenarios rather than rely solely
on hand-authored content.

Generated scenarios must nevertheless be reproducible using:

-   generator version;
-   ruleset version;
-   parameter set;
-   random seed;
-   optional generation metadata.

A scenario need never recur accidentally, but an interesting scenario
can always be replayed exactly.

### 2.4 Difficulty is multidimensional

There is no universal scalar called `difficulty`.

Game difficulty may contain dimensions such as:

-   deduction;
-   planning;
-   spatial reasoning;
-   memory;
-   vocabulary;
-   probability;
-   tactics;
-   reaction/execution;
-   risk management;
-   resource management;
-   uncertainty;
-   information scarcity;
-   time pressure;
-   punishment;
-   recovery;
-   opponent aggressiveness;
-   branching complexity;
-   session length.

A game exposes the subset that is meaningful to it.

### 2.5 Games may adapt between runs, not secretly during them

GameLab may use the player model to construct an appropriately
challenging scenario before play begins.

Once a game begins, its rules and generated scenario remain fixed except
where change is an explicit part of those rules.

The platform should not covertly:

-   increase enemy health;
-   alter dice probabilities;
-   move hidden objects;
-   change puzzle solutions;
-   modify opponent capabilities;
-   manipulate outcomes to force a target win/loss rate.

Adaptive generation should remain fair and inspectable.

### 2.6 Progression means better-matched demands

Player progression must not simply mean "more enemies", "faster enemies"
or "more of everything".

As a player demonstrates improvement in a skill dimension, games that
exercise that dimension should become more demanding in ways appropriate
to their mechanics.

Examples:

-   higher deduction → longer or less redundant inference chains;
-   higher planning → deeper strategic dependencies;
-   higher spatial ability → more complex layouts;
-   higher vocabulary → rarer or more constrained word challenges;
-   higher probability skill → subtler risk/reward decisions;
-   higher reaction skill → increased execution demand.

### 2.7 Lightweight by default

The base package should remain small.

Prefer:

-   shared engines;
-   compact game specifications;
-   procedural content;
-   vector/CSS/canvas presentation;
-   reusable rules libraries;
-   optional heavyweight modules.

Retro emulators, strong AI engines, large content packs and
Doom-compatible content should be separately installable where
practical.

------------------------------------------------------------------------

## 3. System architecture

``` text
                         PLAYER PROFILE
                              │
                              │ skill estimates
                              ▼
                        ┌─────────────┐
                        │   GAMELAB   │
                        └──────┬──────┘
                               │
             ┌─────────────────┼─────────────────┐
             │                 │                 │
         GENERATOR         SIMULATOR         OPTIMISER
             │                 │                 │
             └─────────────────┼─────────────────┘
                               │
                         GAME SPECIFICATION
                               │
                    rules / state / actions
                               │
                               ▼
                         ┌──────────┐
                         │ GAMEDECK │
                         └────┬─────┘
                              │
        ┌─────────────┬───────┼────────┬──────────────┐
        │             │       │        │              │
      HUMAN          CPU    P2P     REPLAY        SIM AGENT
```

### 3.1 Major components

#### GameDeck Core

Responsible for:

-   game lifecycle;
-   game discovery;
-   input;
-   rendering contract;
-   animation;
-   sound/haptic hooks;
-   random number services;
-   seeds;
-   persistence;
-   saves;
-   replays;
-   player identity;
-   high scores;
-   achievements;
-   CPU integration;
-   multiplayer transport;
-   controller mapping.

#### Game Specification

Defines the game itself.

#### GameLab

Responsible for:

-   scenario generation;
-   simulation;
-   agent evaluation;
-   parameter exploration;
-   scenario validation;
-   optimisation;
-   evolutionary search;
-   difficulty estimation;
-   player-to-scenario matching.

#### Player Model

Maintains longitudinal estimates of demonstrated player abilities and
records their history.

#### Playground

Hosts simulations and interactive systems that may not have conventional
win/loss states.

#### Optional Runtime Adapters

Permit larger or fundamentally different engines to participate in
GameDeck without becoming core dependencies.

Examples:

-   retro computer emulator;
-   Doom-compatible engine;
-   heavyweight chess engine.

------------------------------------------------------------------------

## 4. Game module contract

A conceptual game module exposes functionality equivalent to:

``` text
GameModule
    manifest
    initialise(config, seed)
    legalActions(state, player)
    applyAction(state, action)
    status(state)
    score(state, player)
    serialize(state)
    deserialize(state)
    renderModel(state, viewer)
```

Optional capabilities:

``` text
generate(parameters, seed)
validate(scenario)
agents[]
parameterSpace
skillLoadings
scenarioMetrics
privateState
publicState
```

The implementation language may differ, but the conceptual boundary
should remain stable.

------------------------------------------------------------------------

## 5. Game manifest

Each game should provide metadata approximately equivalent to:

``` json
{
  "id": "mancala",
  "name": "Mancala",
  "version": "1.0.0",
  "players": {
    "minimum": 1,
    "maximum": 2
  },
  "modes": [
    "local",
    "cpu",
    "p2p",
    "simulation"
  ],
  "capabilities": {
    "procedural_generation": true,
    "replay": true,
    "hidden_information": false,
    "deterministic_seed": true
  },
  "skills": {
    "planning": 0.35,
    "tactics": 0.30,
    "probability": 0.05,
    "memory": 0.10,
    "resource_management": 0.20
  }
}
```

Values shown are illustrative rather than normative.

------------------------------------------------------------------------

## 6. Game state and event model

GameDeck should favour event-driven state transitions.

A game event might contain:

``` json
{
  "game_id": "example",
  "session_id": "uuid",
  "sequence": 17,
  "actor": "player_1",
  "action": "MOVE",
  "payload": {},
  "timestamp": "ISO-8601"
}
```

Benefits include:

-   replay;
-   multiplayer synchronization;
-   auditing;
-   deterministic reconstruction;
-   simulation;
-   debugging;
-   performance analysis.

Where deterministic reconstruction is possible, a game can be
represented by:

``` text
initial state + seed + ordered event stream
```

rather than repeated full-state snapshots.

Periodic snapshots may still be used for performance and recovery.

------------------------------------------------------------------------

## 7. Randomness

### 7.1 Runtime randomness

Games requiring genuine unpredictable outcomes should use a
cryptographically appropriate system RNG where available.

Examples:

-   dice;
-   coin toss;
-   shuffled cards;
-   casual random scenario selection.

### 7.2 Seeded randomness

GameLab and reproducible scenarios require seeded pseudo-randomness.

The seed must be recorded with:

-   generator version;
-   ruleset version;
-   relevant parameters.

### 7.3 Fairness

The platform must not alter random distributions covertly to manipulate
difficulty.

If a game uses biased or unusual random distributions as an explicit
rule variant, this must be represented as a parameter and be
inspectable.

------------------------------------------------------------------------

## 8. GameLab

GameLab is the principal differentiating capability.

It treats games as explorable mathematical systems.

### 8.1 Parameter spaces

Each game may expose parameters such as:

``` text
map size
resource abundance
enemy speed
enemy decision policy
information availability
turn budget
time limit
board connectivity
obstacle density
reward density
randomness
AI error
recovery opportunities
```

Parameters may be:

-   continuous;
-   discrete;
-   categorical;
-   conditional;
-   constrained.

### 8.2 Scenario generation

A basic generation pipeline is:

``` text
target profile
     │
     ▼
sample parameters
     │
     ▼
generate candidate
     │
     ▼
constraint validation
     │
     ▼
simulation
     │
     ▼
metric evaluation
     │
 ┌───┴────┐
 │        │
reject   accept
```

Generation may use:

-   random search;
-   constrained random generation;
-   heuristics;
-   optimisation;
-   evolutionary algorithms;
-   search over generated state spaces.

### 8.3 Simulation

GameLab should be able to run games without rendering.

Simulation should be substantially faster than real-time wherever the
game permits it.

Typical uses:

-   scenario validation;
-   balancing;
-   parameter exploration;
-   CPU evaluation;
-   procedural generation;
-   regression testing;
-   player-skill calibration.

### 8.4 Simulated player agents

A common conceptual agent hierarchy should include:

#### Random

Selects among legal actions randomly.

#### Greedy

Optimises immediate reward.

#### Heuristic

Uses game-specific sensible strategies.

#### Bounded

Searches a limited number of actions/turns ahead.

#### Strong

Uses a substantially competent strategy.

#### Near-optimal / solver

Used where computationally feasible.

#### Noisy expert

Usually makes strong decisions but introduces controlled error.

#### Human-like bounded agent

May model:

-   limited memory;
-   limited lookahead;
-   imperfect execution;
-   variable reaction;
-   probabilistic decision errors.

Not every game requires every agent.

### 8.5 Difficulty surfaces

GameLab should estimate outcomes across parameter combinations.

Example:

``` text
                       ENEMY SPEED
                 low                 high

random policy      92  81  69  52  31
                   87  74  62  44  24
                   79  65  48  31  15
                   70  53  34  20   8
predictive policy  58  39  22  10   3

                approximate player win %
```

Two scenarios with the same win probability may nevertheless have very
different experiential profiles.

GameLab should therefore retain multidimensional descriptors rather than
collapse all results into one difficulty number.

### 8.6 Skill expression

A useful game should often produce different outcomes for differently
capable agents.

For example:

``` text
Random agent        4%
Greedy agent       19%
Casual agent       44%
Strong agent       73%
Near-optimal       91%
```

This suggests meaningful skill expression.

By contrast:

``` text
Random             47%
Strong             54%
Near-optimal       58%
```

suggests that outcomes are dominated by randomness.

Neither is intrinsically invalid, but the distinction should be
measurable and selectable.

### 8.7 Decision leverage

GameLab should optionally estimate how consequential available decisions
are.

At a state `s`, alternative actions can be simulated to estimate their
effect on future success.

This can identify:

-   pivotal decisions;
-   inconsequential choices;
-   recoverable mistakes;
-   irreversible mistakes;
-   unfair hidden traps.

A generated puzzle should be rejectable when success depends on an
arbitrary early choice for which the player had no reasonable
information.

### 8.8 Winnable but not inevitable

Scenario generation should support target bands such as:

``` text
target competent-player win probability: 40–60%
strong-player win probability: >70%
random-agent win probability: <15%
```

The exact metrics are game-specific.

### 8.9 Evolutionary generation

GameLab may evolve scenarios:

``` text
generate population
       ↓
simulate
       ↓
score fitness
       ↓
retain candidates
       ↓
mutate / recombine
       ↓
repeat
```

Fitness may target multiple characteristics simultaneously.

Example:

``` text
win probability       0.45
luck sensitivity      low
planning demand       high
execution demand      low
median duration        25 min
recovery               medium
```

This enables generation toward a *style of challenge*, not merely a
numeric difficulty.

------------------------------------------------------------------------

## 9. Game profiles and presets

Users should not normally need to manipulate dozens of raw parameters.

GameLab should map higher-level profiles onto game-specific parameter
combinations.

Examples:

-   Relaxed;
-   Tactical;
-   Chaotic;
-   Exploratory;
-   Punishing;
-   Quick;
-   Long-form;
-   High deduction;
-   Low luck;
-   High planning;
-   Reflex-heavy.

A custom profile may resemble:

``` text
Luck              ██░░░
Planning          ████░
Execution         ███░░
Punishment        ██░░░
Exploration       █████
Time pressure     ░░░░░
Complexity        ████░
Session length    20–40 min
```

The meaning of those targets is resolved separately for each game.

------------------------------------------------------------------------

## 10. Player model and character sheet

The player is itself an evolving model.

### 10.1 Skill dimensions

Initial candidate dimensions include:

-   deduction;
-   spatial reasoning;
-   planning;
-   memory;
-   wordplay/vocabulary;
-   probability;
-   tactics;
-   reaction/execution;
-   risk management;
-   resource management.

The model must be extensible.

### 10.2 Skill estimates

A skill should not be treated as an unquestionable integer.

Internally, retain at least:

``` text
estimate
uncertainty/confidence
evidence count
last updated
```

The UI may simplify this into levels or progress bars.

### 10.3 Game skill loadings

Each game describes which abilities it exercises.

Example:

``` text
DETECTIVE CASE
deduction       .45
memory          .20
planning        .20
risk            .05
spatial         .10
```

Another game may overlap only partially.

This creates a cross-game skill graph.

### 10.4 XP as evidence

XP should not simply be awarded for winning.

Performance should provide evidence about relevant skills.

Possible factors include:

-   scenario difficulty relative to current estimate;
-   quality of decisions;
-   efficiency;
-   success/failure;
-   recovery from mistakes;
-   opponent strength;
-   luck contribution;
-   novelty of challenge.

Losing a difficult game may therefore still produce substantial
progression.

Repeatedly defeating trivial scenarios should produce little evidence of
improvement.

### 10.5 Cross-game progression

Improving a skill affects future challenges across every game that loads
on that skill.

For example, increased deduction may lead to:

-   less redundant detective clues;
-   more complex Mastermind spaces;
-   longer Minesweeper inference chains;
-   subtler information in procedural adventures.

It should not automatically increase unrelated reaction demands.

### 10.6 Adaptive probing

When uncertainty around a skill estimate is high, GameLab may
occasionally generate scenarios designed to learn more about the
player's capability.

This should remain within an enjoyable challenge range.

### 10.7 Training mode

Players may explicitly request experiences targeting a skill.

Example:

``` text
TRAIN: SPATIAL

Rogue             procedural navigation
Mancala           tactical position
Life              pattern challenge
Artillery         trajectory challenge
Detective         spatial evidence case
```

The platform therefore becomes a learning system without requiring games
to resemble conventional educational exercises.

------------------------------------------------------------------------

## 11. Persistent player history

The character sheet should include both numerical progression and
memorable history.

Possible records include:

-   games played;
-   wins/losses;
-   high scores;
-   longest games;
-   fastest wins;
-   win streaks;
-   generated scenario seeds;
-   exceptional scenarios;
-   achievements;
-   skill progression;
-   Rogue characters and causes of death;
-   detective cases solved;
-   virtual pets raised;
-   favourite games;
-   personal records;
-   notable multiplayer results.

Persistence should use simple portable data formats, principally JSON.

------------------------------------------------------------------------

## 12. High scores and records

A generic record might contain:

``` json
{
  "game_id": "blocks",
  "mode": "solo",
  "player": "player_id",
  "score": 12450,
  "timestamp": "ISO-8601",
  "metadata": {}
}
```

Games without conventional numeric scores may define records such as:

-   wins;
-   rating;
-   moves;
-   completion time;
-   level reached;
-   survival time;
-   turns;
-   territory;
-   streak;
-   efficiency.

------------------------------------------------------------------------

## 13. Saves and replays

Save files should contain sufficient information to resume play.

Replays should preferentially store:

``` text
game version
ruleset version
scenario seed
initial parameters
ordered action/event stream
```

Replays can support:

-   watching;
-   analysis;
-   debugging;
-   multiplayer dispute resolution;
-   player-model assessment;
-   sharing interesting procedural scenarios.

------------------------------------------------------------------------

## 14. Multiplayer

### 14.1 Supported modes

GameDeck should support, where appropriate:

-   solo;
-   CPU;
-   hot-seat;
-   local multi-controller;
-   peer-to-peer.

### 14.2 Transport abstraction

Game rules should not care whether an action originated:

-   locally;
-   from a controller;
-   from a CPU;
-   from another peer;
-   from a simulation agent.

All should ultimately enter the same validated action interface.

### 14.3 Public and private state

Hidden-information games require explicit separation.

Example for Battleship:

``` text
PUBLIC
turn
shots
hits
misses
score

PRIVATE
ship positions
```

The same model supports:

-   hidden card hands;
-   secret words;
-   drawing prompts;
-   Mastermind codes;
-   hidden roles.

### 14.4 Commit--reveal

Where useful, hidden initial state may be committed cryptographically
before play.

Example:

``` text
hash(secret_state + nonce)
```

The secret and nonce can be revealed later and verified.

This can prevent post-hoc alteration of hidden information such as
moving ships after the game has begun.

### 14.5 Pictionary-style drawing

Drawing games should transmit compact vector/stroke events rather than
repeated screenshots.

------------------------------------------------------------------------

## 15. CPU players

CPU capability should be game-specific but use common interfaces.

Possible levels:

``` text
Sleepy
Casual
Sharp
Merciless
```

Internally these may correspond to:

-   random;
-   heuristic;
-   shallow search;
-   deeper search;
-   specialised engine.

Strong external engines should be optional where they would materially
increase installation size or CPU requirements.

Examples:

-   basic chess CPU in core game;
-   Stockfish as optional advanced engine;
-   lightweight Go opponent by default;
-   stronger Go engine only as an optional module.

------------------------------------------------------------------------

## 16. Universal visual language

GameDeck should have a coherent visual identity without forcing every
game to look identical.

Principles:

-   crisp geometric design;
-   slightly physical/dimensional board-game feel;
-   smooth easing;
-   restrained shadows;
-   common typography;
-   common score and status language;
-   common pause/settings overlays;
-   clear interaction feedback;
-   tactile but lightweight animation.

### 16.1 Motion

Pieces should move rather than teleport.

Examples:

-   Ludo pieces travel through intermediate spaces;
-   chess pieces drag freely and snap cleanly;
-   captures have brief physical feedback;
-   dice visibly roll before settling;
-   artillery trajectories animate continuously.

Typical ordinary movement should feel immediate, approximately in the
150--300 ms range where appropriate.

### 16.2 Input

Primary interactions should feel direct.

Prefer:

-   drag;
-   swipe;
-   controller;
-   direct touch.

Tap-select/tap-destination should remain available as an accessibility
and low-precision alternative rather than being the sole interaction
model.

### 16.3 External engines

Where an emulator or external engine owns its framebuffer, GameDeck
supplies the surrounding shell rather than attempting to redraw the
content.

------------------------------------------------------------------------

## 17. Playground

Playground hosts systems that are interesting to manipulate even without
a conventional win condition.

Initial candidates:

-   Conway's Life;
-   Langton's Ant;
-   elementary cellular automata;
-   boids;
-   procedural maze generation;
-   physics toys;
-   reaction-diffusion;
-   virtual life/pet simulation;
-   GameLab itself.

Playground systems should reuse:

-   persistence;
-   parameter controls;
-   simulation;
-   visualization;
-   replay/history;
-   export where useful.

------------------------------------------------------------------------

## 18. Virtual life / pet system

An original virtual-pet system should model longitudinal state rather
than merely display simple hearts.

Possible state variables:

``` text
hunger
health
energy
happiness
stress
discipline
social need
age
weight
sleep debt
```

Possible events:

``` text
fed
played
ignored
disciplined
treated
slept
woken
overfed
requested attention
became ill
recovered
```

### 18.1 Life record

The complete life should be retained as an event history.

Example:

``` text
08:04  woke
08:17  fed
10:32  requested attention
10:49  played
12:03  hungry
12:41  fed
14:20  unhappy
```

### 18.2 Retrospective visualization

Users should be able to inspect relationships among:

-   feeding;
-   hunger;
-   mood;
-   activity;
-   sleep;
-   health;
-   care patterns.

### 18.3 Teaching and simulation mode

The virtual-life engine can demonstrate:

-   longitudinal data;
-   feedback;
-   lagged effects;
-   cumulative exposure;
-   adherence;
-   stochastic events;
-   observation schedules;
-   intervention strategies.

GameLab may simulate large populations of virtual creatures under
different care regimes.

The virtual pet may optionally act as the player's persistent GameDeck
companion/avatar.

------------------------------------------------------------------------

## 19. Initial game families

The catalogue is illustrative rather than exhaustive.

### 19.1 Chance and tabletop utilities

-   configurable dice;
-   multiple heterogeneous dice;
-   roll histories;
-   summed dice;
-   coin toss;
-   counters;
-   hit points;
-   EXP counters;
-   campaign records;
-   generic high-score recording.

### 19.2 Board and abstract games

-   Chess;
-   Go;
-   Draughts/Checkers;
-   Mancala;
-   Ludo;
-   Snakes & Ladders;
-   Connect Four;
-   Reversi-like game;
-   Backgammon;
-   Mastermind;
-   Battleship-like hidden fleet game;
-   territory/conquest game inspired by classic world-conquest
    mechanics.

### 19.3 Card and tile games

-   Solitaire/patience family;
-   rummy/tile-run game;
-   shedding-card game;
-   Mahjong solitaire;
-   other compact traditional card games.

### 19.4 Word and social games

-   crossword tile word game;
-   Boggle-like timed word grid;
-   category/scatter game;
-   P2P drawing and guessing;
-   forehead/phone guessing game;
-   other compact party games.

Where commercial names, art or content are protected, GameDeck should
implement generic mechanics under original names and presentation unless
reuse is clearly permitted.

### 19.5 Puzzle and arcade

-   Minesweeper;
-   falling blocks;
-   Snake;
-   maze chase;
-   Breakout-like paddle game;
-   Asteroids-like vector game;
-   invader-style game;
-   artillery;
-   2048-like number sliding game.

### 19.6 Adventure

-   Rogue-like procedural dungeon;
-   parser-based interactive fiction;
-   original Zork-like adventures;
-   procedural detective/investigation games.

### 19.7 Print-and-play-derived systems

Print-and-play games are particularly attractive when their mechanics
are:

-   compact;
-   transparent;
-   parameterisable;
-   solo-friendly;
-   procedurally extensible.

Potential mechanical families include:

-   roll-and-write;
-   flip-and-write;
-   fence/enclosure puzzles;
-   polyomino placement;
-   deduction;
-   route building;
-   resource optimisation;
-   push-your-luck;
-   solitaire micro-card games;
-   procedural investigations.

The objective is not to copy copyrighted authored scenarios. It is to
identify suitable mechanics, respect licensing and attribution, and
where appropriate reconstruct a general generative grammar capable of
producing original scenarios.

------------------------------------------------------------------------

## 20. Procedural detective games

A detective engine is a particularly important GameLab demonstration.

A case specification may include:

-   map topology;
-   rooms/locations;
-   evidence;
-   evidence dependencies;
-   suspects/entities;
-   clue visibility;
-   action/turn budget;
-   spatial constraints;
-   red herrings;
-   inference chains;
-   scoring.

GameLab can generate cases subject to constraints such as:

``` text
deduction demand       high
spatial demand         medium
time pressure          low
luck                    low
inference depth         4–6
redundant evidence      moderate
target win probability  45–65%
```

Simulation should reject:

-   impossible cases;
-   trivial cases;
-   cases with contradictory evidence;
-   cases whose solution requires inaccessible information;
-   cases dominated by arbitrary guessing.

------------------------------------------------------------------------

## 21. Rogue

The Rogue-like implementation should be deliberately compact.

Features may include:

-   procedural dungeon;
-   deterministic seed;
-   permadeath;
-   monsters;
-   items;
-   health;
-   resources;
-   score;
-   level reached;
-   turns;
-   cause of death.

GameLab should be able to vary:

-   dungeon topology;
-   monster density;
-   monster intelligence;
-   healing;
-   loot;
-   visibility;
-   branching;
-   resource scarcity.

A complete run should be reproducible from its seed and event history.

------------------------------------------------------------------------

## 22. Cellular automata

Conway's Life should provide:

-   touch/drag cell editing;
-   play;
-   pause;
-   single-step;
-   speed control;
-   pan/zoom;
-   edge wrapping option;
-   pattern presets;
-   import/export of suitable standard pattern formats where feasible.

The same framework should permit other cellular automata.

------------------------------------------------------------------------

## 23. Retro computing

Retro support should use optional emulator runtimes rather than repeated
ports wherever practical.

An Acorn Electron/BBC-style runtime is a priority candidate.

Principles:

-   emulator is optional;
-   software images are separately managed;
-   GameDeck does not assume old commercial software is redistributable;
-   openly licensed/public-domain software may be packaged only where
    its licence permits;
-   user-supplied software images remain separate from GameDeck;
-   GameDeck owns controls, snapshots, input mapping and surrounding UI;
-   the emulated framebuffer remains authentic.

------------------------------------------------------------------------

## 24. Doom-compatible runtime

GameDeck should be able to truthfully claim that it runs Doom-compatible
games without bloating the base installation.

Design:

``` text
games/
    doom/
        engine.wasm
        host
        manifest

optional-content/
    doom-compatible-data/
```

Principles:

-   use the smallest practical engine;
-   engine remains optional if necessary;
-   commercial Doom WADs are never bundled without appropriate rights;
-   freely distributable compatible content may be offered separately
    where licensing permits;
-   users may provide their own compatible content;
-   GameDeck handles controller mapping, records and shell integration.

Doom support is an architectural demonstration, not justification for
turning GameDeck into a large distribution.

------------------------------------------------------------------------

## 25. Controller abstraction

Games consume logical actions rather than hardware-specific key codes.

Baseline controller actions:

``` text
UP
DOWN
LEFT
RIGHT
A
B
X
Y
START
SELECT
L
R
```

Optional analogue inputs may be added later.

Supported sources may include:

-   keyboard;
-   touchscreen;
-   mouse/pointer;
-   standard Bluetooth controller;
-   custom ESP32 controller.

------------------------------------------------------------------------

## 26. ESP32 gamepad

A small dedicated GameDeck controller should be feasible using an ESP32
with Bluetooth HID.

### 26.1 Version 1 target

Keep the first version minimal:

-   ESP32;
-   D-pad;
-   A/B/X/Y;
-   Start;
-   Select;
-   Bluetooth HID;
-   battery/power system.

Optional later additions:

-   L/R buttons;
-   rechargeable LiPo;
-   small OLED;
-   vibration;
-   analogue sticks;
-   player indicator.

The controller should appear to the host as a conventional gamepad
wherever possible. Game-specific ESP32 protocols should be avoided.

------------------------------------------------------------------------

## 27. Licensing and provenance

Reuse of open-source code is encouraged where it reduces duplicated
effort or provides a mature rules engine.

Every reused component must record:

``` text
name
source repository
upstream version / commit
licence
copyright notice
modifications
required attribution
```

### 27.1 Separation of mechanics and assets

A game's mechanics may be implementable even where its:

-   name;
-   artwork;
-   characters;
-   text;
-   scenarios;
-   maps;
-   audio;
-   branding

cannot be reused.

GameDeck should favour original names, art and content when implementing
familiar mechanics.

### 27.2 Imported engines

Licensing implications must be assessed before an engine is combined
with the core application.

Strong-copyleft engines may require architectural separation or may be
unsuitable depending on distribution strategy.

------------------------------------------------------------------------

## 28. Persistence model

Suggested structure:

``` text
data/
    players.json
    highscores.json
    achievements.json
    games/
    saves/
    replays/
    generated/
    pets/
```

A player record may contain:

``` json
{
  "id": "player_id",
  "name": "Player",
  "skills": {},
  "traits": [],
  "games_played": {},
  "achievements": [],
  "records": []
}
```

Schema versioning is required from the beginning.

------------------------------------------------------------------------

## 29. Character traits and achievements

Traits may summarize patterns in the underlying player model.

Examples:

-   Investigator;
-   Pattern Hunter;
-   Calculated Gambler;
-   Explorer;
-   Tactician;
-   Wordsmith;
-   Survivor.

Traits should normally be derived from evidence rather than arbitrary
grinding.

Achievements may celebrate unusual events without affecting skill
estimates.

------------------------------------------------------------------------

## 30. GameDeck as a learning system

The system should permit entertainment and learning to coexist without
forcing either interpretation.

A player may simply play games.

Underneath, GameDeck can estimate:

-   demonstrated strengths;
-   developing skills;
-   uncertainty in estimates;
-   cross-game transfer;
-   preferred challenge styles.

A user may optionally inspect this information or explicitly request
training.

The system should avoid presenting inferred cognitive skills as
clinical, diagnostic or psychometric truth. They are game-performance
estimates within GameDeck's own model.

------------------------------------------------------------------------

## 31. GameLab designer interface

GameLab should itself be usable interactively.

A designer/player can:

1.  choose a game;
2.  expose its parameter space;
3.  alter parameters;
4.  run simulations;
5.  visualize outcomes;
6.  inspect difficulty surfaces;
7.  compare agents;
8.  generate candidate scenarios;
9.  evolve scenarios toward a target;
10. play a selected scenario immediately.

Possible visualizations include:

-   heat maps;
-   response surfaces;
-   distributions;
-   agent win curves;
-   decision leverage;
-   skill/luck decomposition;
-   parameter sensitivity;
-   scenario populations over evolutionary generations.

This makes GameLab useful both as infrastructure and as an interactive
computational-ludology sandbox.

------------------------------------------------------------------------

## 32. Preset generation from player profile

A generic matching process is:

``` text
PLAYER MODEL
     +
DESIRED EXPERIENCE
     +
GAME SKILL LOADINGS
     +
GAME PARAMETER MODEL
     │
     ▼
TARGET SCENARIO PROFILE
     │
     ▼
GENERATION / OPTIMISATION
     │
     ▼
VALIDATION / SIMULATION
     │
     ▼
PLAYABLE SCENARIO
```

Examples:

> Generate a Rogue dungeon expected to give this player a 35--45% chance
> of success, with high planning demand and low reaction demand.

> Generate a Minesweeper board slightly beyond the player's current
> deduction estimate, with no required guesses.

> Generate a detective case with high deduction, medium memory demand
> and no time pressure.

------------------------------------------------------------------------

## 33. Performance requirements

The platform should be efficient enough that ordinary games require
negligible modern CPU resources.

### 33.1 Runtime

Board, card and puzzle games should normally:

-   run comfortably on low-end contemporary hardware;
-   avoid continuous computation when idle;
-   animate at the display refresh rate where practical;
-   separate simulation from rendering.

### 33.2 Simulation

GameLab simulation should:

-   run headless;
-   avoid rendering;
-   support batching;
-   permit parallel execution where useful;
-   allow bounded simulation budgets;
-   cache reusable results where appropriate.

### 33.3 Heavy components

Components such as:

-   strong chess engines;
-   strong Go engines;
-   emulators;
-   Doom;
-   very large evolutionary runs

must not impose their cost when unused.

------------------------------------------------------------------------

## 34. Security and integrity

For multiplayer:

-   validate all received actions against local rules;
-   never trust remote clients to declare legal moves;
-   separate private and public state;
-   authenticate session peers where practical;
-   prevent malformed state from crashing a game;
-   bound message size and frequency.

For persistent records:

-   use schema validation;
-   tolerate corrupted optional records gracefully;
-   avoid allowing one malformed save to make the whole application
    unusable.

------------------------------------------------------------------------

## 35. Accessibility

Games should provide alternatives to interaction patterns that depend
solely on:

-   dragging;
-   colour;
-   sound;
-   rapid reaction;
-   fine motor control.

Where mechanics permit:

-   keyboard/controller navigation;
-   tap-select/tap-destination;
-   reduced-motion mode;
-   high-contrast rendering;
-   configurable animation speed;
-   readable text scaling;
-   sound-independent cues

should be supported.

GameLab may also generate lower-execution-demand scenarios without
necessarily reducing strategic complexity.

------------------------------------------------------------------------

## 36. Non-goals

GameDeck is **not** intended to become:

-   a collection of unrelated game applications;
-   a giant ROM/software archive;
-   an unlicensed clone collection;
-   a cloud-dependent gaming platform;
-   a system that covertly manipulates outcomes;
-   a replacement for specialist competitive chess/Go software;
-   a psychometric diagnostic instrument.

A game should normally be expressed as a specification over shared
platform capabilities. Bespoke engines are exceptional adapters.

------------------------------------------------------------------------

## 37. Initial implementation sequence

### Phase 0 --- Core contracts

Implement:

-   game manifest;
-   state/action interface;
-   event stream;
-   seeded RNG;
-   persistence schemas;
-   player identity;
-   basic renderer/input contract.

### Phase 1 --- Tiny proving games

Implement:

-   dice/coin;
-   Connect Four;
-   Minesweeper;
-   Snakes & Ladders;
-   Conway's Life.

Purpose:

-   prove rules interface;
-   persistence;
-   animation;
-   scores;
-   seeds;
-   basic CPU;
-   Playground.

### Phase 2 --- GameLab v1

Implement:

-   parameter definitions;
-   headless simulation;
-   random/heuristic agents;
-   parameter sweeps;
-   scenario validation;
-   simple difficulty metrics;
-   interactive visualization.

Use Minesweeper and Connect Four as initial laboratories.

### Phase 3 --- Player model

Implement:

-   skill dimensions;
-   skill loadings;
-   uncertainty;
-   XP/evidence updates;
-   cross-game character sheet;
-   adaptive scenario selection.

### Phase 4 --- Rich board games

Add:

-   Mancala;
-   Ludo;
-   Chess;
-   Draughts;
-   Mastermind;
-   Battleship.

### Phase 5 --- P2P

Implement:

-   session creation;
-   peer discovery/connection strategy;
-   synchronized event streams;
-   public/private state;
-   commit--reveal;
-   reconnect/recovery.

Battleship should be the principal hidden-information test.

### Phase 6 --- Procedural games

Add:

-   Rogue;
-   procedural detective engine;
-   solitaire generation;
-   roll-and-write / PnP-derived mechanics.

Implement evolutionary generation.

### Phase 7 --- Arcade and social

Add:

-   falling blocks;
-   maze chase;
-   artillery;
-   word grid;
-   drawing/guessing;
-   category games;
-   other social P2P games.

### Phase 8 --- Virtual life

Implement:

-   virtual pet;
-   longitudinal state;
-   event history;
-   life dashboard;
-   simulation mode.

### Phase 9 --- Optional runtimes

Add optional:

-   Acorn Electron/BBC-style emulator;
-   Doom-compatible runtime;
-   heavyweight CPU engines.

### Phase 10 --- Hardware

Prototype:

-   ESP32 BLE HID controller;
-   GameDeck controller mapping;
-   physical enclosure.

------------------------------------------------------------------------

## 38. Acceptance criteria

### Core

A compliant game must be able to:

-   initialize;
-   expose legal actions;
-   accept validated actions;
-   produce state;
-   terminate correctly;
-   serialize;
-   restore.

### Reproducibility

Given the same:

-   game version;
-   ruleset;
-   parameters;
-   seed;
-   action stream,

a deterministic/reproducibly stochastic game must reproduce the same
outcome.

### GameLab

For a GameLab-enabled game, the platform must be able to:

-   generate multiple candidate scenarios;
-   reject invalid scenarios;
-   simulate without rendering;
-   compare at least two agent strategies;
-   calculate at least one meaningful challenge metric;
-   reproduce an accepted generated scenario.

### Adaptive player model

The platform must demonstrate that:

1.  performance in Game A changes at least one relevant player-skill
    estimate;
2.  the changed estimate can alter generation parameters in Game B;
3.  unrelated skill dimensions need not change;
4.  the generated Game B scenario is fixed before play begins.

### Multiplayer

A P2P game must:

-   synchronize legal actions;
-   reject illegal remote actions;
-   preserve private state;
-   recover cleanly from normal temporary interruption where feasible.

### Persistence

High scores, player progression, saves and replays must survive
application restart using versioned portable storage.

------------------------------------------------------------------------

## 39. Architectural tests

The following should be treated as design tests.

### Test A --- New tiny game

Can a simple deterministic board game be added primarily by implementing
the game contract rather than creating new platform infrastructure?

### Test B --- New procedural game

Can a new generator use existing GameLab simulation, agents, metrics and
validation infrastructure?

### Test C --- New P2P game

Can a new multiplayer game reuse the existing transport without
implementing its own networking?

### Test D --- New controller

Can a new physical controller map to GameDeck actions without
game-specific changes?

### Test E --- Heavy engine

Can Doom/emulation/strong AI remain absent without affecting ordinary
GameDeck operation?

### Test F --- Cross-game learning

Can skill demonstrated in one game meaningfully influence an appropriate
dimension of another game without merely increasing global difficulty?

If these tests fail, the architecture is becoming too game-specific.

------------------------------------------------------------------------

## 40. Longer-term possibilities

The architecture permits, but does not initially require:

-   community-authored game specifications;
-   shareable scenario seeds;
-   downloadable scenario packs;
-   user-created presets;
-   tournaments;
-   LAN play;
-   cooperative Rogue;
-   campaign systems;
-   procedural interactive fiction;
-   generated puzzle-of-the-day challenges;
-   comparative game-mechanics research;
-   automated rule balancing;
-   game-design teaching;
-   classroom simulation experiments;
-   multiplayer ESP32 controllers;
-   a persistent GameDeck companion.

------------------------------------------------------------------------

## 41. Summary

GameDeck is a lightweight universal play environment built around a
simple proposition:

**games are executable rules systems rather than isolated
applications.**

GameLab makes those systems inspectable and generative. It can simulate
them, measure them, search their parameter spaces and generate new
scenarios that satisfy explicit constraints.

The player model closes the loop. Performance across different games
contributes evidence about a multidimensional character sheet. GameLab
uses that profile to create future challenges matched to what the player
has demonstrated, while preserving fixed and fair rules during actual
play.

This produces a platform in which:

-   games share infrastructure;
-   scenarios can be effectively infinite;
-   generated content is validated before play;
-   difficulty has multiple interpretable dimensions;
-   progression transfers across games;
-   multiplayer is a platform feature;
-   CPU opponents are reusable agents;
-   simulations and toys coexist with conventional games;
-   retro engines and Doom remain optional;
-   hardware controllers remain generic;
-   records and histories remain portable and inspectable.

The intended result is not a large bundle of unrelated games.

It is a small rules, simulation and play system capable of expressing a
very large number of them.

> **Never a bad map.**
>
> **Never the same case twice.**
>
> **The games change because the player has changed.**
