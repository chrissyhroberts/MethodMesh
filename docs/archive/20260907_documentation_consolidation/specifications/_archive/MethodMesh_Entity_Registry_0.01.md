# MethodMesh Entity Registry v0.01

**Status:** Draft

---

# 1. Purpose

The Entity Registry defines the canonical vocabulary of Entity types recognised by MethodMesh.

This registry extends the MethodMesh Conceptual Model.

It does not define execution behaviour.

It defines the classes of objects that may become the subject of observation, measurement, description or reasoning within a research context.

---

# 2. Definition

An Entity is defined as:

> **A distinct object that may become the subject of observation, measurement, description or reasoning within a research context.**

An Entity possesses identity.

An Entity may possess Properties.

An Entity may participate in Relationships.

An Entity may emit Signals.

An Entity may be acted upon by Methods.

Entities may exist in different Modes.

Entities may possess different Traits.

---

# 3. Design Principles

The Entity Registry follows five principles.

## Identity

Every Entity possesses a stable identity.

## Independence

Entities exist independently of observations made about them.

## Composability

Entities are described through Modes, Traits and Relationships rather than deep inheritance.

## Generality

The registry is intended to be discipline-independent.

## Extensibility

New Entity types may be introduced without modifying the conceptual model.

---

# 4. Registry Entry Model

Every Entity definition contains the following fields.

| Field | Description |
|-------|-------------|
| **Name** | Canonical entity name |
| **Description** | Short definition |
| **Typical Mode** | Physical, Digital or Conceptual |
| **Typical Status** | Typical lifecycle state (optional) |
| **Typical Traits** | Common characteristics |
| **Common Relationships** | Frequently encountered relationship types (informative only) |
| **Examples** | Informative examples |

The Common Relationships field is informative only. It identifies relationship types that are commonly associated with the Entity. The formal definition, semantics and constraints of relationships are specified separately in the MethodMesh Relationship Registry.

---

# 5. Canonical Entity Types

The following sections define the initial canonical Entity vocabulary.

## Living Organisms

Participant
Patient
Volunteer
Researcher
Operator
Observer
Animal
Plant
Tree
Crop
Livestock
Microorganism
Virus
Bacterium
Fungus
Parasite

## Human Collections
Collective Entities represent groups whose identity is independent of the identities of their members.

Household
Family
Community
Population
Cohort
Organisation
Institution
Research Consortium

## Built Environment

House
Building
Room
Clinic
Hospital
Laboratory
School
Road
Bridge
Farm

## Equipment

Camera
PCR Machine
Microscope
Thermometer
Scale
Computer
Phone
Vehicle
Drone
GPS Receiver
NFC Reader

## Specimens

Specimen
Blood Sample
Serum
Plasma
Aliquot
Swab
Tissue
Soil Sample
Water Sample

## Information Entities

Study
Project
Dataset
Image
Audio Recording
Video
Protocol
Report
Questionnaire
Consent Form
Publication

## Geographical Entities

Country
Region
District
Village
Settlement
River
Forest
Lake
Mountain
Border




