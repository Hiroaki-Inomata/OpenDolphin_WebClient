type ReturnToBarProps = {
  scope: { facilityId: string | undefined; userId?: string };
  returnTo?: string | null;
  from?: string | null;
  fallbackUrl: string;
  showShortcuts?: boolean;
};

export function ReturnToBar(_: ReturnToBarProps) {
  return null;
}
