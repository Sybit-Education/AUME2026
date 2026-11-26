import { useEffect, useId, useRef, useState, type FormEvent, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { dayPlaceholder, formatDay, parseDay } from './history'
import { POPOVER_WIDTH, type FilterSpec, type FilterValues, type SortDirection } from './tableState'

/*
 * Gemeinsame Bausteine der Kontroll-Tabellen (Anfragen, Anstehend, Historie): Sortieren per Klick auf die
 * Überschrift, Filter über das Trichter-Symbol, aktive Filter als Chips. Die Historie filtert im Backend, die
 * Tabellen auf der Seite Kontrollen im Browser; das Aussehen und Verhalten ist dasselbe.
 */

interface SortHeaderProps {
  label: string
  /** Richtung, wenn nach dieser Spalte sortiert wird, sonst null. */
  sorted: SortDirection | null
  onSort: () => void
  filterButton?: ReactNode
}

export function SortHeader({ label, sorted, onSort, filterButton }: SortHeaderProps) {
  const { t } = useTranslation()
  const arrow = !sorted ? '↕' : sorted === 'asc' ? '↑' : '↓'
  return (
    <th aria-sort={sorted ? (sorted === 'asc' ? 'ascending' : 'descending') : 'none'}>
      <span className="ins-th">
        <button type="button" className={`ins-th-sort${sorted ? ' on' : ''}`} onClick={onSort}
          title={t('inspections.history.sortBy', { column: label })}>
          {label} <span aria-hidden="true" className="ins-th-arrow">{arrow}</span>
        </button>
        {filterButton}
      </span>
    </th>
  )
}

interface FilterButtonProps {
  label: string
  active: boolean
  expanded: boolean
  onClick: (button: HTMLButtonElement) => void
}

export function FilterButton({ label, active, expanded, onClick }: FilterButtonProps) {
  const { t } = useTranslation()
  return (
    <button type="button" className={`ins-th-filter${active ? ' on' : ''}`} aria-haspopup="dialog" aria-expanded={expanded}
      aria-label={t(active ? 'inspections.history.filterActive' : 'inspections.history.filterBy', { column: label })}
      onClick={(event) => onClick(event.currentTarget)}>
      <svg viewBox="0 0 16 16" width="13" height="13" aria-hidden="true">
        <path d="M1.5 2h13l-5 6.2V13l-3 1.5V8.2z" fill={active ? 'currentColor' : 'none'} stroke="currentColor" strokeWidth="1.4" strokeLinejoin="round" />
      </svg>
    </button>
  )
}

interface FilterMenuProps {
  spec: FilterSpec
  left: number
  top: number
  onApply: (values: FilterValues) => void
  onClose: () => void
}

export function FilterMenu({ spec, left, top, onApply, onClose }: FilterMenuProps) {
  const { t } = useTranslation()
  const id = useId()
  const box = useRef<HTMLFormElement>(null)
  const [invalid, setInvalid] = useState<'from' | 'to' | null>(null)
  // Immer die aktuelle Funktion aufrufen, ohne Fokus und Listener bei jedem Rendern neu zu setzen.
  const close = useRef(onClose)
  useEffect(() => { close.current = onClose })

  useEffect(() => {
    box.current?.querySelector<HTMLElement>('input, .ins-pop-opt.on, .ins-pop-opt')?.focus()
    const onKey = (event: KeyboardEvent) => { if (event.key === 'Escape') close.current() }
    const onPointer = (event: PointerEvent) => {
      const target = event.target as Element | null
      // Klick auf einen Filter-Knopf schaltet selbst um; sonst schließt jeder Klick außerhalb.
      if (box.current && target && !box.current.contains(target) && !target.closest('.ins-th-filter')) close.current()
    }
    document.addEventListener('keydown', onKey)
    document.addEventListener('pointerdown', onPointer)
    return () => {
      document.removeEventListener('keydown', onKey)
      document.removeEventListener('pointerdown', onPointer)
    }
  }, [])

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const data = new FormData(event.currentTarget)
    const value = (name: string) => String(data.get(name) ?? '')
    if (spec.kind === 'dates') {
      const from = parseDay(value('from'))
      const to = parseDay(value('to'))
      const wrong = from === null ? 'from' : to === null ? 'to' : null
      setInvalid(wrong)
      if (wrong) box.current?.querySelector<HTMLInputElement>(`#${CSS.escape(`${id}-${wrong}`)}`)?.focus()
      else onApply({ from: from ?? '', to: to ?? '' })
    } else if (spec.kind === 'text') {
      onApply({ text: value('text') })
    }
  }

  return (
    <form ref={box} className="ins-pop" role="dialog" aria-label={t('inspections.history.filterBy', { column: spec.label })}
      style={{ left, top, width: POPOVER_WIDTH }} onSubmit={submit}>
      {spec.kind === 'dates' && (
        <>
          <DateField id={`${id}-from`} name="from" label={t('inspections.history.from')} initial={spec.from} invalid={invalid === 'from'} />
          <DateField id={`${id}-to`} name="to" label={t('inspections.history.to')} initial={spec.to} invalid={invalid === 'to'} />
          {invalid && <p className="err xs" role="alert" style={{ margin: '0 0 8px' }}>{t('inspections.history.invalidDate')}</p>}
        </>
      )}
      {spec.kind === 'text' && (
        <div className="fld">
          <label htmlFor={`${id}-text`}>{t('inspections.history.contains', { column: spec.label })}</label>
          <input id={`${id}-text`} name="text" maxLength={80} defaultValue={spec.value} />
        </div>
      )}
      {spec.kind === 'options' && (
        <OptionList label={spec.label} current={spec.value} options={spec.options} onPick={(value) => onApply({ value })} />
      )}
      {spec.kind !== 'options' && (
        <div className="acts" style={{ marginTop: 0 }}>
          <button className="btn sm" type="submit">{t('inspections.history.apply')}</button>
          <button className="btn sm gh" type="button" onClick={() => onApply(spec.kind === 'dates' ? { from: '', to: '' } : { text: '' })}>
            {t('inspections.history.clear')}
          </button>
        </div>
      )}
    </form>
  )
}

