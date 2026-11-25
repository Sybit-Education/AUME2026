import type { ReactNode } from 'react'

/**
 * Icons für die Tab-Leiste der Studierenden-App (24 × 24). Farben setzt App.css: inaktiv nur Linie,
 * aktiv zweifarbig (Fläche var(--icon-fill), Linie var(--primary-dark)). Reine Linien tragen fill="none".
 */
export type NavIconName = 'home' | 'plan' | 'inspection' | 'more'

const paths: Record<NavIconName, ReactNode> = {
  home: (
    <>
      <path d="M3.5 10.5 12 3.8l8.5 6.7" />
      <path d="M5.8 9v9.4c0 .9.7 1.6 1.6 1.6h3.1v-5.2c0-.6.4-1 1-1h1c.6 0 1 .4 1 1V20h3.1c.9 0 1.6-.7 1.6-1.6V9" />
    </>
  ),
  plan: (
    <>
      <rect x="3.8" y="5" width="16.4" height="15.2" rx="3" />
      <path d="M8 3.2v3.4M16 3.2v3.4M3.8 9.6h16.4" fill="none" />
      <path d="m8.8 14.6 2.2 2.1 4.2-4.3" fill="none" />
    </>
  ),
  inspection: (
    <>
      <path d="M8.6 4.6H7.2c-1.3 0-2.4 1.1-2.4 2.4v11.6c0 1.3 1.1 2.4 2.4 2.4h9.6c1.3 0 2.4-1.1 2.4-2.4V7c0-1.3-1.1-2.4-2.4-2.4h-1.4" />
      <rect x="8.6" y="2.8" width="6.8" height="3.6" rx="1.4" />
      <path d="m8.8 13.4 2.2 2.1 4.2-4.3" fill="none" />
    </>
  ),
  more: (
    <>
      <rect x="4" y="4" width="6.6" height="6.6" rx="2" />
      <rect x="13.4" y="4" width="6.6" height="6.6" rx="2" />
      <rect x="4" y="13.4" width="6.6" height="6.6" rx="2" />
      <rect x="13.4" y="13.4" width="6.6" height="6.6" rx="2" />
    </>
  ),
}

export default function NavIcon({ name }: { name: NavIconName }) {
  return (
    <svg className="nav-icon" viewBox="0 0 24 24" width="24" height="24" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" focusable="false">
      {paths[name]}
    </svg>
  )
}
