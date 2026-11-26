import { useEffect, useId, useLayoutEffect, useRef, useState, type KeyboardEvent } from 'react'
import './select.css'

export interface SelectOption {
  value: string
  label: string
  disabled?: boolean
}

interface SelectProps {
  /** Für `<label htmlFor>`; die Beschriftung bezieht sich auf den Knopf. */
  id?: string
  /** Mit Namen wird der Wert in einem Formular mitgesendet (verstecktes Feld). */
  name?: string
  /** Gesteuert: aktueller Wert. Ohne `value` merkt sich die Auswahl den Wert selbst (`defaultValue`). */
  value?: string
  defaultValue?: string
  onChange?: (value: string) => void
  options: SelectOption[]
  /** Text, solange kein Eintrag gewählt ist. */
  placeholder?: string
  disabled?: boolean
  required?: boolean
  'aria-label'?: string
}

/**
 * Auswahlliste im App-Design statt `<select>`: Die aufgeklappte Liste eines `<select>` zeichnet der Browser selbst,
 * sie lässt sich nicht gestalten. Bedienung wie gewohnt: Klick oder Pfeiltasten öffnen, Pfeiltasten, Pos1/Ende wählen
 * vor, Enter übernimmt, Escape schließt.
 */
export default function Select({
  id, name, value, defaultValue, onChange, options, placeholder, disabled, required, 'aria-label': ariaLabel,
}: SelectProps) {
  const generated = useId()
  const buttonId = id ?? `${generated}-button`
  const listId = `${generated}-list`
  const [own, setOwn] = useState(defaultValue ?? '')
  const current = value ?? own
  const [open, setOpen] = useState(false)
  const [active, setActive] = useState(0)
  const [position, setPosition] = useState<{ left: number; top: number; width: number; maxHeight: number } | null>(null)
  const button = useRef<HTMLButtonElement>(null)
  const list = useRef<HTMLUListElement>(null)
  const selected = options.find((option) => option.value === current)
  const enabled = options.map((option, index) => (option.disabled ? -1 : index)).filter((index) => index >= 0)

  function choose(index: number) {
    const option = options[index]
    if (!option || option.disabled) return
    if (value === undefined) setOwn(option.value)
    if (option.value !== current) onChange?.(option.value)
    close()
  }

  function openList() {
    if (disabled) return
    const index = options.findIndex((option) => option.value === current)
    setActive(index >= 0 && !options[index].disabled ? index : (enabled[0] ?? 0))
    setOpen(true)
  }

  function close() {
    setOpen(false)
    button.current?.focus()
  }

  function move(step: number) {
    const at = enabled.indexOf(active)
    const next = enabled[Math.min(Math.max(at + step, 0), enabled.length - 1)]
    if (next !== undefined) setActive(next)
  }

  // Die Liste liegt fest über allem (position: fixed), damit sie in Dialogen und Tabellen nicht abgeschnitten wird.
  useLayoutEffect(() => {
    if (!open || !button.current) return
    const box = button.current.getBoundingClientRect()
    const below = window.innerHeight - box.bottom - 8
    const above = box.top - 8
    const maxHeight = Math.min(280, Math.max(below, above))
    const top = below >= Math.min(160, maxHeight) || below >= above ? box.bottom + 4 : box.top - 4 - maxHeight
    setPosition({ left: box.left, top, width: box.width, maxHeight })
  }, [open])

  useEffect(() => {
    if (!open) return
    list.current?.focus()
    const outside = (event: PointerEvent) => {
      const target = event.target as Node
      if (!list.current?.contains(target) && !button.current?.contains(target)) setOpen(false)
    }
    // Beim Scrollen der Seite würde die feste Liste vom Knopf wegrutschen, daher schließen.
    const scroll = (event: Event) => { if (!list.current?.contains(event.target as Node)) setOpen(false) }
    document.addEventListener('pointerdown', outside)
    window.addEventListener('scroll', scroll, true)
    window.addEventListener('resize', scroll)
    return () => {
      document.removeEventListener('pointerdown', outside)
      window.removeEventListener('scroll', scroll, true)
      window.removeEventListener('resize', scroll)
    }
  }, [open])

  useEffect(() => {
    if (open) list.current?.querySelector(`[data-index="${active}"]`)?.scrollIntoView({ block: 'nearest' })
  }, [open, active])

  function onButtonKey(event: KeyboardEvent<HTMLButtonElement>) {
    if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      event.preventDefault()
      openList()
    }
  }

  function onListKey(event: KeyboardEvent<HTMLUListElement>) {
    const keys: Record<string, () => void> = {
      ArrowDown: () => move(1),
      ArrowUp: () => move(-1),
      Home: () => setActive(enabled[0] ?? 0),
      End: () => setActive(enabled[enabled.length - 1] ?? 0),
      Enter: () => choose(active),
      ' ': () => choose(active),
      Escape: () => close(),
    }
    if (event.key === 'Tab') {
      setOpen(false)
      return
    }
    const handler = keys[event.key]
    if (handler) {
      event.preventDefault()
      event.stopPropagation()
      handler()
    }
  }

  return (
    <div className="sel">
      {name && <input type="hidden" name={name} value={current} />}
      <button ref={button} id={buttonId} type="button" className={`sel-btn${selected ? '' : ' sel-empty'}`}
        disabled={disabled} aria-haspopup="listbox" aria-expanded={open} aria-controls={open ? listId : undefined}
        aria-label={ariaLabel} aria-required={required || undefined}
        onClick={() => (open ? close() : openList())} onKeyDown={onButtonKey}>
        <span className="sel-text">{selected?.label ?? placeholder ?? ''}</span>
        <svg className="sel-arrow" viewBox="0 0 12 12" width="12" height="12" aria-hidden="true">
          <path d="M2.5 4.5 6 8l3.5-3.5" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
      </button>
      {open && position && (
        <ul ref={list} id={listId} role="listbox" tabIndex={-1} className="sel-pop"
          aria-labelledby={buttonId} aria-activedescendant={`${listId}-${active}`}
          style={{ left: position.left, top: position.top, width: position.width, maxHeight: position.maxHeight }}
          onKeyDown={onListKey}>
          {options.map((option, index) => (
            <li key={option.value} id={`${listId}-${index}`} data-index={index} role="option"
              aria-selected={option.value === current} aria-disabled={option.disabled || undefined}
              className={`sel-opt${index === active ? ' act' : ''}${option.value === current ? ' on' : ''}${option.disabled ? ' off' : ''}`}
              onPointerMove={() => { if (!option.disabled) setActive(index) }}
              onClick={() => choose(index)}>
              {option.label}
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
