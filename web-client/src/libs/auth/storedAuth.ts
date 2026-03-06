import { resolveSessionActor } from '../session/storedSession';

export type StoredAuth = {
  facilityId: string;
  userId: string;
};

export function readStoredAuth(): StoredAuth | null {
  const sessionActor = resolveSessionActor();
  if (!sessionActor) return null;
  const separator = sessionActor.actor.indexOf(':');
  if (separator <= 0) return null;
  return {
    facilityId: sessionActor.facilityId,
    userId: sessionActor.actor.slice(separator + 1),
  };
}

export function resolveAuditActor(): { actor: string; facilityId: string } {
  const sessionActor = resolveSessionActor();
  if (sessionActor) return sessionActor;

  const stored = readStoredAuth();
  if (!stored) return { actor: 'unknown', facilityId: 'unknown' };
  return { actor: `${stored.facilityId}:${stored.userId}`, facilityId: stored.facilityId };
}
