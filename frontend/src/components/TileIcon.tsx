import type { ReactNode } from 'react'

/**
 * Zweifarbige Icons für `.icon-tile` (24 × 24). Flächen übernehmen `fill` aus der CSS-Klasse
 * (var(--icon-fill)), reine Linien tragen `fill="none"`. Rein dekorativ, daher `aria-hidden`.
 */
export type TileIconName = 'kitchen' | 'bath' | 'floor' | 'stairs' | 'list' | 'settings' | 'flat'

const none = 'none'

const shapes: Record<TileIconName, ReactNode> = {
  kitchen: (
    <>
      <path d="M5 11h14v5.5a3.5 3.5 0 0 1-3.5 3.5h-7A3.5 3.5 0 0 1 5 16.5V11Z" />
      <path d="M3 11h18" fill={none} />
      <path d="M9 4.5c-.8.9-.8 1.9 0 2.8M12 4c-.8.9-.8 2.1 0 3.3M15 4.5c-.8.9-.8 1.9 0 2.8" fill={none} />
    </>
  ),
  bath: (
    <>
      <path d="M4 12h16v2.5a5 5 0 0 1-5 5H9a5 5 0 0 1-5-5V12Z" />
      <path d="M6.5 12V6.2a2.2 2.2 0 0 1 4.2-.9" fill={none} />
      <path d="M8 19.5 7 21M16 19.5l1 1.5" fill={none} />
    </>
  ),
  floor: (
    <>
      <path d="M4 15.5 12 11l8 4.5-8 4.5-8-4.5Z" />
      <path d="M12 3.5V11" fill={none} />
      <path d="M9.5 6.5h5" fill={none} />
    </>
  ),
  stairs: (
    <>
      <path d="M4 20V16h4.5v-4H13V8h4.5V4H20v16H4Z" />
    </>
  ),
  list: (
    <>
      <rect x="5" y="4" width="14" height="16.5" rx="3" />
      <path d="M8.5 9.5h7M8.5 13h7M8.5 16.5h4" fill={none} />
    </>
  ),
  flat: (
    <>
      <path d="M4.5 11 12 4.5l7.5 6.5v8a1.5 1.5 0 0 1-1.5 1.5H6A1.5 1.5 0 0 1 4.5 19v-8Z" />
      <path d="M10 20.5v-5h4v5" fill={none} />
    </>
  ),
  settings: (
    <>
      <path d="M10.3 3.6h3.4l.5 2.3 1.6.9 2.2-.8 1.7 2.9-1.7 1.6v1.9l1.7 1.6-1.7 2.9-2.2-.8-1.6.9-.5 2.3h-3.4l-.5-2.3-1.6-.9-2.2.8-1.7-2.9 1.7-1.6v-1.9L4.4 8.9l1.7-2.9 2.2.8 1.6-.9.5-2.3Z" />
      <circle className="tile-icon-hole" cx="12" cy="12" r="2.6" />
    </>
  ),
}

export default function TileIcon({ name, size = 22 }: { name: TileIconName; size?: number }) {
  return (
    <svg className="tile-icon" viewBox="0 0 24 24" width={size} height={size} strokeLinecap="round"
      strokeLinejoin="round" aria-hidden="true" focusable="false">
      {shapes[name]}
    </svg>
  )
}
