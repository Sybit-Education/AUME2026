import { useRef } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useLoaderData, useSearchParams } from 'react-router'
import LoadingPage from '@/components/LoadingPage'
import type { HistoryPage as HistoryData } from './api'
import {
  formatDate, hasFilter, toggleSort, withFilter, type FilterKey, type HistoryFilter, type SortColumn,
} from './history'
import InspectionTabs from './InspectionTabs'
import { ActiveFilters, FilterButton, FilterMenu, SortHeader, type ActiveFilter } from './tableControls'
import { useFilterMenu, type FilterSpec, type FilterValues } from './tableState'

export interface HistoryLoaderData {
  filter: HistoryFilter
  /** `null`, wenn der Zeitraum ungültig ist und deshalb nichts geladen wurde. */
  data: HistoryData | null
}

type FilterColumn = 'residence' | 'flat' | 'kind' | 'date' | 'result' | 'defects'

// Gleicher Aufbau wie Anfragen und Anstehend: Wohnanlage, WG, Kontrolle, Datum, Ergebnis, Mängel, Geprüft von.
export default function HistoryPage() {
  const { t, i18n } = useTranslation()
  const loaded = useLoaderData<HistoryLoaderData | null>()
  const [params, setParams] = useSearchParams()
  const section = useRef<HTMLElement>(null)
  const menu = useFilterMenu<FilterColumn>(section)

  if (!loaded) return <LoadingPage />
  const { filter, data } = loaded
  const date = (value: string | null) => formatDate(value, i18n.resolvedLanguage)
  const rows = data?.content ?? []
  const label: Record<FilterColumn, string> = {
    residence: t('inspections.residence'), flat: t('inspections.history.flat'), kind: t('inspections.history.kind'),
    date: t('inspections.history.date'), result: t('inspections.result'), defects: t('inspections.defects'),
  }
  const all = t('inspections.history.all')

  function goToPage(page: number) {
    const next = new URLSearchParams(params)
    next.set('page', String(page))
    setParams(next)
  }

  function update(values: Partial<Record<FilterKey, string>>) {
    setParams(withFilter(params, values))
  }

  const spec: Record<FilterColumn, FilterSpec> = {
    residence: { kind: 'text', label: label.residence, value: filter.residence },
    flat: { kind: 'text', label: label.flat, value: filter.flat },
    kind: { kind: 'options', label: label.kind, value: filter.followUp, options: [
      ['', all], ['false', t('inspections.history.initial')], ['true', t('inspections.history.followUp')],
    ] },
    date: { kind: 'dates', label: label.date, from: filter.from, to: filter.to },
    result: { kind: 'options', label: label.result, value: filter.result, options: [
      ['', all], ['PASSED', t('inspections.overall.PASSED')], ['FAILED', t('inspections.overall.FAILED')],
    ] },
    defects: { kind: 'options', label: label.defects, value: filter.defects, options: [
      ['', all], ['true', t('inspections.history.withDefects')], ['false', t('inspections.history.withoutDefects')],
    ] },
  }

  function apply(column: FilterColumn, values: FilterValues) {
    if (column === 'residence') update({ residence: values.text ?? '' })
    else if (column === 'flat') update({ flat: values.text ?? '' })
    else if (column === 'kind') update({ followUp: values.value ?? '' })
    else if (column === 'date') update({ from: values.from ?? '', to: values.to ?? '' })
    else if (column === 'result') update({ result: values.value ?? '' })
    else update({ defects: values.value ?? '' })
    menu.close()
  }

  const active: ActiveFilter[] = []
  const chip = (key: string, text: string, clear: Partial<Record<FilterKey, string>>) =>
    active.push({ key, label: text, clear: () => update(clear) })
  if (filter.residence) chip('residence', `${label.residence}: ${filter.residence}`, { residence: '' })
  if (filter.flat) chip('flat', `${label.flat}: ${filter.flat}`, { flat: '' })
  if (filter.followUp) {
    chip('kind', `${label.kind}: ${t(filter.followUp === 'true' ? 'inspections.history.followUp' : 'inspections.history.initial')}`, { followUp: '' })
  }
  if (filter.from || filter.to) {
    chip('date', `${label.date}: ${filter.from ? date(filter.from) : '…'} – ${filter.to ? date(filter.to) : '…'}`, { from: '', to: '' })
  }
  if (filter.result) chip('result', `${label.result}: ${t(`inspections.overall.${filter.result}`)}`, { result: '' })
  if (filter.defects) {
    chip('defects', `${label.defects}: ${t(filter.defects === 'true' ? 'inspections.history.withDefects' : 'inspections.history.withoutDefects')}`, { defects: '' })
  }

  const filtered: Record<FilterColumn, boolean> = {
    residence: Boolean(filter.residence), flat: Boolean(filter.flat), kind: Boolean(filter.followUp),
    date: Boolean(filter.from || filter.to), result: Boolean(filter.result), defects: Boolean(filter.defects),
  }
  const header = (column: SortColumn, text: string, filterColumn?: FilterColumn) => (
    <SortHeader label={text} sorted={filter.sort === column ? filter.dir : null}
      onSort={() => setParams(toggleSort(params, filter, column))}
      filterButton={filterColumn && (
        <FilterButton label={text} active={filtered[filterColumn]} expanded={menu.open?.column === filterColumn}
          onClick={(button) => menu.toggle(filterColumn, button)} />
      )} />
  )

  return (
    <section className="card obm ins-history" ref={section}>
      <InspectionTabs />
      <h2>{t('inspections.history.title')}</h2>

      <ActiveFilters filters={active} onReset={() => setParams(new URLSearchParams())} />
      {!data && <p className="err" role="alert">{t('inspections.history.invalidRange')}</p>}

      <div className="tw">
        <table className="tbl ins-fixed" aria-label={t('inspections.history.title')}>
          <colgroup>
            <col style={{ width: '16%' }} />
            <col style={{ width: '9%' }} />
            <col style={{ width: '14%' }} />
            <col style={{ width: '12%' }} />
            <col style={{ width: '15%' }} />
            <col style={{ width: '10%' }} />
            <col style={{ width: '14%' }} />
            <col style={{ width: '10%' }} />
          </colgroup>
          <thead>
            <tr>
              {header('residence', label.residence, 'residence')}
              {header('flat', label.flat, 'flat')}
              {header('kind', label.kind, 'kind')}
              {header('date', label.date, 'date')}
              {header('result', label.result, 'result')}
              {header('defects', label.defects, 'defects')}
              {header('inspector', t('inspections.history.inspector'))}
              <th><span className="ins-sr">{t('inspections.history.view')}</span></th>
            </tr>
          </thead>
          <tbody>
            {data && rows.length === 0 && (
              <tr>
                <td colSpan={8} className="mut">
                  {t(hasFilter(filter) ? 'inspections.history.noMatch' : 'inspections.history.empty')}
                </td>
              </tr>
            )}
            {rows.map((entry) => (
              <tr key={entry.id}>
                <td>{entry.residenceName}</td>
                <td><b>{entry.flatLabel}</b></td>
                <td>{entry.round > 0 ? t('inspections.round', { round: entry.round }) : t('inspections.history.initial')}</td>
                <td>{date(entry.inspectedAt)}</td>
                <td>
                  {entry.overallResult
                    ? <span className={`chip ${entry.overallResult === 'PASSED' ? 'ok' : 'bad'}`}>{t(`inspections.overall.${entry.overallResult}`)}</span>
                    : '–'}
                </td>
                <td>{entry.deficiencies}</td>
                <td>{entry.inspectorName ?? '–'}</td>
                <td className="r">
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

      {menu.open && (
        <FilterMenu key={menu.open.column} spec={spec[menu.open.column]} left={menu.open.left} top={menu.open.top}
          onApply={(values) => apply(menu.open!.column, values)} onClose={menu.close} />
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
