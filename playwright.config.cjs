const { defineConfig } = require('@playwright/test');

module.exports = defineConfig({
  testDir: './src/test-e2e',
  workers: 1,
  reporter: 'list',
  use: {
    baseURL: process.env.MARKET_BASE_URL || 'http://127.0.0.1:8080',
    browserName: 'chromium',
    headless: true
  }
});
