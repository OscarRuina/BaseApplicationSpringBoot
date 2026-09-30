-- Roles are a schema invariant, not seed data.
--
-- RoleService.findRoleByType throws InvalidRoleException (HTTP 400) when the row is missing,
-- and the only code that created roles was the @Profile("dev") seeder. A production database
-- therefore starts with an empty roles table and every registration fails.
--
-- INSERT IGNORE is load-bearing: an existing database is baselined at version 1 and skips V1,
-- but V2 still runs on it. When that database already has the roles (a dev database seeded by
-- the seeder), the unique constraint on name_role would abort the migration.
INSERT IGNORE INTO roles (name_role, create_at_role, update_at_role) VALUES
    ('USER', now(6), now(6)),
    ('ADMIN', now(6), now(6));
