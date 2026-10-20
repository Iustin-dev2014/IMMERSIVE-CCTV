package ro.immersivecctv;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/** Small salted SHA-256 helper used for locally stored CCTV device credentials. */
public final class SecurityHashes {
    private SecurityHashes() {}

    public static String hash(String salt, String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((Objects.requireNonNullElse(salt, "") + ":"
                    + Objects.requireNonNullElse(password, "")).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime", impossible);
        }
    }

    public static boolean matches(String salt, String password, String expectedHash) {
        if (expectedHash == null || expectedHash.isEmpty()) return false;
        byte[] actual = hash(salt, password).getBytes(StandardCharsets.US_ASCII);
        byte[] expected = expectedHash.getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(actual, expected);
    }
}
