package io.github.latticehub.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PoleSpringInstallerTest {
    @Test
    void parsesSupportedBootMajorVersions() {
        assertEquals(2, PoleSpringInstaller.bootMajor("2.7.18"));
        assertEquals(3, PoleSpringInstaller.bootMajor("3.5.16"));
        assertEquals(4, PoleSpringInstaller.bootMajor("4.1.0"));
    }

    @Test
    void rejectsMissingOrInvalidVersions() {
        assertThrows(IllegalStateException.class, () -> PoleSpringInstaller.bootMajor(null));
        assertThrows(IllegalStateException.class, () -> PoleSpringInstaller.bootMajor("development"));
    }
}
