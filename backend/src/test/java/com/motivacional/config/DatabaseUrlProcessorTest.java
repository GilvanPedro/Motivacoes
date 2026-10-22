package com.motivacional.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.motivacional.config.DatabaseUrlProcessor.JdbcSettings;

class DatabaseUrlProcessorTest {

    private static final String HOST = "ep-cool-name-123456.us-east-2.aws.neon.tech";

    @Test
    void convertsNeonUrl() {
        JdbcSettings s = DatabaseUrlProcessor.parse(
                "postgresql://neondb_owner:npg_AbC123@" + HOST + "/neondb?sslmode=require&channel_binding=require");
        assertEquals("jdbc:postgresql://" + HOST + "/neondb?sslmode=require", s.url());
        assertEquals("neondb_owner", s.username());
        assertEquals("npg_AbC123", s.password());
    }

    @Test
    void acceptsPsqlCommandAndAddsSsl() {
        JdbcSettings s = DatabaseUrlProcessor.parse("psql 'postgres://user:p%40ss@" + HOST + ":5432/db'");
        assertEquals("jdbc:postgresql://" + HOST + ":5432/db?sslmode=require", s.url());
        assertEquals("user", s.username());
        assertEquals("p@ss", s.password());
    }

    @Test
    void keepsJdbcUrlAsIs() {
        String jdbc = "jdbc:postgresql://localhost/db?user=a&password=b";
        assertEquals(jdbc, DatabaseUrlProcessor.parse(jdbc).url());
    }
}
