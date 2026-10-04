# ADR-003: Public model boundary

Status: Accepted

Public models are immutable top-level types in `cn.arkmillion.agones.model`. Defensive unmodifiable collections prevent generated protobuf details from leaking into user code.
