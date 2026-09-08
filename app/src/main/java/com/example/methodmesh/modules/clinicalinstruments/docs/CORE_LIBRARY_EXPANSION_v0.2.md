# Clinical Instruments core library expansion v0.2

The v0.2 prototype expands the immutable core library from 4 to 19 executable linear instruments.

## Acute / bedside

- qSOFA
- CRB-65
- CURB-65
- AVPU
- 4AT delirium assessment v1.2
- PERC rule
- Wells PE (2-level)
- Wells DVT (2-level)
- Modified Centor / McIsaac
- SIRS criteria
- Shock Index
- Modified Shock Index

## Cardiovascular

- CHA2DS2-VASc
- HAS-BLED

## Mental health

- PHQ-2
- PHQ-9
- GAD-2
- GAD-7

## Nutrition

- Adult BMI classification

## Admission rule

A definition enters the immutable core only when:

1. it is faithfully representable by the v0.1 linear engine;
2. item wording/criteria and scoring can be checked against an authoritative source;
3. redistribution is defensible (public domain, explicit reusable licence/no-permission statement, or factual numerical rule rather than reproduced proprietary prose);
4. the definition includes at least one executable regression case;
5. the parser accepts the definition and all embedded cases pass.

## Deliberately not bundled yet

- Clinical Frailty Scale: permission from Dalhousie/GMR is required before redistribution.
- AQ-10: ARC free-use terms are limited to non-profit/research contexts; commercial/IT use may require permission/licence.
- WHO-5: CC BY-NC-SA 3.0 IGO creates a non-commercial/share-alike compatibility question for a general-purpose app distribution.
- NEWS2: authoritative and highly desirable, but the RCP instrument/reproduction position should be resolved explicitly before shipping an embedded electronic copy.
- WHO Verbal Autopsy: requires branching/relevance and therefore belongs to the future protocol engine rather than this linear checklist engine.

These remain priority candidates rather than being silently copied into core.
