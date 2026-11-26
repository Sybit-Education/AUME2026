import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Form, useActionData, useLoaderData, useNavigation } from 'react-router'
import LoadingPage from '@/components/LoadingPage'
import type { WgActionResult } from './actions'
import type { WgData } from './routes'

export default function WgPage() {
  const { t, i18n } = useTranslation()
  const data = useLoaderData<WgData | null>()
  const result = useActionData<WgActionResult | undefined>()
  const busy = useNavigation().state === 'submitting'
  const [confirming, setConfirming] = useState(false)

  if (!data) return <LoadingPage />
  const error = result && <p className="err" role="alert">{t(result.error)}</p>

  if (!data.membership) {
    return (
      <section className="card">
        <h2>{t('wg.title')}</h2>
        <p className="mut">{t('wg.joinIntro')}</p>
        <Form method="post" aria-label={t('wg.join')}>
          <div className="fld">
            <label htmlFor="code">{t('wg.code')}</label>
            <input id="code" name="code" required maxLength={64} autoComplete="off" autoCapitalize="characters" />
          </div>
          {error}
          <div className="acts">
            <button className="btn" type="submit" disabled={busy}>{busy ? t('wg.joining') : t('wg.join')}</button>
          </div>
        </Form>
      </section>
    )
  }

  const { flat, residence, validFrom, members } = data.membership
  // Format in UTC so the date does not shift.
  const since = new Intl.DateTimeFormat(i18n.resolvedLanguage, { dateStyle: 'medium', timeZone: 'UTC' })
    .format(new Date(validFrom))

  return (
    <section className="card">
      <h2>{t('wg.title')}</h2>
      <p><span className="mut">{t('wg.flat')}:</span> <span className="b">{flat}</span></p>
      <p><span className="mut">{t('wg.residence')}:</span> {residence}</p>
      <p><span className="mut">{t('wg.since')}:</span> {since}</p>
      <h3>{t('wg.members')}</h3>
      <ul>{members.map((name, index) => <li key={`${name}-${index}`}>{name}</li>)}</ul>
      {error}
      {confirming ? (
        <Form method="post">
          <input type="hidden" name="intent" value="leave" />
          <p className="note wr">{t('wg.leaveConfirm')}</p>
          <div className="acts">
            <button className="btn dg" type="submit" disabled={busy}>{busy ? t('wg.leaving') : t('wg.leaveYes')}</button>
            <button className="btn gh" type="button" onClick={() => setConfirming(false)}>{t('wg.leaveNo')}</button>
          </div>
        </Form>
      ) : (
        <div className="acts">
          <button className="btn dg" type="button" onClick={() => setConfirming(true)}>{t('wg.leave')}</button>
        </div>
      )}
    </section>
  )
}
