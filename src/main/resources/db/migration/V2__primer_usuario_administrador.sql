-- A partir de esta versión el primer usuario registrado es el administrador del
-- servidor (único que ve y usa el panel B2B). En instalaciones anteriores todos los
-- usuarios eran ROLE_USER: se promueve al más antiguo si todavía no hay ningún admin.
UPDATE users
SET role = 'ROLE_ADMIN'
WHERE id = (SELECT id FROM users ORDER BY created_at FETCH FIRST 1 ROWS ONLY)
  AND NOT EXISTS (SELECT 1 FROM users WHERE role = 'ROLE_ADMIN');
