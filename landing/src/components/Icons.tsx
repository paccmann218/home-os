type P = { className?: string };
const base = { fill: "none", stroke: "currentColor", strokeWidth: 2, strokeLinecap: "round" as const, strokeLinejoin: "round" as const };

export const DownloadIcon = (p: P) => (
  <svg viewBox="0 0 24 24" aria-hidden="true" {...base} {...p}>
    <path d="M12 4v11m0 0-4.5-4.5M12 15l4.5-4.5M5 19.5h14" />
  </svg>
);

export const CubeIcon = (p: P) => (
  <svg viewBox="0 0 24 24" aria-hidden="true" {...base} {...p}>
    <path d="M12 3 20 7.5v9L12 21 4 16.5v-9L12 3Z" />
    <path d="M12 12 20 7.5M12 12v9M12 12 4 7.5" />
  </svg>
);

export const ArrowIcon = (p: P) => (
  <svg viewBox="0 0 24 24" aria-hidden="true" {...base} {...p}>
    <path d="M5 12h14m-5-5 5 5-5 5" />
  </svg>
);

export const CodeIcon = (p: P) => (
  <svg viewBox="0 0 24 24" aria-hidden="true" {...base} {...p}>
    <path d="m8.5 8-4 4 4 4m7-8 4 4-4 4m-2.5-10-2 12" />
  </svg>
);

export const CheckIcon = (p: P) => (
  <svg viewBox="0 0 24 24" aria-hidden="true" {...base} strokeWidth={3} {...p}>
    <path d="m5 12.5 4.5 4.5L19 7.5" />
  </svg>
);

export const SunIcon = (p: P) => (
  <svg viewBox="0 0 24 24" aria-hidden="true" {...base} {...p}>
    <circle cx="12" cy="12" r="4" />
    <path d="M12 2.5v2m0 15v2M4.6 4.6 6 6m12 12 1.4 1.4M2.5 12h2m15 0h2M4.6 19.4 6 18M18 6l1.4-1.4" />
  </svg>
);

export const MoonIcon = (p: P) => (
  <svg viewBox="0 0 24 24" aria-hidden="true" {...base} {...p}>
    <path d="M20 14.5A8 8 0 0 1 9.5 4a8 8 0 1 0 10.5 10.5Z" />
  </svg>
);

export const SystemIcon = (p: P) => (
  <svg viewBox="0 0 24 24" aria-hidden="true" {...base} {...p}>
    <circle cx="12" cy="12" r="8" />
    <path d="M12 4a8 8 0 0 1 0 16Z" fill="currentColor" stroke="none" />
  </svg>
);
