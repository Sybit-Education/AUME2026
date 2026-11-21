import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { createBrowserRouter } from 'react-router'
import '@/lib/i18n'
import './index.css'
import App from '@/app/App'
import { createAppRoutes } from '@/app/router'
import { initializeAuth } from '@/lib/auth/client'
import { invalidateSession } from '@/lib/auth/session'
import LoadingPage from '@/components/LoadingPage'

if (import.meta.env.PROD && 'serviceWorker' in navigator) {
  void navigator.serviceWorker.register('/sw.js').catch((cause: unknown) => {
    console.error('Unable to register the offline application shell.', cause)
  })
}

const root = createRoot(document.getElementById('root')!)
root.render(<LoadingPage />)

// Keycloak processes callback parameters and cleans the URL before history is captured.
void initializeAuth().catch(() => {}).then(() => {
  invalidateSession()
  const router = createBrowserRouter(createAppRoutes())
  root.render(<StrictMode><App router={router} /></StrictMode>)
})
