import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { createBrowserRouter } from 'react-router'
import './i18n'
import './index.css'
import App from './App'
import { createAppRoutes } from './app/router'
import { initializeAuth } from './auth'
import { invalidateSession } from './auth/session'
import LoadingPage from './pages/LoadingPage'

const root = createRoot(document.getElementById('root')!)
root.render(<LoadingPage />)

// Keycloak processes callback parameters and cleans the URL before history is captured.
void initializeAuth().catch(() => {}).then(() => {
  invalidateSession()
  const router = createBrowserRouter(createAppRoutes())
  root.render(<StrictMode><App router={router} /></StrictMode>)
})
