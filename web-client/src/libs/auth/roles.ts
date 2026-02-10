// Keep legacy/admin role spellings compatible across server seeds and Web client authz checks.
export const SYSTEM_ADMIN_ROLES = new Set([
  'system_admin',
  'admin',
  'system-admin',
  'system-administrator',
  'system_administrator',
]);

export const isSystemAdminRole = (role?: string) => {
  if (!role) return false;
  return SYSTEM_ADMIN_ROLES.has(role);
};
