import { useEffect, useState } from 'react'
import { apiFetch } from '../api/client'
import { AppShell } from '../layout/AppShell'

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

const LOAD_ERROR = 'Could not load attendance records. Try again.'
const CORRECTION_ERROR = 'Could not save the correction. Try again.'

// datetime-local inputs use "YYYY-MM-DDTHH:mm" with no seconds; the backend's
// LocalDateTime field accepts that directly.
function toDatetimeLocalValue(scanDatetime: string): string {
  return scanDatetime.slice(0, 16)
}

export function TimestampsPage() {
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
    <AppShell title="Timestamp Corrections">
      <section className="filters">
        <div className="field">
          <label className="field__label" htmlFor="company">
            Company
          </label>
          <select
            id="company"
            className="select"
            value={companyId}
            onChange={(event) => setCompanyId(event.target.value)}
          >
            <option value="">Select a company</option>
            {companies.map((company) => (
              <option key={company.id} value={company.id}>
                {company.name}
              </option>
            ))}
          </select>
        </div>

        <div className="field">
          <label className="field__label" htmlFor="employeeCode">
            Employee Code
          </label>
          <input
            id="employeeCode"
            type="text"
            className="input"
            value={employeeCode}
            onChange={(event) => setEmployeeCode(event.target.value)}
          />
        </div>

        <div className="field">
          <label className="field__label" htmlFor="date">
            Date
          </label>
          <input
            id="date"
            type="date"
            className="input"
            value={date}
            onChange={(event) => setDate(event.target.value)}
          />
        </div>

        <button type="button" className="btn btn--primary" onClick={loadRecords} disabled={!canLoad || loading}>
          {loading ? 'Loading…' : 'Load'}
        </button>
      </section>

      {loading && <p className="state">Loading attendance records…</p>}
      {error && (
        <p className="alert" role="alert">
          {error}
        </p>
      )}
      {saveError && (
        <p className="alert" role="alert">
          {saveError}
        </p>
      )}

      {records && !loading && records.length > 0 && (
        <div className="table-wrap">
          <table className="table">
            <thead>
              <tr>
                <th>Scan Type</th>
                <th>Scan Time</th>
                <th>Correction</th>
              </tr>
            </thead>
            <tbody>
              {records.map((record, index) => {
                const edit = editStateFor(record)
                const disabled = edit.reason.trim() === '' || savingId === record.id
                const isPairStart = index > 0 && record.scanType === 'ENTRY'
                const rowClasses = [
                  record.missingClockOut && 'row--flagged',
                  isPairStart && 'row--pair-start',
                ]
                  .filter(Boolean)
                  .join(' ')
                return (
                  <tr key={record.id} className={rowClasses || undefined}>
                    <td>{record.scanType}</td>
                    <td>
                      {record.scanDatetime}
                      {record.missingClockOut && ' (Missing Clock Out)'}
                    </td>
                    <td>
                      <div className="field">
                        <label className="field__label" htmlFor={`new-time-${record.id}`}>
                          New Time
                        </label>
                        <input
                          id={`new-time-${record.id}`}
                          type="datetime-local"
                          className="input"
                          value={edit.newScanDatetime}
                          onChange={(event) =>
                            updateEdit(record.id, { newScanDatetime: event.target.value }, record)
                          }
                        />
                      </div>
                      <div className="field">
                        <label className="field__label" htmlFor={`reason-${record.id}`}>
                          Reason
                        </label>
                        <input
                          id={`reason-${record.id}`}
                          type="text"
                          className="input"
                          placeholder="Required to save"
                          value={edit.reason}
                          onChange={(event) => updateEdit(record.id, { reason: event.target.value }, record)}
                        />
                      </div>
                      <button
                        type="button"
                        className="btn btn--primary"
                        onClick={() => handleCorrect(record)}
                        disabled={disabled}
                      >
                        {savingId === record.id ? 'Saving…' : 'Correct'}
                      </button>
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}

      {records && !loading && records.length === 0 && (
        <div className="panel">
          <p className="state">
            No attendance records for {employeeCode.trim()} on {date}.
          </p>
        </div>
      )}
    </AppShell>
  )
}
