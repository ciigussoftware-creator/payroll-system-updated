const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

let currentToken: string | null = null
let unauthorizedHandler: (() => void) | null = null

// Wired up by AuthProvider so apiFetch always sees the latest token
// without every call site having to thread it through.
export function setAuthToken(token: string | null): void {
  currentToken = token
}

// Wired up by AuthProvider so any 401 response — from any feature screen
// built on top of apiFetch — clears auth state and redirects to /login.
export function setUnauthorizedHandler(handler: (() => void) | null): void {
  unauthorizedHandler = handler
}

export async function apiFetch(path: string, init: RequestInit = {}): Promise<Response> {
  const headers = new Headers(init.headers)
  if (currentToken) {
    headers.set('Authorization', `Bearer ${currentToken}`)
  }

  const response = await fetch(`${API_BASE_URL}${path}`, { ...init, headers })

  if (response.status === 401) {
    unauthorizedHandler?.()
  }

  return response
}
