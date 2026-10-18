/** HomeOS mark: a house whose floor line is a heartbeat. */
export function LogoMark({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 32 32" className={className} aria-hidden="true">
      <rect width="32" height="32" rx="9" fill="var(--accent)" />
      <path d="M6.5 14.5 16 6.5l9.5 8V26h-19z" fill="none" stroke="var(--on-accent)" strokeWidth="2.2" strokeLinejoin="round" />
      <path
        d="M9 19.5h3.2l1.8-4.5 2.6 8 1.9-5.5 1.3 2h3.2"
        fill="none"
        stroke="var(--on-accent)"
        strokeWidth="2"
        strokeLinecap="round"
        strokeLinejoin="round"
        opacity="0.85"
      />
    </svg>
  );
}
