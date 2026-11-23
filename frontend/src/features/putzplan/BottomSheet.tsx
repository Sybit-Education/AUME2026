import { useId, useLayoutEffect, useRef, type KeyboardEvent, type PropsWithChildren } from 'react'
import { useTranslation } from 'react-i18next'

interface BottomSheetProps {
  title: string
  busy?: boolean
  onClose: () => void
}

const focusable = 'a[href], button:not([disabled]), input:not([type="hidden"]):not([disabled]), select:not([disabled])'

// On desktop, keep the sheet inside the phone frame.
function fitToFrame(backdrop: HTMLElement) {
  const frame = backdrop.closest<HTMLElement>('.st-wrap')
  const inFrame = frame !== null && !window.matchMedia?.('(max-width: 600px)').matches
  backdrop.classList.toggle('in-frame', inFrame)
  if (!inFrame) {
    backdrop.style.cssText = ''
    return
  }
  const box = frame.getBoundingClientRect()
  const headerBottom = document.querySelector('.hd')?.getBoundingClientRect().bottom ?? 0
  const top = Math.max(box.top, headerBottom)
  const bottom = Math.min(box.bottom, window.innerHeight)
  const radius = getComputedStyle(frame).borderTopLeftRadius
  const topRadius = top === box.top ? radius : '0'
  const bottomRadius = bottom === box.bottom ? radius : '0'
  Object.assign(backdrop.style, {
    top: `${top}px`,
    left: `${box.left}px`,
    width: `${box.width}px`,
    height: `${bottom - top}px`,
    borderRadius: `${topRadius} ${topRadius} ${bottomRadius} ${bottomRadius}`,
  })
}

/** Sheet that slides up from the bottom. */
export default function BottomSheet({ title, busy = false, onClose, children }: PropsWithChildren<BottomSheetProps>) {
  const { t } = useTranslation()
  const titleId = useId()
  const backdrop = useRef<HTMLDivElement>(null)
  const sheet = useRef<HTMLDivElement>(null)

  useLayoutEffect(() => {
    const previous = document.activeElement instanceof HTMLElement ? document.activeElement : null
    const overflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    // Measure after locking scroll because the scrollbar can shift the frame.
    const fit = () => { if (backdrop.current) fitToFrame(backdrop.current) }
    fit()
    window.addEventListener('resize', fit)
    // Focus the sheet, not an input, so the mobile keyboard stays closed.
    sheet.current?.focus({ preventScroll: true })
    return () => {
      window.removeEventListener('resize', fit)
      document.body.style.overflow = overflow
      previous?.focus({ preventScroll: true })
    }
  }, [])

  function close() {
    if (!busy) onClose()
  }

  function onKeyDown(event: KeyboardEvent<HTMLDivElement>) {
    if (event.key === 'Escape') {
      event.stopPropagation()
      close()
    }
    if (event.key !== 'Tab' || !sheet.current) return
    const items = [...sheet.current.querySelectorAll<HTMLElement>(focusable)]
    if (items.length === 0) return
    const first = items[0]
    const last = items[items.length - 1]
    const active = document.activeElement
    if (event.shiftKey && (active === first || active === sheet.current)) {
      event.preventDefault()
      last.focus()
    } else if (!event.shiftKey && active === last) {
      event.preventDefault()
      first.focus()
    }
  }

  return (
    <div ref={backdrop} className="plan-sheet-bg" onClick={(event) => { if (event.target === event.currentTarget) close() }}>
      <div ref={sheet} className="plan-sheet" role="dialog" aria-modal="true" aria-labelledby={titleId}
        tabIndex={-1} onKeyDown={onKeyDown}>
        <div className="plan-sheet-grip" aria-hidden="true" />
        <div className="plan-sheet-head">
          <h3 id={titleId}>{title}</h3>
          <button className="btn sm gh" type="button" disabled={busy} aria-label={t('plan.closeSheet')} onClick={close}>×</button>
        </div>
        {children}
      </div>
    </div>
  )
}
