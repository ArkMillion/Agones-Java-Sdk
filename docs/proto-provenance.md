# Protobuf provenance

The vendored wire definitions derive from Agones tag `v1.61.0`.

| Local file | Official source | Official SHA-256 |
| --- | --- | --- |
| `agones/sdk.proto` | `proto/sdk/sdk.proto` | `f5a4a7a8e9937ed9cf7d992141d3fb90f734ad058999a89b4a66f4ab9de096e0` |
| `agones/beta.proto` | `proto/sdk/beta/beta.proto` | `ab8722d966ca45080659e935f6e42f6c5681d10917edd3399d0ca260caa114b4` |

Local copies remove Go options and HTTP/OpenAPI-only annotations and add internal Java generation options. RPC shapes, streaming directions, field types, and field numbers are unchanged. Run `scripts/verify-proto-provenance.ps1` to verify the pinned sources and declarations.
