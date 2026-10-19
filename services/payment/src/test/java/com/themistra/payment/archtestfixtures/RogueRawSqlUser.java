package com.themistra.payment.archtestfixtures;

import jakarta.persistence.EntityManager;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 * T28 (frozen brief D5, Phase 3 F1/F5). A deliberate violation of
 * {@code ArchitectureTest.shouldConfineRawSqlAndEntityManagerWritesToTheExportRead}. Each method
 * reaches the database outside the repository layer, in one of the shapes the rule bans: a
 * {@code JdbcTemplate} write, a JDBC {@code Statement} execute inside a lambda, an
 * {@code EntityManager.remove} method reference, a native query outside
 * {@code TransactionExportRepository}, and a JPQL update. Never referenced by production code.
 */
public class RogueRawSqlUser {

    public void jdbcTemplateUpdate(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.update("DELETE FROM payments.ledger_entries");
    }

    public void statementInsideALambda(List<Statement> statements) {
        statements.forEach(statement -> {
            try {
                statement.executeUpdate("UPDATE payments.receipts SET signature = 'x'");
            } catch (SQLException e) {
                throw new IllegalStateException(e);
            }
        });
    }

    public void removeByMethodReference(EntityManager entityManager, List<Object> entities) {
        entities.forEach(entityManager::remove);
    }

    public void nativeQueryOutsideTheExport(EntityManager entityManager) {
        entityManager.createNativeQuery("DELETE FROM payments.ledger_entries").executeUpdate();
    }

    public void jpqlUpdate(EntityManager entityManager) {
        entityManager.createQuery("UPDATE LedgerEntry e SET e.prevHash = 'x'").executeUpdate();
    }
}
