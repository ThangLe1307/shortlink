package com.shortlink.linkapi.component;

import java.security.SecureRandom;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CodeGenerator {

  private static final char[] ALPHABET =
      "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();
  private static final int LENGTH = 7;

  private final SecureRandom rng = new SecureRandom();

  public String next() {
    char[] out = new char[LENGTH];
    for (int i = 0; i < LENGTH; i++) {
      out[i] = ALPHABET[rng.nextInt(ALPHABET.length)];
    }
    return new String(out);
  }
}
