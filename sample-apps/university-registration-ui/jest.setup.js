import '@testing-library/jest-dom';

// Mock fetch for Node.js environment (needed for E2E tests)
if (!global.fetch) {
  global.fetch = jest.fn(() => 
    Promise.resolve({
      ok: true,
      status: 200,
      headers: {
        get: jest.fn(() => null)
      },
      json: jest.fn(() => Promise.resolve({}))
    })
  );
}

// Mock axios only for unit tests (removed to allow real axios for E2E tests)
// Use axios-mock-adapter in individual test files for better control

// Mock Next.js router
jest.mock('next/router', () => ({
  useRouter: () => ({
    push: jest.fn(),
    replace: jest.fn(),
    prefetch: jest.fn(),
    query: {},
    pathname: '/',
    asPath: '/',
  }),
}))

// Mock Next.js navigation
jest.mock('next/navigation', () => ({
  useRouter: () => ({
    push: jest.fn(),
    replace: jest.fn(),
    refresh: jest.fn(),
  }),
  useSearchParams: () => new URLSearchParams(),
  usePathname: () => '/',
}))