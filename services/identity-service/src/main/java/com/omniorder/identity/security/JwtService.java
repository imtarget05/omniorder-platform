package com.omniorder.identity.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private final RSAPrivateKey privateKey;
    private final RSAPublicKey publicKey;
    private final String issuer;
    private final long expirationSeconds;

    public JwtService(@Value("${jwt.private-key-pem}") Resource privateKeyResource,
                      @Value("${jwt.public-key-pem}") Resource publicKeyResource,
                      @Value("${jwt.issuer}") String issuer,
                      @Value("${jwt.expiration-seconds}") long expirationSeconds) throws Exception {
        this.privateKey = loadPrivateKey(readBytes(privateKeyResource));
        this.publicKey = loadPublicKey(readBytes(publicKeyResource));
        this.issuer = issuer;
        this.expirationSeconds = expirationSeconds;
    }

    private byte[] readBytes(Resource resource) throws Exception {
        return resource.getInputStream().readAllBytes();
    }

    private RSAPrivateKey loadPrivateKey(byte[] pemBytes) throws Exception {
        String pem = new String(pemBytes);
        String derB64 = pem.replaceAll("-----BEGIN (.*)-----", "")
                .replaceAll("-----END (.*)-----", "")
                .replaceAll("\\s", "");
        byte[] der = Base64.getDecoder().decode(derB64);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(der);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        return (RSAPrivateKey) kf.generatePrivate(spec);
    }

    private RSAPublicKey loadPublicKey(byte[] pemBytes) throws Exception {
        String pem = new String(pemBytes);
        String derB64 = pem.replaceAll("-----BEGIN (.*)-----", "")
                .replaceAll("-----END (.*)-----", "")
                .replaceAll("\\s", "");
        byte[] der = Base64.getDecoder().decode(derB64);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(der);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        return (RSAPublicKey) kf.generatePublic(spec);
    }

    public String generateToken(String username, UUID userId, List<String> roles) {
        try {
            JWTClaimsSet.Builder claimsBuilder = new JWTClaimsSet.Builder()
                    .subject(username)
                    .claim("userId", userId.toString())
                    .issuer(issuer)
                    .issueTime(Date.from(Instant.now()))
                    .expirationTime(Date.from(Instant.now().plusSeconds(expirationSeconds)))
                    .claim("roles", roles);
            JWTClaimsSet claims = claimsBuilder.build();
            SignedJWT signedJWT = new SignedJWT(
                    new JWSHeader(JWSAlgorithm.RS256), claims);
            JWSSigner signer = new RSASSASigner(privateKey);
            signedJWT.sign(signer);
            return signedJWT.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException("Failed to sign JWT", e);
        }
    }

    public String validateAndGetUsername(String token) {
        try {
            SignedJWT signedJWT = SignedJWT.parse(token);
            JWSVerifier verifier = new RSASSAVerifier(publicKey);
            if (!signedJWT.verify(verifier)) {
                throw new IllegalArgumentException("Invalid JWT signature");
            }
            JWTClaimsSet claims = signedJWT.getJWTClaimsSet();
            Date exp = claims.getExpirationTime();
            if (exp != null && exp.before(Date.from(Instant.now()))) {
                throw new IllegalArgumentException("JWT expired");
            }
            if (!issuer.equals(claims.getIssuer())) {
                throw new IllegalArgumentException("Invalid JWT issuer");
            }
            return claims.getSubject();
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid JWT token", e);
        }
    }

    public List<String> getRoles(String token) {
        try {
            SignedJWT signedJWT = SignedJWT.parse(token);
            JWTClaimsSet claims = signedJWT.getJWTClaimsSet();
            Object roles = claims.getClaim("roles");
            if (roles instanceof List) {
                return ((List<?>) roles).stream().map(Object::toString).collect(Collectors.toList());
            }
            return List.of();
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid JWT token", e);
        }
    }
}