export interface ActiveFilter {
  key: string
  label: string
  clear: () => void
}

/** Aktive Filter als Chips über der Tabelle; ein Klick entfernt den Filter, „Zurücksetzen“ alle. */
export function ActiveFilters({ filters, onReset }: { filters: ActiveFilter[]; onReset: () => void }) {
  const { t } = useTranslation()
  if (filters.length === 0) return null
  return (
    <div className="ins-active" aria-label={t('inspections.history.activeFilters')}>
      {filters.map((entry) => (
        <button key={entry.key} type="button" className="chip inf ins-active-chip"
          aria-label={t('inspections.history.removeFilter', { filter: entry.label })} onClick={entry.clear}>
          {entry.label} <span aria-hidden="true">✕</span>
        </button>
      ))}
      <button type="button" className="btn sm gh" onClick={onReset}>{t('inspections.history.reset')}</button>
    </div>
  )
}

interface OptionListProps {
  label: string
  current: string
  options: [value: string, text: string][]
  onPick: (value: string) => void
}

/**
 * Auswahl im Filter-Menü als eigene Liste statt `<select>`: Die aufgeklappte Liste eines `<select>` zeichnet der
 * Browser selbst, sie lässt sich nicht an das App-Design anpassen. Ein Klick filtert sofort.
 */
function OptionList({ label, current, options, onPick }: OptionListProps) {
  return (
    <div className="ins-pop-opts" role="radiogroup" aria-label={label}>
      {options.map(([value, text]) => (
        <button key={value} type="button" role="radio" aria-checked={current === value}
          className={`ins-pop-opt${current === value ? ' on' : ''}`} onClick={() => onPick(value)}>
          {text}
        </button>
      ))}
    </div>
  )
}

interface DateFieldProps {
  id: string
  name: 'from' | 'to'
  label: string
  /** Kalendertag als JJJJ-MM-TT oder leer. */
  initial: string
  invalid: boolean
}

/**
 * Datumsfeld im Format Tag/Monat/Jahr, unabhängig von der Spracheinstellung des Browsers. Der Kalender-Knopf
 * öffnet die Datumsauswahl des Browsers und schreibt den gewählten Tag ins Textfeld.
 */
function DateField({ id, name, label, initial, invalid }: DateFieldProps) {
  const { t, i18n } = useTranslation()
  const language = i18n.resolvedLanguage
  const text = useRef<HTMLInputElement>(null)
  const picker = useRef<HTMLInputElement>(null)

  function openPicker() {
    const input = picker.current
    if (!input) return
    input.value = parseDay(text.current?.value ?? '') ?? ''
    try {
      input.showPicker()
    } catch {
      // Ältere Browser ohne showPicker: Eingabe bleibt über das Textfeld möglich.
      text.current?.focus()
    }
  }

  return (
    <div className="fld">
      <label htmlFor={id}>{label}</label>
      <div className="ins-date">
        <input ref={text} id={id} name={name} inputMode="numeric" autoComplete="off" maxLength={10}
          placeholder={dayPlaceholder(language)} defaultValue={formatDay(initial, language)}
          aria-invalid={invalid || undefined} aria-describedby={`${id}-hint`} />
        <span id={`${id}-hint`} className="ins-sr">{dayPlaceholder(language)}</span>
        <button type="button" className="ins-date-btn" onClick={openPicker}
          aria-label={t('inspections.history.pickDate', { field: label })}>
          <svg viewBox="0 0 16 16" width="14" height="14" aria-hidden="true">
            <rect x="2" y="3" width="12" height="11" rx="1.5" fill="none" stroke="currentColor" strokeWidth="1.4" />
            <path d="M2 6.5h12M5.5 1.5v3M10.5 1.5v3" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" />
          </svg>
        </button>
        <input ref={picker} type="date" className="ins-date-picker" tabIndex={-1} aria-hidden="true"
          onChange={(event) => {
            if (text.current && event.target.value) text.current.value = formatDay(event.target.value, language)
          }} />
      </div>
    </div>
  )
}
