package dev.invisiblespiders.haven.core.service;

import dev.invisiblespiders.haven.api.diagnostic.DiagnosticResult;
import dev.invisiblespiders.haven.api.diagnostic.DiagnosticSeverity;
import dev.invisiblespiders.haven.api.diagnostic.HavenDiagnosticCheck;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DiagnosticServiceImplTest {

    @Test
    void runsRegisteredChecksInStableOrder() {
        DiagnosticServiceImpl service = new DiagnosticServiceImpl();

        service.register("haventeleport", check("zeta", DiagnosticResult.pass("haventeleport.zeta", "ready")));
        service.register("havenclaims", check("alpha", DiagnosticResult.warn("havenclaims.alpha", "optional")));

        List<DiagnosticResult> results = service.runAll();

        assertEquals(List.of("havenclaims.alpha", "haventeleport.zeta"), results.stream()
            .map(DiagnosticResult::id)
            .toList());
    }

    @Test
    void unregisterAllRemovesOwnerChecksOnly() {
        DiagnosticServiceImpl service = new DiagnosticServiceImpl();
        service.register("haventeleport", check("database", DiagnosticResult.pass("haventeleport.database", "ready")));
        service.register("havenclaims", check("database", DiagnosticResult.pass("havenclaims.database", "ready")));

        service.unregisterAll("haventeleport");

        assertEquals(List.of("havenclaims.database"), service.runAll().stream()
            .map(DiagnosticResult::id)
            .toList());
    }

    @Test
    void failedCheckReturnsFailureDiagnostic() {
        DiagnosticServiceImpl service = new DiagnosticServiceImpl();
        service.register("haventeleport", new HavenDiagnosticCheck() {
            @Override
            public String id() {
                return "database";
            }

            @Override
            public DiagnosticResult run() {
                throw new IllegalStateException("pool closed");
            }
        });

        DiagnosticResult result = service.runAll().get(0);

        assertEquals(DiagnosticSeverity.FAIL, result.severity());
        assertEquals("haventeleport.database", result.id());
        assertEquals("Diagnostic check failed: pool closed", result.message());
    }

    @Test
    void rejectsBlankOwnerAndCheckIds() {
        DiagnosticServiceImpl service = new DiagnosticServiceImpl();

        assertThrows(IllegalArgumentException.class, () -> service.register("", check("database",
            DiagnosticResult.pass("database", "ready"))));
        assertThrows(IllegalArgumentException.class, () -> service.register("haventeleport", check("",
            DiagnosticResult.pass("database", "ready"))));
    }

    private static HavenDiagnosticCheck check(String id, DiagnosticResult result) {
        return new HavenDiagnosticCheck() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public DiagnosticResult run() {
                return result;
            }
        };
    }
}
