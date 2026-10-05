import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: '.',
  timeout: 30000,
  expect: {
    timeout: 5000
  },
  fullyParallel: true,
  reporter: 'html',
  use: {
    actionTimeout: 0,
    trace: 'on-first-retry',
    viewport: { width: 1080, height: 2400 } // Typical 20:9 phone aspect ratio
  },
  projects: [
    {
      name: 'Mobile Phone AMOLED',
      use: {
        ...devices['Pixel 7'],
        colorScheme: 'dark'
      }
    }
  ]
});
