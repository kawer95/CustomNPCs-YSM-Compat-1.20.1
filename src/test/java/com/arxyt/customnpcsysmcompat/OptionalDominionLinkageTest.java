package com.arxyt.customnpcsysmcompat;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;

class OptionalDominionLinkageTest {
    @Test
    void mandatoryEntrypointDoesNotLinkDominionApi() throws IOException {
        String resource = "/" + CustomNpcsYsmCompat.class.getName().replace('.', '/') + ".class";
        byte[] bytecode;
        try (var input = CustomNpcsYsmCompat.class.getResourceAsStream(resource)) {
            if (input == null) throw new IOException("Missing class resource " + resource);
            bytecode = input.readAllBytes();
        }
        String constantPool = new String(bytecode, StandardCharsets.ISO_8859_1);
        assertFalse(constantPool.contains("com/arxyt/dominionsword"));
    }
}
