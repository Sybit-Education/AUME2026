import type { createBrowserRouter } from 'react-router'
import { RouterProvider } from 'react-router/dom'
import AuthProvider from './auth/AuthProvider'
import './App.css'

export default function App({ router }: { router: ReturnType<typeof createBrowserRouter> }) {
  function getRequestedPath() {
    // Read the actual router state: React context can still show the previous page
    // when a synchronous session-expiry notification interrupts a transition.
    const location = router.state.navigation.location ?? router.state.location
    return location.pathname + location.search + location.hash
  }
  return <AuthProvider getRequestedPath={getRequestedPath}><RouterProvider router={router} /></AuthProvider>
}
