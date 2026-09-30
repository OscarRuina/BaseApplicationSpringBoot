-- Activation state.
--
-- `active_user` stays the only login gate, so JwtFilter, UserPrincipal, AuthService and
-- UserDetailsServiceImpl keep working untouched. `activated_at_user` is what separates the
-- two unrelated reasons a user can be inactive: a registration nobody confirmed yet
-- (activated_at IS NULL) and an account an administrator switched off (activated_at IS NOT
-- NULL). Without it the API cannot tell "awaiting email confirmation" from "suspended".
--
-- The backfill is deliberately unconditional. Every row that exists today predates public
-- registration, so every one of them is exactly as confirmed as it is ever going to be,
-- including the deactivated ones: they logged in and were then switched off, which is not the
-- same as never having confirmed. Leaving them NULL would relabel a suspended account as
-- pending, and the API would repeat that lie.
--
-- coalesce() covers hand-inserted rows whose create_at_user is NULL.
ALTER TABLE users ADD COLUMN activation_token_user varchar(64) NULL;
ALTER TABLE users ADD COLUMN activation_expires_at_user datetime(6) NULL;
ALTER TABLE users ADD COLUMN activated_at_user datetime(6) NULL;

-- 64 hex chars is the SHA-256 of the 32 random bytes mailed to the user. The token itself is
-- never stored, so a database dump cannot be replayed against POST /users/activate. The
-- unique constraint is safe because every row is NULL here (MySQL allows many NULLs in a
-- unique index) and it is what makes findByActivationToken return at most one row.
ALTER TABLE users ADD CONSTRAINT UK_users_activation_token UNIQUE (activation_token_user);

UPDATE users SET activated_at_user = coalesce(create_at_user, now(6));
