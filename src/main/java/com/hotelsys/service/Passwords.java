package com.hotelsys.service;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public final class Passwords {
    private Passwords() { }
    public static String hash(char[] password) {
        HotelStore.check(password != null && password.length >= 12 && password.length <= 128, "Le mot de passe doit contenir 12 à 128 caractères");
        byte[] salt = new byte[16]; new SecureRandom().nextBytes(salt);
        return "pbkdf2-sha256$600000$" + Base64.getEncoder().encodeToString(salt) + "$" + Base64.getEncoder().encodeToString(derive(password, salt, 600_000));
    }
    public static boolean verify(char[] password, String encoded) {
        if (password == null || password.length > 128) { return false; }
        try {
            String[] parts = encoded.split("\\$");
            if (parts.length != 4 || !parts[0].equals("pbkdf2-sha256")) { return false; }
            int iterations = Integer.parseInt(parts[1]);
            if (iterations < 600_000 || iterations > 2_000_000) { return false; }
            byte[] salt = Base64.getDecoder().decode(parts[2]); byte[] expected = Base64.getDecoder().decode(parts[3]);
            if (salt.length != 16 || expected.length != 32) { return false; }
            byte[] actual = derive(password, salt, iterations);
            try { return MessageDigest.isEqual(expected, actual); } finally { Arrays.fill(actual, (byte) 0); }
        } catch (IllegalArgumentException invalid) { return false; }
    }
    private static byte[] derive(char[] password, byte[] salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, 256);
        try { return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded(); }
        catch (GeneralSecurityException failure) { throw new IllegalStateException(failure); }
        finally { spec.clearPassword(); }
    }
}
