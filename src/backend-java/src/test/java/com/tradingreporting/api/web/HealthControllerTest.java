package com.tradingreporting.api.web;

import com.tradingreporting.api.exception.ServiceUnavailableException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.springframework.test.util.ReflectionTestUtils;

class HealthControllerTest {

    @Test
    void healthAndLiveReturnOkWithoutDatabaseAccess() {
        HealthController controller = new HealthController();

        assertThat(controller.health()).containsExactlyEntriesOf(java.util.Map.of("status", "ok"));
        assertThat(controller.live()).containsExactlyEntriesOf(java.util.Map.of("status", "ok"));
    }

    @Test
    void readyReturnsOkWhenDatabaseAndMigrationRevisionAreAvailable() {
        EntityManager entityManager = mock(EntityManager.class);
        Query healthQuery = mock(Query.class);
        Query revisionQuery = mock(Query.class);
        when(entityManager.createNativeQuery("SELECT 1")).thenReturn(healthQuery);
        when(healthQuery.getSingleResult()).thenReturn(1);
        when(entityManager.createNativeQuery("SELECT version_num FROM alembic_version")).thenReturn(revisionQuery);
        when(revisionQuery.getResultList()).thenReturn(List.of("0002"));
        HealthController controller = controllerWith(entityManager);

        assertThat(controller.ready()).containsExactlyEntriesOf(java.util.Map.of("status", "ok"));
    }

    @Test
    void readyReportsUnavailableWhenDatabaseQueryFails() {
        EntityManager entityManager = mock(EntityManager.class);
        when(entityManager.createNativeQuery("SELECT 1")).thenThrow(new IllegalStateException("database offline"));
        HealthController controller = controllerWith(entityManager);

        assertThatThrownBy(controller::ready)
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessage("Database is not ready.");
    }

    @Test
    void readyReportsUnavailableWhenNoMigrationRevisionExists() {
        EntityManager entityManager = mock(EntityManager.class);
        Query healthQuery = mock(Query.class);
        Query revisionQuery = mock(Query.class);
        when(entityManager.createNativeQuery("SELECT 1")).thenReturn(healthQuery);
        when(healthQuery.getSingleResult()).thenReturn(1);
        when(entityManager.createNativeQuery("SELECT version_num FROM alembic_version")).thenReturn(revisionQuery);
        when(revisionQuery.getResultList()).thenReturn(List.of());
        HealthController controller = controllerWith(entityManager);

        assertThatThrownBy(controller::ready)
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessage("Database migrations are not ready.");
    }

    private static HealthController controllerWith(EntityManager entityManager) {
        HealthController controller = new HealthController();
        ReflectionTestUtils.setField(controller, "entityManager", entityManager);
        return controller;
    }
}
