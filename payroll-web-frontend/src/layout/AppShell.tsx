import type { ReactNode } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'

const NAV_LINKS = [
  { to: '/', label: 'Dashboard' },
  { to: '/timestamps', label: 'Timestamps' },
  { to: '/ot-management', label: 'OT Management' },
  { to: '/notes', label: 'Notes' },
  { to: '/audit-log', label: 'Audit Log' },
]

interface AppShellProps {
  title: string
  children: ReactNode
}

export function AppShell({ title, children }: AppShellProps) {
  const { username, logout } = useAuth()
  const location = useLocation()

  return (
    <div className="app-shell">
      <header className="app-header">
        <span className="app-header__brand">Payroll Admin</span>
        <nav className="app-nav" aria-label="Primary">
          {NAV_LINKS.map((link) => (
            <Link
              key={link.to}
              to={link.to}
              className="app-nav__link"
              aria-current={location.pathname === link.to ? 'page' : undefined}
            >
              {link.label}
            </Link>
          ))}
        </nav>
        <div className="app-header__user">
          <span>Logged in as {username}</span>
          <button type="button" className="btn btn--secondary" onClick={logout}>
            Log out
          </button>
        </div>
      </header>

      <main className="app-main">
        <h1 className="page-title">{title}</h1>
        {children}
      </main>
    </div>
  )
}
