package com.omniorder.identity.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.ByteArrayResource;

class JwtServiceTest {

    @Test
    void generateAndValidateRoundtrip() throws Exception {
        JwtService jwtService = jwtServiceWithEphemeralKeyPair();
        UUID userId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

        String token = jwtService.generateToken("alice", userId, List.of("CUSTOMER"));
        String username = jwtService.validateAndGetUsername(token);
        List<String> roles = jwtService.getRoles(token);

        assertThat(token).contains(".");
        assertThat(username).isEqualTo("alice");
        assertThat(roles).containsExactly("CUSTOMER");
    }

    @Test
    void invalidToken_shouldThrow() throws Exception {
        JwtService jwtService = jwtServiceWithEphemeralKeyPair();
        assertThatThrownBy(() -> jwtService.validateAndGetUsername("invalid.token"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private JwtService jwtServiceWithEphemeralKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        String privateKey = toPem("PRIVATE KEY", keyPair.getPrivate().getEncoded());
        String publicKey = toPem("PUBLIC KEY", keyPair.getPublic().getEncoded());
        return new JwtService(
            resource(privateKey),
            resource(publicKey),
            "identity-service-test",
            60
        );
    }

    private ByteArrayResource resource(String value) {
        return new ByteArrayResource(value.getBytes(StandardCharsets.UTF_8));
    }

    private String toPem(String label, byte[] value) {
        String encoded = Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(value);
        return "-----BEGIN " + label + "-----\n" + encoded + "\n-----END " + label + "-----\n";
    }
}
