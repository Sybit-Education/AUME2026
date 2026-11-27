import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useLocation } from 'react-router'
import NavIcon from '@/components/NavIcon'
import type { LoggedInUser } from '@/lib/auth/client'
import { forgetUser, getSession, subscribe } from '@/lib/auth/session'
import { UiError } from '@/lib/errors'
import { getUnreadCount } from './api'
import { subscribeUnreadCountRefresh } from './unreadCountRefresh'
import './student-messages.css'

const POLL_INTERVAL_MS = 15000

export default function NotificationBell({ user }: { user: LoggedInUser }) {
  const { t } = useTranslation()
  const { pathname } = useLocation()
  const previousPath = useRef(pathname)
  const refreshOnNavigation = useRef<(() => void) | null>(null)
  const [result, setResult] = useState<{ user: LoggedInUser; count: number }>()
  const count = result?.user === user ? result.count : undefined
  const label = count === undefined ? t('more.messages') : t('studentMessages.unreadLabel', { count })

  useEffect(() => {
    let pending: AbortController | undefined
    let disposed = false

    function refresh(replace = false) {
      if (disposed || getSession().user !== user || (pending && !replace)) return
      pending?.abort()
      const request = new AbortController()
      pending = request
      const current = () => !disposed && pending === request && !request.signal.aborted && getSession().user === user
      void getUnreadCount(request.signal).then(
        (value) => {
          if (current()) setResult({ user, count: value })
        },
        (cause: unknown) => {
          if (current() && cause instanceof UiError && cause.key === 'errors.sessionExpired') forgetUser()
        },
      ).finally(() => {
        if (pending === request) pending = undefined
      })
    }

    const refreshWhenVisible = () => {
      if (document.visibilityState === 'visible') refresh()
    }
    const timer = window.setInterval(() => refresh(), POLL_INTERVAL_MS)
    const unsubscribeRefresh = subscribeUnreadCountRefresh((reader) => {
      if (reader === user) refresh(true)
    })
    const unsubscribeSession = subscribe(() => {
      if (getSession().user !== user) stopRequests()
    })

    function stopRequests() {
      disposed = true
      window.clearInterval(timer)
      pending?.abort()
    }

    document.addEventListener('visibilitychange', refreshWhenVisible)
    window.addEventListener('focus', refreshWhenVisible)
    refreshOnNavigation.current = () => refresh(true)
    refresh()
    return () => {
      stopRequests()
      unsubscribeRefresh()
      unsubscribeSession()
      document.removeEventListener('visibilitychange', refreshWhenVisible)
      window.removeEventListener('focus', refreshWhenVisible)
      refreshOnNavigation.current = null
    }
  }, [user])

  useEffect(() => {
    if (previousPath.current !== pathname) {
      previousPath.current = pathname
      refreshOnNavigation.current?.()
    }
  }, [pathname])

  return (
    <Link className="st-icon-btn student-notification-bell" to="/student/messages" aria-label={label}>
      <NavIcon name="bell" />
      {count !== undefined && count > 0 && (
        <span className="student-notification-badge" aria-hidden="true">{count > 99 ? '99+' : count}</span>
      )}
    </Link>
  )
}
