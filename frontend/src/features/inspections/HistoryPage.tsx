import { useEffect, useRef, useState, type FormEvent, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useLoaderData, useSearchParams } from 'react-router'
import LoadingPage from '@/components/LoadingPage'
import type { HistoryPage as HistoryData } from './api'
import {
  formatDate, hasFilter, toggleSort, withFilter, type HistoryFilter, type SortColumn,
} from './history'
import InspectionTabs from './InspectionTabs'

export interface HistoryLoaderData {
  filter: HistoryFilter
  /** `null`, wenn der Zeitraum ungültig ist und deshalb nichts geladen wurde. */
  data: HistoryData | null
}

type FilterColumn = 'date' | 'flat' | 'kind' | 'result'

interface OpenFilter {
  column: FilterColumn
  left: number
  top: number
  button: HTMLButtonElement
}

const POPOVER_WIDTH = 250

export default function HistoryPage() {
  const { t, i18n } = useTranslation()
  const loaded = useLoaderData<HistoryLoaderData | null>()
  const [params, setParams] = useSearchParams()
  const [open, setOpen] = useState<OpenFilter | null>(null)
  const section = useRef<HTMLElement>(null)

  if (!loaded) return <LoadingPage />
  const { filter, data } = loaded
  const date = (value: string | null) => formatDate(value, i18n.resolvedLanguage)
  const rows = data?.content ?? []

  function goToPage(page: number) {
    const next = new URLSearchParams(params)
    next.set('page', String(page))
    setParams(next)
  }

  function applyFilter(values: Parameters<typeof withFilter>[1]) {
    setParams(withFilter(params, values))
    closeFilter()
  }

  function closeFilter() {
    open?.button.focus()
    setOpen(null)
  }

  function openFilter(column: FilterColumn, button: HTMLButtonElement) {
    if (open?.column === column) {
      closeFilter()
      return
    }
    // Das Menü liegt außerhalb des horizontal scrollenden Tabellenrahmens, sonst würde es abgeschnitten.
    const box = section.current?.getBoundingClientRect()
    const anchor = button.getBoundingClientRect()
    if (!box) return
    const left = Math.max(8, Math.min(anchor.left - box.left, box.width - POPOVER_WIDTH - 8))
    setOpen({ column, left, top: anchor.bottom - box.top + 6, button })
  }

  const active: { key: string; label: string; clear: Parameters<typeof withFilter>[1] }[] = []
  if (filter.flat) active.push({ key: 'flat', label: `${t('inspections.history.flat')}: ${filter.flat}`, clear: { flat: '' } })
  if (filter.followUp) {
    active.push({
      key: 'followUp',
      label: `${t('inspections.history.kind')}: ${t(filter.followUp === 'true' ? 'inspections.history.followUp' : 'inspections.history.initial')}`,
      clear: { followUp: '' },
    })
  }
  if (filter.result) {
    active.push({ key: 'result', label: `${t('inspections.result')}: ${t(`inspections.overall.${filter.result}`)}`, clear: { result: '' } })
  }
  if (filter.from || filter.to) {
    active.push({
      key: 'date',
      label: `${t('inspections.history.date')}: ${filter.from ? date(filter.from) : '…'} – ${filter.to ? date(filter.to) : '…'}`,
      clear: { from: '', to: '' },
    })
  }

  const header = (column: SortColumn, label: string, filterColumn?: FilterColumn, filtered = false) => (
    <HeaderCell label={label} column={column} filter={filter}
      onSort={() => setParams(toggleSort(params, filter, column))}
      filterButton={filterColumn && (
        <FilterButton label={label} active={filtered} expanded={open?.column === filterColumn}
          onClick={(button) => openFilter(filterColumn, button)} />
      )} />
  )

  return (
    <section className="card obm ins-history" ref={section}>
      <InspectionTabs />
      <h2>{t('inspections.history.title')}</h2>
      <p className="xs mut">{t('inspections.history.hint')}</p>

      {active.length > 0 && (
        <div className="ins-active" aria-label={t('inspections.history.activeFilters')}>
          {active.map((entry) => (
            <button key={entry.key} type="button" className="chip inf ins-active-chip"
              aria-label={t('inspections.history.removeFilter', { filter: entry.label })}
              onClick={() => setParams(withFilter(params, entry.clear))}>
              {entry.label} <span aria-hidden="true">✕</span>
            </button>
          ))}
          <Link className="btn sm gh" to="/inspections/history">{t('inspections.history.reset')}</Link>
        </div>
      )}
      {!data && <p className="err" role="alert">{t('inspections.history.invalidRange')}</p>}

      <div className="tw">
        <table className="tbl">
          <thead>
            <tr>
              {header('date', t('inspections.history.date'), 'date', Boolean(filter.from || filter.to))}
              {header('flat', t('inspections.history.flat'), 'flat', Boolean(filter.flat))}
              {header('kind', t('inspections.history.kind'), 'kind', Boolean(filter.followUp))}
              {header('result', t('inspections.result'), 'result', Boolean(filter.result))}
              <th>{t('inspections.defects')}</th>
              {header('inspector', t('inspections.history.inspector'))}
              <th><span className="ins-sr">{t('inspections.history.view')}</span></th>
            </tr>
          </thead>
          <tbody>
            {data && rows.length === 0 && (
              <tr>
                <td colSpan={7} className="mut">
                  {t(hasFilter(filter) ? 'inspections.history.noMatch' : 'inspections.history.empty')}
                </td>
              </tr>
            )}
            {rows.map((entry) => (
              <tr key={entry.id}>
                <td>{date(entry.inspectedAt)}</td>
                <td><b>{entry.flatLabel}</b></td>
                <td>{entry.round > 0 ? t('inspections.round', { round: entry.round }) : t('inspections.history.initial')}</td>
                <td>
                  {entry.overallResult
                    ? <span className={`chip ${entry.overallResult === 'PASSED' ? 'ok' : 'bad'}`}>{t(`inspections.overall.${entry.overallResult}`)}</span>
                    : '–'}
                </td>
                <td>{entry.deficiencies}</td>
                <td>{entry.inspectorName ?? '–'}</td>
                <td>
                  <Link className="btn sm gh" to={`/inspections/${entry.id}`}
                    aria-label={t('inspections.history.viewOf', { label: entry.flatLabel, date: date(entry.inspectedAt) })}>
                    {t('inspections.history.view')}
                  </Link>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {open && (
        <FilterPopover key={open.column} column={open.column} filter={filter} left={open.left} top={open.top}
          onApply={applyFilter} onClose={closeFilter} />
      )}

      {data && data.page.totalPages > 1 && (
        <nav className="acts" aria-label={t('pages.pagination')}>
          <button className="btn sec" disabled={data.page.number === 0} onClick={() => goToPage(data.page.number - 1)}>{t('pages.previous')}</button>
          <button className="btn sec" disabled={data.page.number + 1 >= data.page.totalPages} onClick={() => goToPage(data.page.number + 1)}>{t('pages.next')}</button>
        </nav>
      )}
    </section>
  )
}

interface HeaderCellProps {
  label: string
  column: SortColumn
  filter: HistoryFilter
  onSort: () => void
  filterButton?: ReactNode
}

function HeaderCell({ label, column, filter, onSort, filterButton }: HeaderCellProps) {
  const { t } = useTranslation()
  const sorted = filter.sort === column
  const arrow = !sorted ? '↕' : filter.dir === 'asc' ? '↑' : '↓'
  return (
    <th aria-sort={sorted ? (filter.dir === 'asc' ? 'ascending' : 'descending') : 'none'}>
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

function FilterButton({ label, active, expanded, onClick }: FilterButtonProps) {
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

interface FilterPopoverProps {
  column: FilterColumn
  filter: HistoryFilter
  left: number
  top: number
  onApply: (values: Parameters<typeof withFilter>[1]) => void
  onClose: () => void
}

function FilterPopover({ column, filter, left, top, onApply, onClose }: FilterPopoverProps) {
  const { t } = useTranslation()
  const box = useRef<HTMLFormElement>(null)
  // Immer die aktuelle Funktion aufrufen, ohne Fokus und Listener bei jedem Rendern neu zu setzen.
  const close = useRef(onClose)
  useEffect(() => { close.current = onClose })

  useEffect(() => {
    box.current?.querySelector<HTMLElement>('input, select')?.focus()
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
    if (column === 'date') onApply({ from: value('from'), to: value('to') })
    else if (column === 'flat') onApply({ flat: value('flat') })
    else if (column === 'kind') onApply({ followUp: value('followUp') })
    else onApply({ result: value('result') })
  }

  const clear = column === 'date' ? { from: '', to: '' }
    : column === 'flat' ? { flat: '' } : column === 'kind' ? { followUp: '' } : { result: '' }
  const title = {
    date: t('inspections.history.date'), flat: t('inspections.history.flat'),
    kind: t('inspections.history.kind'), result: t('inspections.result'),
  }[column]

  return (
    <form ref={box} className="ins-pop" role="dialog" aria-label={t('inspections.history.filterBy', { column: title })}
      style={{ left, top, width: POPOVER_WIDTH }} onSubmit={submit}>
      {column === 'date' && (
        <>
          <div className="fld">
            <label htmlFor="history-from">{t('inspections.history.from')}</label>
            <input id="history-from" name="from" type="date" defaultValue={filter.from} />
          </div>
          <div className="fld">
            <label htmlFor="history-to">{t('inspections.history.to')}</label>
            <input id="history-to" name="to" type="date" defaultValue={filter.to} />
          </div>
        </>
      )}
      {column === 'flat' && (
        <div className="fld">
          <label htmlFor="history-flat">{t('inspections.history.flatContains')}</label>
          <input id="history-flat" name="flat" maxLength={80} defaultValue={filter.flat} placeholder="S-01" />
        </div>
      )}
      {column === 'kind' && (
        <div className="fld">
          <label htmlFor="history-kind">{t('inspections.history.kind')}</label>
          <select id="history-kind" name="followUp" defaultValue={filter.followUp}>
            <option value="">{t('inspections.history.all')}</option>
            <option value="false">{t('inspections.history.initial')}</option>
            <option value="true">{t('inspections.history.followUp')}</option>
          </select>
        </div>
      )}
      {column === 'result' && (
        <div className="fld">
          <label htmlFor="history-result">{t('inspections.result')}</label>
          <select id="history-result" name="result" defaultValue={filter.result}>
            <option value="">{t('inspections.history.all')}</option>
            <option value="PASSED">{t('inspections.overall.PASSED')}</option>
            <option value="FAILED">{t('inspections.overall.FAILED')}</option>
          </select>
        </div>
      )}
      <div className="acts" style={{ marginTop: 0 }}>
        <button className="btn sm" type="submit">{t('inspections.history.apply')}</button>
        <button className="btn sm gh" type="button" onClick={() => onApply(clear)}>{t('inspections.history.clear')}</button>
      </div>
    </form>
  )
}
