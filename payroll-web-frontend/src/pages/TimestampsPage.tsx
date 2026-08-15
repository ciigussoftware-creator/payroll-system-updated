import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { apiFetch } from '../api/client'
import { useAuth } from '../auth/AuthContext'

interface Company {
  id: number
  name: string
}

interface AttendanceRecordResponse {
  id: number
  scanDatetime: string
  scanType: 'ENTRY' | 'EXIT'
  missingClockOut: boolean
}

interface EditState {
  newScanDatetime: string
  reason: string
}

const LOAD_ERROR = 'Could not load attendance records. Please try again.'
const CORRECTION_ERROR = 'Could not save the correction. Please try again.'
const MISSING_CLOCK_OUT_BACKGROUND = '#fdecea'

// datetime-local inputs use "YYYY-MM-DDTHH:mm" with no seconds; the backend's
// LocalDateTime field accepts that directly.
function toDatetimeLocalValue(scanDatetime: string): string {
  return scanDatetime.slice(0, 16)
}

export function TimestampsPage() {
  const { username, logout } = useAuth()
  const [companies, setCompanies] = useState<Company[]>([])
  const [companyId, setCompanyId] = useState('')
  const [employeeCode, setEmployeeCode] = useState('')
  const [date, setDate] = useState('')
  const [records, setRecords] = useState<AttendanceRecordResponse[] | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [edits, setEdits] = useState<Record<number, EditState>>({})
  const [savingId, setSavingId] = useState<number | null>(null)
  const [saveError, setSaveError] = useState<string | null>(null)

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

  const canLoad = companyId !== '' && employeeCode.trim() !== '' && date !== ''

  function editStateFor(record: AttendanceRecordResponse): EditState {
    return edits[record.id] ?? { newScanDatetime: toDatetimeLocalValue(record.scanDatetime), reason: '' }
  }

  function updateEdit(recordId: number, patch: Partial<EditState>, record: AttendanceRecordResponse) {
    setEdits((prev) => ({
      ...prev,
      [recordId]: { ...editStateFor(record), ...patch },
    }))
  }

  async function loadRecords() {
    if (!canLoad) return
    setLoading(true)
    setError(null)
    setSaveError(null)

    try {
      const response = await apiFetch(
        `/api/attendance/${companyId}/${encodeURIComponent(employeeCode.trim())}/${date}`,
      )
      if (response.status === 401) return

      if (!response.ok) {
        setError(LOAD_ERROR)
        setRecords(null)
        return
      }

      const data: AttendanceRecordResponse[] = await response.json()
      setRecords(data)
      setEdits({})
    } catch {
      setError(LOAD_ERROR)
      setRecords(null)
    } finally {
      setLoading(false)
    }
  }

  async function handleCorrect(record: AttendanceRecordResponse) {
    const edit = editStateFor(record)
    if (edit.reason.trim() === '') return

    setSavingId(record.id)
    setSaveError(null)

    try {
      const response = await apiFetch(`/api/attendance/${record.id}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          companyId: Number(companyId),
          newScanDatetime: edit.newScanDatetime,
          reason: edit.reason,
        }),
      })
      if (response.status === 401) return

      if (!response.ok) {
        setSaveError(CORRECTION_ERROR)
        return
      }

      await loadRecords()
    } catch {
      setSaveError(CORRECTION_ERROR)
    } finally {
      setSavingId(null)
    }
  }

  return (
    <main>
      <h1>Timestamp Corrections</h1>
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
          <label htmlFor="employeeCode">Employee Code</label>
          <input
            id="employeeCode"
            type="text"
            value={employeeCode}
            onChange={(event) => setEmployeeCode(event.target.value)}
          />
        </div>

        <div>
          <label htmlFor="date">Date</label>
          <input id="date" type="date" value={date} onChange={(event) => setDate(event.target.value)} />
        </div>

        <button type="button" onClick={loadRecords} disabled={!canLoad || loading}>
          {loading ? 'Loading…' : 'Load'}
        </button>
      </section>

      {loading && <p>Loading attendance records…</p>}
      {error && <p role="alert">{error}</p>}
      {saveError && <p role="alert">{saveError}</p>}

      {records && !loading && (
        <table>
          <thead>
            <tr>
              <th>Scan Type</th>
              <th>Scan Time</th>
              <th>Correction</th>
            </tr>
          </thead>
          <tbody>
            {records.map((record) => {
              const edit = editStateFor(record)
              const disabled = edit.reason.trim() === '' || savingId === record.id
              return (
                <tr
                  key={record.id}
                  style={record.missingClockOut ? { backgroundColor: MISSING_CLOCK_OUT_BACKGROUND } : undefined}
                >
                  <td>{record.scanType}</td>
                  <td>
                    {record.scanDatetime}
                    {record.missingClockOut && ' (Missing Clock Out)'}
                  </td>
                  <td>
                    <label htmlFor={`new-time-${record.id}`}>New Time</label>
                    <input
                      id={`new-time-${record.id}`}
                      type="datetime-local"
                      value={edit.newScanDatetime}
                      onChange={(event) => updateEdit(record.id, { newScanDatetime: event.target.value }, record)}
                    />
                    <label htmlFor={`reason-${record.id}`}>Reason</label>
                    <input
                      id={`reason-${record.id}`}
                      type="text"
                      value={edit.reason}
                      onChange={(event) => updateEdit(record.id, { reason: event.target.value }, record)}
                    />
                    <button type="button" onClick={() => handleCorrect(record)} disabled={disabled}>
                      {savingId === record.id ? 'Saving…' : 'Correct'}
                    </button>
                  </td>
                </tr>
              )
            })}
          </tbody>
        </table>
      )}

      {records && !loading && records.length === 0 && <p>No attendance records for that date.</p>}
    </main>
  )
}
