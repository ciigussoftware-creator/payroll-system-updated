import { useAuth } from '../auth/AuthContext'

export function DashboardPage() {
  const { username, logout } = useAuth()

  return (
    <main>
      <h1>Payroll Admin</h1>
      <p>Logged in as {username}</p>
      <button type="button" onClick={logout}>
        Logout
      </button>
      <p>Phase 6B: Salary Dashboard — coming next</p>
    </main>
  )
}
