# Generic MethodMesh transport substrate

The transport substrate carries MethodMesh envelopes between the app and external transport implementations. It is deliberately transport-neutral: core knows that a message was sent or received, but does not know whether the provider uses BLE, serial, LAN, radio or another link.

```text
modules / protocols / ODK
            │ logical envelope
            ▼
core transport runtime
 envelope · durable inbox/outbox · dedupe · dispatch
            │ provider contract
            ▼
transport implementation
```

`MethodMeshTransportEnvelope` contains stable message identity, logical source and destination endpoints, message classification, optional module/capability routing, timestamps, correlation fields, an opaque versioned payload and bounded metadata. Transport-local addresses stay inside providers and bindings.

The runtime persists an incoming envelope before dispatch and suppresses repeated message IDs. Outbound envelopes are persisted before a provider is called; an unavailable provider leaves the message queued or retryable. `SENT` and `DELIVERED` remain separate states. Inbox and outbox journals are bounded and prunable.

Modules register a `MethodMeshTransportProvider` and may register consumers for logical endpoints. A provider may accept queued work while disconnected, or report a retryable failure. Provider failures are isolated from other providers and from the app process.

The first ESP mesh module owns the future BLE gateway, provisioning, ESP-NOW framing, routing, authentication and firmware. None of those concepts are part of core. A new transport should implement the provider contract, register it through its module, and keep its local addressing and lifecycle requirements inside that module.

The substrate does not bypass capability Commit semantics, create an ODK-specific message system, or imply that every produced payload becomes a persistent Files artifact.
