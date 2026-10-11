import { useState } from 'react'
import { apiFetch } from '../api/client'
import { CompanyPicker } from '../components/CompanyPicker'
import { useCompanies } from '../hooks/useCompanies'
import { AppShell } from '../layout/AppShell'

interface AuditLogEntry {
  id: number
  entryDatetime: string
  username: string
  action: string
  targetRef: string
  oldValue: string | null
  newValue: string | null
  reason: string | null
}

const LOAD_ERROR = 'Could not load the audit log. Try again.'

const ENTITY_TYPES: { value: string; label: string }[] = [
  { value: '', label: 'All' },
  { value: 'TIMESTAMP_CORRECTED', label: 'Timestamp Corrections' },
  { value: 'OT_DAYLEVEL_SET', label: 'Day-Level OT' },
  { value: 'OT_EMPLOYEE_SET', label: 'Employee OT Authorization' },
  { value: 'NOTE_ADDED', label: 'Notes' },
]

function summarize(entry: AuditLogEntry): string {
  if (entry.oldValue != null || entry.newValue != null) {
    return `${entry.oldValue ?? '(none)'} → ${entry.newValue ?? '(none)'}`
  }
  return entry.reason ?? entry.newValue ?? ''
}

export function AuditLogPage() {
  const { companies, companyId, setCompanyId } = useCompanies()
  const [entityType, setEntityType] = useState('')
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')
  const [entries, setEntries] = useState<AuditLogEntry[] | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const canLoad = companyId !== ''

  async function loadEntries() {
    if (!canLoad) return
    setLoading(true)
    setError(null)

    try {
      const params = new URLSearchParams()
      if (entityType !== '') params.set('entityType', entityType)
      if (from !== '') params.set('from', from)
      if (to !== '') params.set('to', to)
      const query = params.toString()

      const response = await apiFetch(`/api/audit-log/${companyId}${query ? `?${query}` : ''}`)
      if (response.status === 401) return

      if (!response.ok) {
        setError(LOAD_ERROR)
        setEntries(null)
        return
      }

      const data: AuditLogEntry[] = await response.json()
      setEntries(data)
    } catch {
      setError(LOAD_ERROR)
      setEntries(null)
    } finally {
      setLoading(false)
    }
  }

  return (
    <AppShell title="Audit Log">
      <section className="filters">
        <CompanyPicker companies={companies} value={companyId} onChange={setCompanyId} />

        <div className="field">
          <label className="field__label" htmlFor="entityType">
            Entity Type
          </label>
          <select
            id="entityType"
            className="select"
            value={entityType}
            onChange={(event) => setEntityType(event.target.value)}
          >
            {ENTITY_TYPES.map((et) => (
              <option key={et.value} value={et.value}>
                {et.label}
              </option>
            ))}
          </select>
        </div>

        <div className="field">
          <label className="field__label" htmlFor="from">
            From
          </label>
          <input
            id="from"
            type="date"
            className="input"
            value={from}
            onChange={(event) => setFrom(event.target.value)}
          />
        </div>

        <div className="field">
          <label className="field__label" htmlFor="to">
            To
          </label>
          <input id="to" type="date" className="input" value={to} onChange={(event) => setTo(event.target.value)} />
        </div>

        <button type="button" className="btn btn--primary" onClick={loadEntries} disabled={!canLoad || loading}>
          {loading ? 'Loading…' : 'Load'}
        </button>
      </section>

      {loading && <p className="state">Loading audit log…</p>}
      {error && (
        <p className="alert" role="alert">
          {error}
        </p>
      )}

      {entries && !loading && (
        <>
          {entries.length === 0 && <p className="state">No audit entries match these filters.</p>}

          {entries.length > 0 && (
            <div className="table-wrap">
              <table className="table">
                <thead>
                  <tr>
                    <th>Timestamp</th>
                    <th>Entity Type</th>
                    <th>Changed By</th>
                    <th>Summary</th>
                  </tr>
                </thead>
                <tbody>
                  {entries.map((entry) => (
                    <tr key={entry.id}>
                      <td>{entry.entryDatetime}</td>
                      <td>{entry.action}</td>
                      <td>{entry.username}</td>
                      <td>{summarize(entry)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </>
      )}
    </AppShell>
  )
}
