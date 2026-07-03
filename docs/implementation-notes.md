# ResearchOS Engineering Journal

This document records the evolution of the ResearchOS implementation.

It is intentionally informal. Rather than documenting every code change, it captures the architectural decisions, implementation milestones and lessons learned during development.

---

# Vision

ResearchOS is an open, modular architecture for designing, executing and preserving scientific research.

The objective is to create a capability-first research platform in which reusable methods generate interoperable knowledge represented as a graph. The implementation should remain grounded in real research workflows rather than abstract software architecture.

A key engineering principle is:

> Build the minimum architecture required, then prove it through implementation.

---

# Engineering Principles

- Prefer implementation over speculation.
- Extend the architecture only when implementation demands it.
- Keep the canonical knowledge model stable.
- Preserve backwards compatibility where practical.
- Migrate incrementally rather than rewrite.
- Build vertical slices that demonstrate end-to-end functionality.

---

# Roadmap

## Phase 1 — ResearchOS Kernel ✅

Completed

- Canonical knowledge objects
    - Entity
    - Observation
    - Relationship
    - ObjectRef
- ResearchGraph
- Runtime execution model
- Unit testing
- First executable graph

---

## Phase 2 — Runtime Migration (Current)

Objective:

Replace legacy module-specific outputs with canonical ResearchOS knowledge objects.

Completed

- NFC capability
- GPS capability
- Calibrated Scale capability
- Admin Fingerprint capability

Each capability now produces canonical `Observation` objects while preserving backwards compatibility through adapters.

Current architecture:

Capability

↓

Method

↓

Observation

---

## Phase 3 — Live Research Session (Current)

Objective:

Replace the demonstration graph with a live graph representing the current research session.

Completed

- ResearchSession
- ResearchRuntime singleton
- Graph UI now reads from the live runtime rather than a synthetic graph.

Remaining

- Capabilities automatically insert observations into the ResearchSession.
- Remove DemoResearchGraph.
- Live graph updates from user interaction.

---

## Phase 4 — Protocol Runtime

Objective:

Execute protocols rather than individual capabilities.

Conceptually:

Study

↓

Protocol

↓

Capability

↓

Method

↓

Observation

↓

Knowledge Graph

---

## Phase 5 — Persistence

Represent the complete ResearchGraph as a portable object model.

Possible targets include:

- JSON
- Room
- Neo4j
- RDF

Persistence should remain an implementation detail rather than the architectural centre of the system.

---

## Phase 6 — Research Representation

Stretch goal

Demonstrate that ResearchOS can represent:

- a study design
- a protocol
- a complete study dataset

using only the canonical graph model.

---

# Engineering Log

## 2026-07-03

Major architectural milestone.

The project transitioned from architectural design to implementation.

Completed:

- Implemented the canonical ResearchOS knowledge model.
- Introduced Entity, Observation, Relationship and ResearchGraph.
- Displayed the first executable ResearchGraph within the Android application.
- Established a single live ResearchSession and ResearchRuntime.
- Migrated the first production capabilities (NFC, GPS, Calibrated Scale and Admin Fingerprint) to produce canonical Observation objects.
- Established the adapter pattern for incremental migration from legacy runtime objects.
- Completed the first end-to-end implementation from capability to canonical knowledge object.

Most importantly, development philosophy shifted from:

Design architecture

to

Implement architecture.

Future work should prioritise implementing real research workflows rather than extending the architectural model.

---

# Notes

One important insight emerged during implementation.

ResearchOS is not organised around forms, records or databases.

It is organised around **Capabilities**.

Capabilities are the user-facing units of functionality (e.g. NFC, GPS, Camera, BLE, Sample Tracking). Internally, each capability orchestrates device services, signals, methods and knowledge generation while exposing a coherent piece of functionality to the researcher.

This capability-first organisation appears to be one of the defining characteristics of the ResearchOS architecture.


