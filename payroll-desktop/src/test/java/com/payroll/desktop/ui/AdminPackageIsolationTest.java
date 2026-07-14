package com.payroll.desktop.ui;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that AdminShell and all classes in the admin package contain zero
 * compile-time references to com.payroll.desktop.ui.superadmin.
 *
 * Implemented by scanning compiled .class file bytes for the string "superadmin".
 * Class-file constant pools store all type/method/field references as UTF-8 strings,
 * so any import or reference will be detectable as plain ASCII bytes.
 */
class AdminPackageIsolationTest {

    @Test
    void adminPackageAndAdminShellHaveNoSuperAdminReferences() throws IOException, URISyntaxException {
        URL selfUrl = AdminPackageIsolationTest.class.getProtectionDomain().getCodeSource().getLocation();
        Path testClassesDir = Path.of(selfUrl.toURI());
        Path classesDir = testClassesDir.getParent().resolve("classes");

        List<Path> classesToCheck = new ArrayList<>();

        Path adminDir = classesDir.resolve("com/payroll/desktop/ui/admin");
        if (Files.exists(adminDir)) {
            try (var walk = Files.walk(adminDir)) {
                walk.filter(p -> p.toString().endsWith(".class"))
                        .forEach(classesToCheck::add);
            }
        }

        Path adminShellClass = classesDir.resolve("com/payroll/desktop/ui/shell/AdminShell.class");
        if (Files.exists(adminShellClass)) {
            classesToCheck.add(adminShellClass);
        }

        assertThat(classesToCheck)
                .as("Expected to find compiled admin package classes — was mvn compile run?")
                .isNotEmpty();

        byte[] needle = "superadmin".getBytes(java.nio.charset.StandardCharsets.UTF_8);

        for (Path classFile : classesToCheck) {
            byte[] bytes = Files.readAllBytes(classFile);
            assertThat(containsSequence(bytes, needle))
                    .as("Class %s must not reference the superadmin package", classFile.getFileName())
                    .isFalse();
        }
    }

    private boolean containsSequence(byte[] haystack, byte[] needle) {
        outer:
        for (int i = 0; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) continue outer;
            }
            return true;
        }
        return false;
    }
}
