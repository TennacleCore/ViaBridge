package io.github.term4.viabridge.protocol;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

public final class RpcCodec {

    private RpcCodec() {}

    public static byte[] encode(RpcFrame frame) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            out.write(frame.protocolVersion() & 0xFF);
            writeVarInt(out, frame.requestId());
            writeVarInt(out, frame.opcode());
            out.write(frame.payload());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    public static RpcFrame decode(byte[] bytes) {
        ByteArrayInputStream in = new ByteArrayInputStream(bytes);
        try {
            int version = in.read();
            if (version < 0) throw new IllegalArgumentException("empty frame");
            int requestId = readVarInt(in);
            int opcode = readVarInt(in);
            byte[] payload = in.readAllBytes();
            return new RpcFrame(version, requestId, opcode, payload);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** {@link RpcOpcodes#SEND_CLIENTBOUND} request body. */
    public static byte[] encodeSendClientbound(int inputProtocolId, String packetClass, String packetType, byte[] packetBody) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            writeVarInt(out, inputProtocolId);
            writeString(out, packetClass);
            writeString(out, packetType);
            out.write(packetBody);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    public record SendClientboundRequest(int inputProtocolId, String packetClass, String packetType, byte[] packetBody) {}

    public static SendClientboundRequest decodeSendClientbound(byte[] payload) {
        ByteArrayInputStream in = new ByteArrayInputStream(payload);
        try {
            int protocolId = readVarInt(in);
            String packetClass = readString(in);
            String packetType = readString(in);
            byte[] body = in.readAllBytes();
            return new SendClientboundRequest(protocolId, packetClass, packetType, body);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static byte[] encodeStatusResponse(byte status) {
        return encodeStatusResponse(status, null);
    }

    public static byte[] encodeStatusResponse(byte status, String message) {
        return encodeStatusResponse(status, message, null);
    }

    public static byte[] encodeStatusResponse(byte status, String message, byte[] body) {
        if (status == RpcStatus.ERROR) {
            if (message == null || message.isEmpty()) return new byte[]{status};
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try {
                out.write(status);
                writeString(out, message);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return out.toByteArray();
        }
        if (body != null && body.length > 0) {
            ByteArrayOutputStream out = new ByteArrayOutputStream(body.length + 1);
            try {
                out.write(status);
                out.write(body);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return out.toByteArray();
        }
        return new byte[]{status};
    }

    public record StatusResponse(byte status, String message, byte[] body) {}

    public static StatusResponse decodeStatusResponse(byte[] payload) {
        if (payload.length == 0) throw new IllegalArgumentException("empty status payload");
        byte status = payload[0];
        if (payload.length == 1) return new StatusResponse(status, null, null);
        if (status == RpcStatus.ERROR) {
            ByteArrayInputStream in = new ByteArrayInputStream(payload, 1, payload.length - 1);
            try {
                return new StatusResponse(status, readString(in), null);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return new StatusResponse(status, null, java.util.Arrays.copyOfRange(payload, 1, payload.length));
    }

    /** @deprecated use {@link #decodeStatusResponse} */
    @Deprecated
    public static byte readStatus(byte[] payload) {
        return decodeStatusResponse(payload).status();
    }

    private static void writeString(ByteArrayOutputStream out, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        writeVarInt(out, bytes.length);
        out.write(bytes);
    }

    private static String readString(ByteArrayInputStream in) throws IOException {
        int length = readVarInt(in);
        byte[] bytes = in.readNBytes(length);
        if (bytes.length != length) throw new IllegalArgumentException("truncated string");
        return new String(bytes, StandardCharsets.UTF_8);
    }

    static void writeVarInt(ByteArrayOutputStream out, int value) throws IOException {
        while ((value & ~0x7F) != 0) {
            out.write((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.write(value);
    }

    static int readVarInt(ByteArrayInputStream in) throws IOException {
        int value = 0;
        int size = 0;
        int b;
        while ((b = in.read()) >= 0) {
            value |= (b & 0x7F) << (size * 7);
            size++;
            if (size > 5) throw new IllegalArgumentException("varint too long");
            if ((b & 0x80) == 0) return value;
        }
        throw new IllegalArgumentException("truncated varint");
    }
}
