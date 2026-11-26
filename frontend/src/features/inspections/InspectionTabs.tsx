import { useTranslation } from 'react-i18next'
import { NavLink } from 'react-router'

/** Umschalter zwischen den aktuellen Kontrollen (Anfragen und Anstehend) und der Kontroll-Historie. */
export default function InspectionTabs() {
  const { t } = useTranslation()
  const active = ({ isActive }: { isActive: boolean }) => isActive ? 'on' : undefined
  return (
    <nav className="tabs" aria-label={t('navigation.inspections')}>
      <NavLink end to="/inspections" className={active}>{t('inspections.tabCurrent')}</NavLink>
      <NavLink to="/inspections/history" className={active}>{t('inspections.tabHistory')}</NavLink>
    </nav>
  )
}
