import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { setAuthToken, setUnauthorizedHandler } from '../api/client'
import { AuthContext, AuthProvider } from '../auth/AuthContext'
import type { AuthContextValue } from '../auth/AuthContext'
import { NotesPage } from './NotesPage'

const baseAuthValue: AuthContextValue = {
  token: 'tok123',
  username: 'admin',
  isAuthenticated: true,
  login: vi.fn(),
  logout: vi.fn(),
}

const companiesResponse = [
  { id: 1, name: 'Wood Lanka' },
  { id: 2, name: 'DCH Plywood' },
]

const notesResponse = [
  {
    id: 2,
    employeeCode: 'EMP-001',
    noteDate: '2026-06-21',
    text: 'Second note',
    createdBy: 'admin',
    createdAt: '2026-06-21T10:00:00Z',
  },
  {
    id: 1,
    employeeCode: 'EMP-001',
    noteDate: '2026-06-20',
    text: 'First note',
    createdBy: 'admin',
    createdAt: '2026-06-20T10:00:00Z',
  },
]

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

function renderNotesPage(authValue: AuthContextValue = baseAuthValue) {
  return render(
    <MemoryRouter initialEntries={['/notes']}>
      <AuthContext.Provider value={authValue}>
        <Routes>
          <Route path="/login" element={<div>Login Page</div>} />
          <Route path="/notes" element={<NotesPage />} />
        </Routes>
      </AuthContext.Provider>
    </MemoryRouter>,
  )
}

async function selectCompanyAndEmployee(user: ReturnType<typeof userEvent.setup>, employeeCode: string) {
  await waitFor(() => expect(screen.getByRole('option', { name: 'Wood Lanka' })).toBeInTheDocument())
  await user.selectOptions(screen.getByLabelText('Company'), 'Wood Lanka')
  await user.type(screen.getByLabelText('Employee Code'), employeeCode)
}

async function loadWithEmployee(user: ReturnType<typeof userEvent.setup>, employeeCode: string) {
  await selectCompanyAndEmployee(user, employeeCode)
  await user.click(screen.getByRole('button', { name: 'Load' }))
}

describe('NotesPage', () => {
  beforeEach(() => {
    setAuthToken(null)
    setUnauthorizedHandler(null)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  it("loads and renders an employee's notes from a mocked GET", async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse(companiesResponse))
      .mockResolvedValueOnce(jsonResponse(notesResponse))
    vi.stubGlobal('fetch', fetchMock)
    const user = userEvent.setup()

    renderNotesPage()
    await loadWithEmployee(user, 'EMP-001')

    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2))
    expect(fetchMock.mock.calls[1][0]).toContain('/api/notes/1/EMP-001')

    expect(await screen.findByText('Second note')).toBeInTheDocument()
    expect(screen.getByText('First note')).toBeInTheDocument()
  })

  it('adding a note calls POST with the correct body and reloads afterward', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse(companiesResponse))
      .mockResolvedValueOnce(jsonResponse([]))
      .mockResolvedValueOnce(
        jsonResponse({
          id: 3,
          employeeCode: 'EMP-001',
          noteDate: '2026-06-22',
          text: 'New note',
          createdBy: 'admin',
          createdAt: '2026-06-22T10:00:00Z',
        }),
      )
      .mockResolvedValueOnce(jsonResponse(notesResponse))
    vi.stubGlobal('fetch', fetchMock)
    const user = userEvent.setup()

    renderNotesPage()
    await loadWithEmployee(user, 'EMP-001')
    await screen.findByText('No notes for this employee.')

    await user.type(screen.getByLabelText('Date'), '2026-06-22')
    await user.type(screen.getByLabelText('Note'), 'New note')
    await user.click(screen.getByRole('button', { name: 'Add Note' }))

    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(4))
    const [postUrl, postInit] = fetchMock.mock.calls[2]
    expect(postUrl).toContain('/api/notes')
    expect(postInit.method).toBe('POST')
    const body = JSON.parse(postInit.body as string)
    expect(body).toEqual({ companyId: 1, employeeCode: 'EMP-001', noteDate: '2026-06-22', text: 'New note' })

    const [reloadUrl] = fetchMock.mock.calls[3]
    expect(reloadUrl).toContain('/api/notes/1/EMP-001')
  })

  it('disables Add Note when the text is empty', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse(companiesResponse))
      .mockResolvedValueOnce(jsonResponse([]))
    vi.stubGlobal('fetch', fetchMock)
    const user = userEvent.setup()

    renderNotesPage()
    await loadWithEmployee(user, 'EMP-001')
    await screen.findByText('No notes for this employee.')

    await user.type(screen.getByLabelText('Date'), '2026-06-22')
    expect(screen.getByRole('button', { name: 'Add Note' })).toBeDisabled()
  })

  it('shows a generic error state for a non-401 error response', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse(companiesResponse))
      .mockResolvedValueOnce(new Response(null, { status: 500 }))
    vi.stubGlobal('fetch', fetchMock)
    const user = userEvent.setup()

    renderNotesPage()
    await loadWithEmployee(user, 'EMP-001')

    expect(await screen.findByRole('alert')).toHaveTextContent('Could not load notes')
  })

  it('triggers the 6A redirect-to-login behavior on a 401 response', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse(companiesResponse))
      .mockResolvedValueOnce(new Response(null, { status: 401 }))
    vi.stubGlobal('fetch', fetchMock)
    const user = userEvent.setup()

    render(
      <MemoryRouter initialEntries={['/notes']}>
        <AuthProvider>
          <Routes>
            <Route path="/login" element={<div>Login Page</div>} />
            <Route path="/notes" element={<NotesPage />} />
          </Routes>
        </AuthProvider>
      </MemoryRouter>,
    )

    await waitFor(() => expect(screen.getByRole('option', { name: 'Wood Lanka' })).toBeInTheDocument())
    await loadWithEmployee(user, 'EMP-001')

    await waitFor(() => expect(screen.getByText('Login Page')).toBeInTheDocument())
  })
})
