# ADR-002: Protocol source and compatibility

Status: Accepted

The wire contract derives from official Agones v1.61.0 protobuf files. HTTP/OpenAPI annotations are removed because they do not alter gRPC compatibility. Generated types remain internal.
