import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { apiFetch, setAuthToken, setUnauthorizedHandler } from './client'

describe('apiFetch', () => {
  beforeEach(() => {
    setAuthToken(null)
    setUnauthorizedHandler(null)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('attaches the Authorization header when a token exists', async () => {
    setAuthToken('abc123')
    const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 200 }))
    vi.stubGlobal('fetch', fetchMock)

    await apiFetch('/api/whatever')

    const [, init] = fetchMock.mock.calls[0]
    const headers = new Headers(init.headers)
    expect(headers.get('Authorization')).toBe('Bearer abc123')
  })

  it('omits the Authorization header when no token is set', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 200 }))
    vi.stubGlobal('fetch', fetchMock)

    await apiFetch('/api/whatever')

    const [, init] = fetchMock.mock.calls[0]
    const headers = new Headers(init.headers)
    expect(headers.get('Authorization')).toBeNull()
  })

  it('invokes the unauthorized handler on a 401 response from any call', async () => {
    const handler = vi.fn()
    setUnauthorizedHandler(handler)
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(null, { status: 401 })))

    await apiFetch('/api/whatever')

    expect(handler).toHaveBeenCalledTimes(1)
  })

  it('does not invoke the unauthorized handler on a non-401 response', async () => {
    const handler = vi.fn()
    setUnauthorizedHandler(handler)
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(null, { status: 200 })))

    await apiFetch('/api/whatever')

    expect(handler).not.toHaveBeenCalled()
  })
})
