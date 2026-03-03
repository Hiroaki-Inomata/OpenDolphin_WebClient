const MSW_SCRIPT_NAME = 'mockServiceWorker.js';

const hasMockWorkerScript = (registration: ServiceWorkerRegistration) =>
  [registration.active, registration.installing, registration.waiting].some((worker) =>
    worker?.scriptURL?.includes(MSW_SCRIPT_NAME),
  );

export async function unregisterMsw(): Promise<void> {
  if (typeof window === 'undefined' || typeof navigator === 'undefined' || !('serviceWorker' in navigator)) return;

  try {
    const registrations = await navigator.serviceWorker.getRegistrations();
    await Promise.all(
      registrations
        .filter((registration) => hasMockWorkerScript(registration))
        .map(async (registration) => {
          try {
            await registration.unregister();
          } catch {
            // ignore unregister failures
          }
        }),
    );
  } catch (error) {
    if (import.meta.env.DEV) {
      console.debug('[msw] unregister skipped', error);
    }
  }
}
