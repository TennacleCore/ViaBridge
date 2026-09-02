# ViaBridge

Backend servers cannot call ViaVersion when Via runs only on Velocity. ViaBridge exposes `VersionedPacketTransformer` over plugin messages (`viabridge:rpc`).

## Modules

| Module | Role |
|--------|------|
| `protocol` | Wire format |
| `viabridge-velocity` | Velocity plugin (requires ViaVersion) |
| `viabridge-client` | Optional Minestom helper library |

Player protocol version: Via's `vv:proxy_details` — not duplicated here.

## RPC (wire v1)

Frame: `u8 version`, `varint requestId`, `varint opcode`, payload.

`requestId = 0` → fire-and-forget (no response). Use for hot paths.

### Opcodes

| Opcode | Name | Via API |
|--------|------|---------|
| `0` | `PING` | liveness |
| `1` | `SEND_CLIENTBOUND` | `transformer.send` |
| `2` | `SEND_SERVERBOUND` | `transformer.send` |
| `3` | `SCHEDULE_SEND_CLIENTBOUND` | `transformer.scheduleSend` |
| `4` | `SCHEDULE_SEND_SERVERBOUND` | `transformer.scheduleSend` |
| `5` | `TRANSFORM_CLIENTBOUND` | `transformer.transform` |
| `6` | `TRANSFORM_SERVERBOUND` | `transformer.transform` |

Opcodes `1`–`6` share one payload:

```
varint inputProtocolId
string packetClass    // e.g. ClientboundPackets1_21_6
string packetType     // e.g. SET_ENTITY_MOTION
bytes packetBody      // serialized fields at inputProtocolId (no packet id)
```

The proxy resolves `packetClass` / `packetType` against all Via packet enums on the classpath. `packetBody` is passed to `PacketWrapper.create(type, body, connection)`.

### Response (when `requestId != 0`)

| Status | Meaning | Trailing payload |
|--------|---------|------------------|
| `0` OK | sent / transformed | transform: raw `writeToBuffer` output |
| `1` CANCELLED | Via cancelled | — |
| `2` ERROR | failed | UTF-8 string |

## Build

```bash
./gradlew :viabridge-velocity:jar
```

Jar bundles `protocol`. Deploy to Velocity `plugins/`.

## Requirements

- Velocity + ViaVersion (+ ViaBackwards/ViaRewind as needed)
- Trusted backend only (standard Velocity backend plugin messages)

## Legacy count rewrite

A 1.8 client draws any stack count but 1, 0 included; the modern wire has no 0 (it is the empty slot). An item whose
custom data carries `viabridge:legacy_count` (a number) reaches a 1.8 client with that count, patched inside ViaRewind's
own slot and window-contents translation, so resends never flash. Needs ViaRewind; other clients see the modern count.
