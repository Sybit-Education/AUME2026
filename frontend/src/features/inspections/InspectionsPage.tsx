import { useRef } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useLoaderData } from 'react-router'
import LoadingPage from '@/components/LoadingPage'
import type { InspectionPage, InspectionSummary } from './api'
import type { RequestPage } from './requestsApi'
import RequestsSection from './RequestsPage'
import { formatDayMonth, formatPeriod, formatWindow, today } from './dates'
import { formatDate } from './history'
import InspectionTabs from './InspectionTabs'
import { ActiveFilters, FilterButton, FilterMenu, SortHeader, type ActiveFilter } from './tableControls'
import { compareValues, contains, overlaps, useFilterMenu, useLocalTable, type FilterSpec } from './tableState'

// Seite Kontrollen der Objektbetreuung: oben die Anfragen (Zeitraum abstimmen), darunter die anstehenden Kontrollen.

export interface InspectionsLoaderData {
  inspections: InspectionPage
  /** null ohne das Recht, Anfragen zu beantworten */
  requests: RequestPage | null
}

type Column = 'residence' | 'flat' | 'kind' | 'period' | 'status'
type StatusKey = 'READY' | 'ANNOUNCED' | 'DRAFT'

const statusOrder: StatusKey[] = ['DRAFT', 'READY', 'ANNOUNCED']

/**
 * Angezeigter Status nach dem, was man tun kann: in Bearbeitung (begonnen), startbereit (Zeitraum läuft oder
 * kein Zeitraum, z. B. Nachkontrolle) oder angekündigt (Zeitraum beginnt erst, starten ab dem ersten Tag).
 */
function statusOf(inspection: InspectionSummary): StatusKey {
  if (inspection.status === 'DRAFT') return 'DRAFT'
  return inspection.startsOn && inspection.startsOn > today() ? 'ANNOUNCED' : 'READY'
}

const statusChip: Record<StatusKey, string> = { DRAFT: 'wr', READY: 'ok', ANNOUNCED: 't' }

export default function InspectionsPage() {
  const { t } = useTranslation()
  const loaded = useLoaderData<InspectionsLoaderData | null>()

  if (!loaded) return <LoadingPage />

  return (
    <section className="card obm">
      <InspectionTabs />
      <h2>{t('navigation.inspections')}</h2>
      {loaded.requests && <RequestsSection data={loaded.requests} />}
      <UpcomingSection inspections={loaded.inspections.content} />
    </section>
  )
}

