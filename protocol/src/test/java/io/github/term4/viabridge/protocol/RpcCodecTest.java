package io.github.term4.viabridge.protocol;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class RpcCodecTest {

    @Test
    void pingRoundTrip() {
        RpcFrame frame = new RpcFrame(RpcFrame.WIRE_VERSION, 42, RpcOpcodes.PING, new byte[]{1, 2, 3});
        RpcFrame decoded = RpcCodec.decode(RpcCodec.encode(frame));
        assertEquals(frame.protocolVersion(), decoded.protocolVersion());
        assertEquals(frame.requestId(), decoded.requestId());
        assertEquals(frame.opcode(), decoded.opcode());
        assertArrayEquals(frame.payload(), decoded.payload());
    }

    @Test
    void sendClientboundRoundTrip() {
        byte[] body = new byte[]{0x01, 0x02};
        byte[] payload = RpcCodec.encodeSendClientbound(772, "ClientboundPackets1_21_6", "SET_ENTITY_MOTION", body);
        RpcCodec.SendClientboundRequest req = RpcCodec.decodeSendClientbound(payload);
        assertEquals(772, req.inputProtocolId());
        assertEquals("ClientboundPackets1_21_6", req.packetClass());
        assertEquals("SET_ENTITY_MOTION", req.packetType());
        assertArrayEquals(body, req.packetBody());
    }
}
