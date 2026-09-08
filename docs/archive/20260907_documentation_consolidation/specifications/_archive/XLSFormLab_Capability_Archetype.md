# XLSForm Lab Capability Archetype

## 1. Capability identity

Every capability must define a stable identity.

``` kotlin
id = "calibrated_scale"
name = "Calibrated Scale"
version = "1.0.0"
category = CapabilityCategory.Measurement
tags = listOf("pain", "vas", "scale", "measurement")
```

The `id` must be stable once released. New behaviour should be
introduced through versioning.

## 2. Manifest

Each capability provides a manifest describing what it is, where it
appears, what it supports, and what it returns.

## 3. Settings schema

Settings define persistent admin defaults. These are used unless
temporarily overridden by an ODK intent.

## 4. Intent override schema

Intent overrides are temporary and must never alter stored admin
defaults.

## 5. Demo mode

Every capability provides a standalone demo.

## 6. Settings mode

Every capability exposes configurable defaults.

## 7. Help mode

Every capability provides embedded documentation.

## 8. Execution contract

Every capability accepts a standard request and returns a standard
result.

## 9. Return contract

Capabilities declare their returned fields and JSON schema.

## 10. Copy XLSForm intent

Every capability generates a ready-to-paste XLSForm intent template.

## 11. Developer diagnostics

Developer mode exposes effective settings, incoming intent, returned
values and diagnostics.

## 12. File structure

``` text
modules/
  examplecapability/
    ExampleCapabilityModule.kt
    ExampleCapabilityManifest.kt
    ExampleCapabilityDemo.kt
    ExampleCapabilityHelp.kt
    ExampleCapabilityExecution.kt
    ExampleCapabilityReturnSchema.kt
```

## 13. Rule

**Everything is a capability.**

The shell should contain no special-case logic for individual modules.
