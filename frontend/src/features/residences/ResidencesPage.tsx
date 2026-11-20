import { useTranslation } from 'react-i18next'
import { Form, Link, useActionData, useLoaderData, useNavigation, useSearchParams } from 'react-router'
import LoadingPage from '@/components/LoadingPage'
import type { StaffActionResult } from './actions'
import type { Residence, StaffMember } from './api'
import type { ResidencesData } from './routes'
import './residences.css'

function residencesUrl(page: number, edit?: number) {
  return edit === undefined ? `/residences?page=${page}` : `/residences?page=${page}&edit=${edit}`
}

function ViewRow({ residence, page, canEdit }: { residence: Residence; page: number; canEdit: boolean }) {
  const { t } = useTranslation()
  return (
    <tr>
      <th scope="row">{residence.name}</th>
      <td>
        {residence.flats.length === 0 ? <span className="mut">{t('residences.noFlats')}</span> : (
          <ul className="flat-chips" aria-label={t('residences.flats')}>
            {residence.flats.map((flat) => <li className="chip" key={flat}>{flat}</li>)}
          </ul>
        )}
      </td>
      <td>{residence.caretaker?.name ?? <span className="mut">{t('residences.noCaretaker')}</span>}</td>
      <td>{residence.deputy?.name ?? <span className="mut">{t('residences.noDeputy')}</span>}</td>
      {canEdit && (
        <td className="r">
          <Link className="btn gh sm" to={residencesUrl(page, residence.id)}
            aria-label={t('residences.editFor', { name: residence.name })}>{t('residences.edit')}</Link>
        </td>
      )}
    </tr>
  )
}

function EditRow({ residence, page, caretakers }: { residence: Residence; page: number; caretakers: StaffMember[] }) {
  const { t } = useTranslation()
  const result = useActionData<StaffActionResult | undefined>()
  const saving = useNavigation().state === 'submitting'
  return (
    <tr>
      <td colSpan={5}>
        {/* Die Action-URL behält `edit`: Nach einem Fehler bleibt das Formular mit der Meldung offen. */}
        <Form method="post" action={residencesUrl(page, residence.id)} aria-label={t('residences.editFor', { name: residence.name })}>
          <input type="hidden" name="residenceId" value={residence.id} />
          <p className="b">{residence.name}</p>
          <div className="g2 staff-fields">
            <div className="fld">
              <label htmlFor="caretakerId">{t('residences.caretaker')}</label>
              <select id="caretakerId" name="caretakerId" required defaultValue={residence.caretaker?.id ?? ''}>
                {!residence.caretaker && <option value="" disabled>{t('residences.chooseCaretaker')}</option>}
                {caretakers.map((person) => <option key={person.id} value={person.id}>{person.name}</option>)}
              </select>
            </div>
            <div className="fld">
              <label htmlFor="deputyId">{t('residences.deputy')}</label>
              <select id="deputyId" name="deputyId" defaultValue={residence.deputy?.id ?? ''}>
                <option value="">{t('residences.noDeputy')}</option>
                {caretakers.map((person) => <option key={person.id} value={person.id}>{person.name}</option>)}
              </select>
            </div>
          </div>
          {result && <p className="err" role="alert">{t(result.error)}</p>}
          <div className="acts">
            <button className="btn sm" type="submit" disabled={saving}>
              {saving ? t('residences.saving') : t('residences.save')}
            </button>
            <Link className="btn gh sm" to={residencesUrl(page)}>{t('residences.cancel')}</Link>
          </div>
        </Form>
      </td>
    </tr>
  )
}

export default function ResidencesPage() {
  const { t } = useTranslation()
  const data = useLoaderData<ResidencesData | null>()
  const [params, setParams] = useSearchParams()

  function goToPage(page: number) {
    const next = new URLSearchParams(params)
    next.set('page', String(page))
    next.delete('edit') // ein angefangenes Bearbeiten gilt nur für die aktuelle Seite
    setParams(next)
  }

  if (!data) return <LoadingPage />

  const { residences, caretakers } = data
  const page = residences.page.number
  const canEdit = caretakers !== null
  const editParam = params.get('edit')
  const editingId = canEdit && editParam !== null && /^\d+$/.test(editParam) ? Number(editParam) : null

  return (
    <section className="card">
      <h2>{t('navigation.residences')}</h2>
      {residences.content.length === 0 ? <p className="mut">{t('pages.noResidences')}</p> : (
        <div className="tw">
          <table className="tbl" aria-label={t('navigation.residences')}>
            <thead>
              <tr>
                <th scope="col">{t('residences.name')}</th>
                <th scope="col">{t('residences.flats')}</th>
                <th scope="col">{t('residences.caretaker')}</th>
                <th scope="col">{t('residences.deputy')}</th>
                {canEdit && <th scope="col" className="r">{t('residences.actions')}</th>}
              </tr>
            </thead>
            <tbody>
              {residences.content.map((residence) => (
                residence.id === editingId && canEdit
                  ? <EditRow key={residence.id} residence={residence} page={page} caretakers={caretakers} />
                  : <ViewRow key={residence.id} residence={residence} page={page} canEdit={canEdit} />
              ))}
            </tbody>
          </table>
        </div>
      )}
      <nav className="acts" aria-label={t('pages.pagination')}>
        <button className="btn sec" disabled={residences.page.number === 0} onClick={() => goToPage(residences.page.number - 1)}>{t('pages.previous')}</button>
        <button className="btn sec" disabled={residences.page.number + 1 >= residences.page.totalPages} onClick={() => goToPage(residences.page.number + 1)}>{t('pages.next')}</button>
      </nav>
    </section>
  )
}
