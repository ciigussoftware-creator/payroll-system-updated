import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { apiFetch } from '../api/client'
import { useAuth } from '../auth/AuthContext'

interface Company {
  id: number
  name: string
}

type DayType = 'SUNDAY' | 'WEEKDAY' | 'MERCANTILE_HOLIDAY' | 'SPECIAL'

interface DayLevelOtState {
  isAllStaffOt: boolean
  dayType: DayType
  setAt: string | null
  setBy: number | null
}

interface EmployeeAuthorization {
  employeeCode: string
  authDate: string
  authorized: boolean
  setAt: string | null
  setBy: string | null
}

const DAY_TYPES: DayType[] = ['SUNDAY', 'WEEKDAY', 'MERCANTILE_HOLIDAY', 'SPECIAL']

const LOAD_ERROR = 'Could not load OT configuration. Please try again.'
const DAY_LEVEL_SAVE_ERROR = 'Could not save the day-level OT switch. Please try again.'
const EMPLOYEE_SAVE_ERROR = 'Could not save the employee authorization. Please try again.'

function todayIsoDate(): string {
  return new Date().toISOString().slice(0, 10)
}

export function OtManagementPage() {
  const { username, logout } = useAuth()
  const [companies, setCompanies] = useState<Company[]>([])
  const [companyId, setCompanyId] = useState('')
  const [date, setDate] = useState('')

  const [dayLevelState, setDayLevelState] = useState<DayLevelOtState | null>(null)
  const [dayLevelAllStaffOt, setDayLevelAllStaffOt] = useState(false)
  const [dayLevelDayType, setDayLevelDayType] = useState<DayType>('WEEKDAY')
  const [dayLevelReason, setDayLevelReason] = useState('')
  const [dayLevelSaving, setDayLevelSaving] = useState(false)
  const [dayLevelSaveError, setDayLevelSaveError] = useState<string | null>(null)

  const [employeeAuths, setEmployeeAuths] = useState<EmployeeAuthorization[] | null>(null)
  const [newEmployeeCode, setNewEmployeeCode] = useState('')
  const [newEmployeeAuthorized, setNewEmployeeAuthorized] = useState(true)
  const [employeeReason, setEmployeeReason] = useState('')
  const [employeeSaving, setEmployeeSaving] = useState(false)
  const [employeeSaveError, setEmployeeSaveError] = useState<string | null>(null)

  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false

    async function loadCompanies() {
      const response = await apiFetch('/api/companies')
      if (!response.ok || cancelled) return
      const data: Company[] = await response.json()
      if (!cancelled) setCompanies(data)
    }

    void loadCompanies()
    return () => {
      cancelled = true
    }
  }, [])

  const canLoad = companyId !== '' && date !== ''
  const isRetroactive = date !== '' && date < todayIsoDate()

  type LoadOutcome = 'ok' | 'unauthorized' | 'error'

  async function loadDayLevel(): Promise<LoadOutcome> {
    const response = await apiFetch(`/api/ot-config/day-level/${companyId}/${date}`)
    if (response.status === 401) return 'unauthorized'
    if (!response.ok) return 'error'

    const data: DayLevelOtState = await response.json()
    setDayLevelState(data)
    setDayLevelAllStaffOt(data.isAllStaffOt)
    setDayLevelDayType(data.dayType)
    return 'ok'
  }

  async function loadEmployeeAuths(): Promise<LoadOutcome> {
    const response = await apiFetch(`/api/ot-config/employee-authorizations/${companyId}/${date}`)
    if (response.status === 401) return 'unauthorized'
    if (!response.ok) return 'error'

    const data: EmployeeAuthorization[] = await response.json()
    setEmployeeAuths(data)
    return 'ok'
  }

  async function loadAll() {
    if (!canLoad) return
    setLoading(true)
    setError(null)
    setDayLevelSaveError(null)
    setEmployeeSaveError(null)
    setDayLevelReason('')
    setEmployeeReason('')

    try {
      const [dayLevelResult, employeeAuthsResult] = await Promise.all([loadDayLevel(), loadEmployeeAuths()])
      if (dayLevelResult === 'unauthorized' || employeeAuthsResult === 'unauthorized') return

      if (dayLevelResult === 'error' || employeeAuthsResult === 'error') {
        setError(LOAD_ERROR)
        setDayLevelState(null)
        setEmployeeAuths(null)
      }
    } catch {
      setError(LOAD_ERROR)
      setDayLevelState(null)
      setEmployeeAuths(null)
    } finally {
      setLoading(false)
    }
  }

  async function handleSaveDayLevel() {
    if (isRetroactive && dayLevelReason.trim() === '') return

    setDayLevelSaving(true)
    setDayLevelSaveError(null)

    try {
      const response = await apiFetch(`/api/ot-config/day-level/${companyId}/${date}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          isAllStaffOt: dayLevelAllStaffOt,
          dayType: dayLevelDayType,
          reason: dayLevelReason,
        }),
      })
      if (response.status === 401) return

      if (!response.ok) {
        setDayLevelSaveError(DAY_LEVEL_SAVE_ERROR)
        return
      }

      setDayLevelReason('')
      await loadDayLevel()
    } catch {
      setDayLevelSaveError(DAY_LEVEL_SAVE_ERROR)
    } finally {
      setDayLevelSaving(false)
    }
  }

  async function handleSaveEmployeeAuth() {
    if (newEmployeeCode.trim() === '') return
    if (isRetroactive && employeeReason.trim() === '') return

    setEmployeeSaving(true)
    setEmployeeSaveError(null)

    try {
      const response = await apiFetch(
        `/api/ot-config/employee-authorization/${companyId}/${encodeURIComponent(newEmployeeCode.trim())}/${date}`,
        {
          method: 'PUT',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            authorized: newEmployeeAuthorized,
            reason: employeeReason,
          }),
        },
      )
      if (response.status === 401) return

      if (!response.ok) {
        setEmployeeSaveError(EMPLOYEE_SAVE_ERROR)
        return
      }

      setEmployeeReason('')
      await loadEmployeeAuths()
    } catch {
      setEmployeeSaveError(EMPLOYEE_SAVE_ERROR)
    } finally {
      setEmployeeSaving(false)
    }
  }

  const dayLevelSaveDisabled = dayLevelSaving || (isRetroactive && dayLevelReason.trim() === '')
  const employeeSaveDisabled =
    employeeSaving || newEmployeeCode.trim() === '' || (isRetroactive && employeeReason.trim() === '')

  return (
    <main>
      <h1>OT Management</h1>
      <p>Logged in as {username}</p>
      <Link to="/">Back to Dashboard</Link>
      <button type="button" onClick={logout}>
        Logout
      </button>

      <section>
        <div>
          <label htmlFor="company">Company</label>
          <select id="company" value={companyId} onChange={(event) => setCompanyId(event.target.value)}>
            <option value="">Select a company</option>
            {companies.map((company) => (
              <option key={company.id} value={company.id}>
                {company.name}
              </option>
            ))}
          </select>
        </div>

        <div>
          <label htmlFor="date">Date</label>
          <input id="date" type="date" value={date} onChange={(event) => setDate(event.target.value)} />
        </div>

        <button type="button" onClick={loadAll} disabled={!canLoad || loading}>
          {loading ? 'Loading…' : 'Load'}
        </button>
      </section>

      {loading && <p>Loading OT configuration…</p>}
      {error && <p role="alert">{error}</p>}

      {dayLevelState && !loading && (
        <section>
          <h2>Day-Level OT Configuration</h2>

          <div>
            <label htmlFor="allStaffOt">All Staff OT</label>
            <input
              id="allStaffOt"
              type="checkbox"
              checked={dayLevelAllStaffOt}
              onChange={(event) => setDayLevelAllStaffOt(event.target.checked)}
            />
          </div>

          <div>
            <label htmlFor="dayType">Day Type</label>
            <select
              id="dayType"
              value={dayLevelDayType}
              onChange={(event) => setDayLevelDayType(event.target.value as DayType)}
            >
              {DAY_TYPES.map((dt) => (
                <option key={dt} value={dt}>
                  {dt}
                </option>
              ))}
            </select>
          </div>

          {isRetroactive && (
            <div>
              <label htmlFor="dayLevelReason">Reason (required for retroactive changes)</label>
              <input
                id="dayLevelReason"
                type="text"
                value={dayLevelReason}
                onChange={(event) => setDayLevelReason(event.target.value)}
              />
            </div>
          )}

          {dayLevelSaveError && <p role="alert">{dayLevelSaveError}</p>}

          <button type="button" onClick={handleSaveDayLevel} disabled={dayLevelSaveDisabled}>
            {dayLevelSaving ? 'Saving…' : 'Save Day-Level OT'}
          </button>
        </section>
      )}

      {employeeAuths && !loading && (
        <section>
          <h2>Per-Employee OT Authorization</h2>

          {employeeAuths.length === 0 && <p>No employee authorizations set for that date.</p>}

          {employeeAuths.length > 0 && (
            <table>
              <thead>
                <tr>
                  <th>Employee Code</th>
                  <th>Authorized</th>
                  <th>Set By</th>
                  <th>Set At</th>
                </tr>
              </thead>
              <tbody>
                {employeeAuths.map((auth) => (
                  <tr key={auth.employeeCode}>
                    <td>{auth.employeeCode}</td>
                    <td>{auth.authorized ? 'Yes' : 'No'}</td>
                    <td>{auth.setBy}</td>
                    <td>{auth.setAt}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}

          <div>
            <label htmlFor="newEmployeeCode">Employee Code</label>
            <input
              id="newEmployeeCode"
              type="text"
              value={newEmployeeCode}
              onChange={(event) => setNewEmployeeCode(event.target.value)}
            />
          </div>

          <div>
            <label htmlFor="newEmployeeAuthorized">Authorized</label>
            <input
              id="newEmployeeAuthorized"
              type="checkbox"
              checked={newEmployeeAuthorized}
              onChange={(event) => setNewEmployeeAuthorized(event.target.checked)}
            />
          </div>

          {isRetroactive && (
            <div>
              <label htmlFor="employeeReason">Reason (required for retroactive changes)</label>
              <input
                id="employeeReason"
                type="text"
                value={employeeReason}
                onChange={(event) => setEmployeeReason(event.target.value)}
              />
            </div>
          )}

          {employeeSaveError && <p role="alert">{employeeSaveError}</p>}

          <button type="button" onClick={handleSaveEmployeeAuth} disabled={employeeSaveDisabled}>
            {employeeSaving ? 'Saving…' : 'Save Employee Authorization'}
          </button>
        </section>
      )}
    </main>
  )
}
