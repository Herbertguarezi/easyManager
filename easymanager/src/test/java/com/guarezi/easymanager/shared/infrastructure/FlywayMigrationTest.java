package com.guarezi.easymanager.shared.infrastructure;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class FlywayMigrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void appliesV1MigrationSuccessfully() {
        Integer appliedCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '1' AND success = true",
                Integer.class);

        assertThat(appliedCount).isEqualTo(1);
    }

    @Test
    void productsTableHasExpectedColumns() throws Exception {
        Set<String> columnNames = new HashSet<>();

        try (var connection = jdbcTemplate.getDataSource().getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            try (ResultSet columns = metaData.getColumns(null, null, "products", null)) {
                while (columns.next()) {
                    columnNames.add(columns.getString("COLUMN_NAME").toLowerCase());
                }
            }
        }

        assertThat(columnNames).containsExactlyInAnyOrder("id", "name", "amount", "photo_url", "barcode");
    }
}
