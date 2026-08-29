import { useEffect, useState } from 'react'
import { apiFetch } from '../api/client'
import { AppShell } from '../layout/AppShell'

interface Company {
  id: number
  name: string
}

interface Note {
  id: number
  employeeCode: string
  noteDate: string
  text: string
  createdBy: string
  createdAt: string
}

const LOAD_ERROR = 'Could not load notes. Try again.'
const SAVE_ERROR = 'Could not save the note. Try again.'

export function NotesPage() {
  const [companies, setCompanies] = useState<Company[]>([])
  const [companyId, setCompanyId] = useState('')
  const [employeeCode, setEmployeeCode] = useState('')
  const [notes, setNotes] = useState<Note[] | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const [newDate, setNewDate] = useState('')
  const [newText, setNewText] = useState('')
  const [saving, setSaving] = useState(false)
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

  const canLoad = companyId !== '' && employeeCode.trim() !== ''

  async function loadNotes() {
    if (!canLoad) return
    setLoading(true)
    setError(null)
    setSaveError(null)

    try {
      const response = await apiFetch(`/api/notes/${companyId}/${encodeURIComponent(employeeCode.trim())}`)
      if (response.status === 401) return

      if (!response.ok) {
        setError(LOAD_ERROR)
        setNotes(null)
        return
      }

      const data: Note[] = await response.json()
      setNotes(data)
    } catch {
      setError(LOAD_ERROR)
      setNotes(null)
    } finally {
      setLoading(false)
    }
  }

  async function handleAddNote() {
    if (newText.trim() === '' || newDate === '') return

    setSaving(true)
    setSaveError(null)

    try {
      const response = await apiFetch('/api/notes', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          companyId: Number(companyId),
          employeeCode: employeeCode.trim(),
          noteDate: newDate,
          text: newText,
        }),
      })
      if (response.status === 401) return

      if (!response.ok) {
        setSaveError(SAVE_ERROR)
        return
      }

      setNewText('')
      setNewDate('')
      await loadNotes()
    } catch {
      setSaveError(SAVE_ERROR)
    } finally {
      setSaving(false)
    }
  }

  const addDisabled = saving || newText.trim() === '' || newDate === ''

  return (
    <AppShell title="Notes">
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

        <button type="button" className="btn btn--primary" onClick={loadNotes} disabled={!canLoad || loading}>
          {loading ? 'Loading…' : 'Load'}
        </button>
      </section>

      {loading && <p className="state">Loading notes…</p>}
      {error && (
        <p className="alert" role="alert">
          {error}
        </p>
      )}

      {notes && !loading && (
        <section className="panel">
          <h2>Notes for {employeeCode}</h2>

          {notes.length === 0 && <p className="state">No notes yet for {employeeCode}.</p>}

          {notes.length > 0 && (
            <div className="table-wrap">
              <table className="table">
                <thead>
                  <tr>
                    <th>Date</th>
                    <th>Note</th>
                    <th>Added By</th>
                    <th>Added At</th>
                  </tr>
                </thead>
                <tbody>
                  {notes.map((note) => (
                    <tr key={note.id}>
                      <td>{note.noteDate}</td>
                      <td>{note.text}</td>
                      <td>{note.createdBy}</td>
                      <td>{note.createdAt}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          <div className="field">
            <label className="field__label" htmlFor="newDate">
              Date
            </label>
            <input
              id="newDate"
              type="date"
              className="input"
              value={newDate}
              onChange={(event) => setNewDate(event.target.value)}
            />
          </div>

          <div className="field">
            <label className="field__label" htmlFor="newText">
              Note
            </label>
            <textarea
              id="newText"
              className="textarea"
              value={newText}
              onChange={(event) => setNewText(event.target.value)}
            />
          </div>

          {saveError && (
            <p className="alert" role="alert">
              {saveError}
            </p>
          )}

          <button type="button" className="btn btn--primary" onClick={handleAddNote} disabled={addDisabled}>
            {saving ? 'Saving…' : 'Add Note'}
          </button>
        </section>
      )}
    </AppShell>
  )
}
