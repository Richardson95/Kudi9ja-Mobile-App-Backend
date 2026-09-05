package com.quadrilateral.kudi9ja.support;

import jakarta.persistence.Entity;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.dialect.PostgreSQLDialect;
import org.hibernate.tool.schema.spi.SchemaManagementToolCoordinator;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

/**
 * Writes the PostgreSQL DDL the entities imply, for a human to turn into a
 * migration.
 *
 * <p>Not a test and not part of the build. It exists because a schema typed by
 * hand from twenty entities drifts from them silently, and a mapping that no
 * longer matches its table is the kind of defect that surfaces as a wrong
 * balance rather than as an error.
 *
 * <p>The output is a <b>starting point</b>, never the migration itself. What
 * Hibernate generates has no meaningful ordering, no comments, and none of the
 * partial indexes or constraints a money schema wants — those are added by
 * hand, and Flyway owns the file from then on.
 *
 * <p>Run it directly: {@code SchemaExporter.main}.
 */
public final class SchemaExporter {

    private static final String ENTITY_PACKAGE = "com.quadrilateral.kudi9ja";

    private SchemaExporter() {
    }

    public static void main(String[] args) {
        String target = args.length > 0 ? args[0] : "target/schema-postgres.sql";
        new File(target).delete();

        Map<String, Object> settings = new HashMap<>();
        settings.put(AvailableSettings.DIALECT, PostgreSQLDialect.class.getName());
        // No database is contacted. The dialect above is the only source of
        // truth for what the DDL should look like, which is the point: the
        // target is Postgres whatever happens to be running locally.
        settings.put("hibernate.boot.allow_jdbc_metadata_access", "false");
        settings.put(AvailableSettings.HBM2DDL_CHARSET_NAME, "UTF-8");
        settings.put(AvailableSettings.FORMAT_SQL, "true");
        settings.put("jakarta.persistence.schema-generation.scripts.action", "create");
        settings.put("jakarta.persistence.schema-generation.scripts.create-target", target);

        StandardServiceRegistryBuilder builder = new StandardServiceRegistryBuilder();
        settings.forEach(builder::applySetting);
        StandardServiceRegistry registry = builder.build();

        MetadataSources sources = new MetadataSources(registry);
        entityClasses().forEach(sources::addAnnotatedClass);
        Metadata metadata = sources.buildMetadata();

        SchemaManagementToolCoordinator.process(
                metadata, registry, settings, action -> {
                    // Nothing is dropped on shutdown: this writes a file and
                    // touches no database at all.
                });

        System.out.println("Wrote " + target);
    }

    private static Set<Class<?>> entityClasses() {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));

        return scanner.findCandidateComponents(ENTITY_PACKAGE).stream()
                .map(definition -> {
                    try {
                        return Class.forName(definition.getBeanClassName());
                    } catch (ClassNotFoundException e) {
                        throw new IllegalStateException(e);
                    }
                })
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
}
