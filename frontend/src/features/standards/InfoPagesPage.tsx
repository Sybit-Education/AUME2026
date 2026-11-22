import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Form, useActionData, useLoaderData, useNavigation } from 'react-router'
import LoadingPage from '@/components/LoadingPage'
import { useAuth } from '@/lib/auth/AuthContext'
import type { InfoPagesActionResult } from './actions'
import type { InfoPages } from './api'

const fields = [
  { name: 'process', rows: 6 },
  { name: 'consequences', rows: 4 },
  { name: 'standards', rows: 3 },
] as const

export default function InfoPagesPage() {
  const { t } = useTranslation()
  const { user } = useAuth()
  const data = useLoaderData<InfoPages | null>()
  const result = useActionData<InfoPagesActionResult>()
  const transition = useNavigation()
  const [dismissed, setDismissed] = useState<InfoPagesActionResult>()

  if (!data) return <LoadingPage />

  const canEdit = user?.privileges.includes('INFO_PAGE_EDIT') ?? false
  const message = result === dismissed ? undefined : result

  return (
    <>
      <div className="ch"><h2>{t('standards.title')}</h2></div>
      <section className="card">
        <h3>{t('standards.info')}</h3>
        <p className="mut">{t('standards.infoHint')}</p>
        {!canEdit && <p className="note">{t('standards.readOnly')}</p>}
        {/* Der Schlüssel erhält ungespeicherte Eingaben, wenn ein Sprachwechsel den Loader neu ausführt. */}
        <Form method="post" key={data.updatedAt ?? 'new'} onChange={() => setDismissed(result)}>
          {fields.map(({ name, rows }) => (
            <div className="fld" key={name}>
              <label htmlFor={`info-${name}`}>{t(`standards.${name}`)}</label>
              <textarea
                id={`info-${name}`}
                name={name}
                rows={rows}
                defaultValue={data[name]}
                maxLength={20000}
                required
                readOnly={!canEdit}
              />
            </div>
          ))}
          {message && 'error' in message && <p className="err" role="alert">{t(message.error)}</p>}
          {message && 'saved' in message && <p className="note ok" role="status">{t('standards.saved')}</p>}
          {canEdit && (
            <div className="acts">
              <button className="btn" type="submit" disabled={transition.state === 'submitting'}>{t('standards.save')}</button>
            </div>
          )}
        </Form>
      </section>
    </>
  )
}
