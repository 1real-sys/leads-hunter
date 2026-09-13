package dev.jlm.leadshunter.support;

import java.net.URI;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;

/** Somente no classpath de testes: nunca executa migrations/fixtures no catálogo da aplicação. */
public class IsolatedTestDatabaseInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    private static final Map<String, Database> DATABASES = new HashMap<>();

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        var environment = context.getEnvironment();
        String original = environment.getProperty("spring.datasource.url");
        if (original == null) return; // Contextos sem banco continuam sem banco.
        String server = serverUrl(original);
        String username = environment.getRequiredProperty("spring.datasource.username");
        String password = environment.getProperty("spring.datasource.password", "");
        Database database;
        synchronized (DATABASES) {
            database = DATABASES.computeIfAbsent(server + "|" + username,
                key -> create(server, username, password));
        }
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource(
            "isolatedTestDatabase", Map.of(
                "spring.datasource.url", database.url(),
                "spring.datasource.hikari.jdbc-url", database.url(),
                "spring.flyway.url", database.url(),
                "spring.flyway.user", username,
                "spring.flyway.password", password,
                "spring.flyway.clean-disabled", "true",
                "spring.jpa.hibernate.ddl-auto", "validate"
            )));
    }

    static String serverUrl(String original) {
        if (!original.startsWith("jdbc:mysql://")) {
            throw new IllegalArgumentException("Os testes integrados exigem uma conexão MySQL simples.");
        }
        URI uri = URI.create(original.substring(5));
        if (uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null) {
            throw new IllegalArgumentException("URL MySQL inválida para isolamento de testes.");
        }
        return "jdbc:mysql://" + uri.getRawAuthority() + "/"
            + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
    }

    private static Database create(String server, String username, String password) {
        String name = "lh_test_" + UUID.randomUUID().toString().replace("-", "");
        try (var connection = DriverManager.getConnection(server, username, password);
             var statement = connection.createStatement()) {
            // Identificador gerado internamente; não vem de configuração ou entrada externa.
            statement.executeUpdate("CREATE DATABASE `" + name + "` CHARACTER SET utf8mb4");
        } catch (SQLException exception) {
            throw new IllegalStateException("Não foi possível criar o banco temporário dos testes. "
                + "O usuário MySQL configurado precisa de permissão CREATE/DROP DATABASE. "
                + "A base da aplicação não será usada como fallback. SQLState=" + exception.getSQLState());
        }
        int query = server.indexOf('?');
        String url = query < 0 ? server + name : server.substring(0, query) + name + server.substring(query);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> remove(server, username, password, name),
            "cleanup-test-database"));
        return new Database(url);
    }

    private static void remove(String server, String username, String password, String name) {
        if (!name.matches("lh_test_[a-f0-9]{32}")) throw new IllegalStateException("Catálogo de teste inválido");
        try (var connection = DriverManager.getConnection(server, username, password);
             var statement = connection.createStatement()) {
            statement.executeUpdate("DROP DATABASE `" + name + "`");
        } catch (SQLException exception) {
            System.err.println("Não foi possível remover o catálogo temporário " + name
                + "; SQLState=" + exception.getSQLState());
        }
    }

    private record Database(String url) {}
}
