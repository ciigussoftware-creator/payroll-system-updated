import { afterEach } from 'vitest'
import { cleanup } from '@testing-library/react'
import '@testing-library/jest-dom/vitest'

// Not using vitest's `globals: true`, so @testing-library/react's automatic
// afterEach cleanup (which detects a global `afterEach`) never registers —
// wire it up explicitly instead.
afterEach(() => {
  cleanup()
})
