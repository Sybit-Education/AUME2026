import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { createBrowserRouter } from 'react-router'
import '@/lib/i18n'
import '@fontsource/manrope/400.css'
import '@fontsource/manrope/500.css'
import '@fontsource/manrope/600.css'
import '@fontsource/manrope/700.css'
import './index.css'
import App from '@/app/App'
import { createAppRoutes } from '@/app/router'
import { initializeAuth } from '@/lib/auth/client'
import { invalidateSession } from '@/lib/auth/session'
import LoadingPage from '@/components/LoadingPage'

const root = createRoot(document.getElementById('root')!)
root.render(<LoadingPage />)

// Keycloak processes callback parameters and cleans the URL before history is captured.
void initializeAuth().catch(() => {}).then(() => {
  invalidateSession()
  const router = createBrowserRouter(createAppRoutes())
  root.render(<StrictMode><App router={router} /></StrictMode>)
})