/** Anstehende Kontrollen: sortieren und filtern wie in der Historie, aber im Browser. */
function UpcomingSection({ inspections }: { inspections: InspectionSummary[] }) {
  const { t, i18n } = useTranslation()
  const section = useRef<HTMLElement>(null)
  const menu = useFilterMenu<Column>(section)
  const table = useLocalTable<Column>()
  const { filters } = table
  const language = i18n.resolvedLanguage
  const all = t('inspections.history.all')
  const label: Record<Column, string> = {
    residence: t('inspections.residence'), flat: t('inspections.history.flat'), kind: t('inspections.history.kind'),
    period: t('inspections.period'), status: t('inspections.statusLabel'),
  }
  const kindText = (value: string) => t(value === 'followUp' ? 'inspections.history.followUp' : 'inspections.history.initial')
  const statusText: Record<StatusKey, string> = {
    READY: t('inspections.ready'), ANNOUNCED: t('inspections.announced'), DRAFT: t('inspections.status.DRAFT'),
  }

  const rows = inspections
    .filter((row) => contains(row.residenceName, filters.residence) && contains(row.flatLabel, filters.flat))
    .filter((row) => !filters.kind?.value || (filters.kind.value === 'followUp') === (row.round > 0))
    .filter((row) => overlaps(row.startsOn, row.endsOn, filters.period))
    .filter((row) => !filters.status?.value || statusOf(row) === filters.status.value)
  if (table.sort) {
    const { column, dir } = table.sort
    const key = (row: InspectionSummary): string | number | null => ({
      residence: row.residenceName, flat: row.flatLabel, kind: row.round, period: row.startsOn,
      status: statusOrder.indexOf(statusOf(row)),
    })[column]
    rows.sort((a, b) => compareValues(key(a), key(b), dir))
  }

  const spec: Record<Column, FilterSpec> = {
    residence: { kind: 'text', label: label.residence, value: filters.residence?.text ?? '' },
    flat: { kind: 'text', label: label.flat, value: filters.flat?.text ?? '' },
    kind: { kind: 'options', label: label.kind, value: filters.kind?.value ?? '', options: [
      ['', all], ['initial', kindText('initial')], ['followUp', kindText('followUp')],
    ] },
    period: { kind: 'dates', label: label.period, from: filters.period?.from ?? '', to: filters.period?.to ?? '' },
    status: { kind: 'options', label: label.status, value: filters.status?.value ?? '', options: [
      ['', all], ['READY', statusText.READY], ['ANNOUNCED', statusText.ANNOUNCED], ['DRAFT', statusText.DRAFT],
    ] },
  }

  const active: ActiveFilter[] = []
  const clear = (column: Column) => () => table.setFilter(column, {})
  if (filters.residence?.text) active.push({ key: 'residence', label: `${label.residence}: ${filters.residence.text}`, clear: clear('residence') })
  if (filters.flat?.text) active.push({ key: 'flat', label: `${label.flat}: ${filters.flat.text}`, clear: clear('flat') })
  if (filters.kind?.value) active.push({ key: 'kind', label: `${label.kind}: ${kindText(filters.kind.value)}`, clear: clear('kind') })
  if (filters.period?.from || filters.period?.to) {
    const day = (value?: string) => value ? formatDate(value, language) : '…'
    active.push({ key: 'period', label: `${label.period}: ${day(filters.period.from)} – ${day(filters.period.to)}`, clear: clear('period') })
  }
  if (filters.status?.value) {
    active.push({ key: 'status', label: `${label.status}: ${statusText[filters.status.value as StatusKey]}`, clear: clear('status') })
  }

  const header = (column: Column) => (
    <SortHeader label={label[column]} sorted={table.sortedBy(column)} onSort={() => table.toggleSort(column)}
      filterButton={
        <FilterButton label={label[column]} active={Boolean(filters[column])} expanded={menu.open?.column === column}
          onClick={(button) => menu.toggle(column, button)} />
      } />
  )

  return (
    <section className="ins-section ins-history" ref={section} aria-labelledby="upcoming-title">
      <h3 id="upcoming-title">{t('inspections.upcomingTitle')}</h3>
      <ActiveFilters filters={active} onReset={table.reset} />
      {inspections.length === 0 ? <p className="mut">{t('inspections.none')}</p> : (
        <div className="tw ins-scroll">
          <table className="tbl ins-fixed" aria-label={t('inspections.upcomingTitle')}>
            <colgroup>
              <col style={{ width: '20%' }} />
              <col style={{ width: '10%' }} />
              <col style={{ width: '15%' }} />
              <col style={{ width: '21%' }} />
              <col style={{ width: '16%' }} />
              <col style={{ width: '18%' }} />
            </colgroup>
            <thead>
              <tr>
                {header('residence')}
                {header('flat')}
                {header('kind')}
                {header('period')}
                {header('status')}
                <th scope="col" />
              </tr>
            </thead>
            <tbody>
              {rows.length === 0 && (
                <tr><td colSpan={6} className="mut">{t('inspections.history.noMatch')}</td></tr>
              )}
              {rows.map((inspection) => {
                const hours = formatWindow(inspection.windowStart, inspection.windowEnd)
                const action = inspection.status === 'PLANNED' ? t('inspections.start') : t('inspections.continue')
                const status = statusOf(inspection)
                return (
                  <tr key={inspection.id}>
                    <td>{inspection.residenceName}</td>
                    <td><b>{inspection.flatLabel}</b></td>
                    <td>{inspection.round > 0 ? t('inspections.round', { round: inspection.round }) : kindText('initial')}</td>
                    <td className="ins-period">
                      {inspection.startsOn && inspection.endsOn
                        ? formatPeriod(inspection.startsOn, inspection.endsOn, language)
                        : <span className="mut">{t('inspections.noPeriod')}</span>}
                      {hours && <div className="xs mut">{hours}</div>}
                    </td>
                    <td><span className={`chip ${statusChip[status]}`}>{statusText[status]}</span></td>
                    <td className="r">
                      {/* Angekündigt, aber noch nicht begonnen: sichtbar zum Planen, starten erst ab Beginn des Zeitraums. */}
                      {status === 'ANNOUNCED' && inspection.startsOn ? (
                        <span className="xs mut ins-period">{t('inspections.startsFrom', { date: formatDayMonth(inspection.startsOn, language) })}</span>
                      ) : (
                        <Link className="btn sm" aria-label={`${action}: ${inspection.flatLabel}`}
                          to={inspection.status === 'PLANNED' ? `/inspections/${inspection.id}?start=1` : `/inspections/${inspection.id}`}>
                          {action}
                        </Link>
                      )}
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}
      {menu.open && (
        <FilterMenu key={menu.open.column} spec={spec[menu.open.column]} left={menu.open.left} top={menu.open.top}
          onApply={(values) => { table.setFilter(menu.open!.column, values); menu.close() }} onClose={menu.close} />
      )}
    </section>
  )
}
