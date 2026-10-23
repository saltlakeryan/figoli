const { test, expect } = require('@playwright/test');

test('checked state survives a fresh list read', async ({ page }) => {
  const email = process.env.MARKET_TEST_EMAIL;
  const password = process.env.MARKET_TEST_PASSWORD;
  test.skip(!email || !password, 'Set MARKET_TEST_EMAIL and MARKET_TEST_PASSWORD for the local app.');

  await page.goto('/login?next=%2Fmarket');
  await page.getByLabel('Email address').fill(email);
  await page.getByLabel('Password').fill(password);
  await Promise.all([
    page.waitForURL('**/market**'),
    page.getByRole('button', { name: /sign in/i }).click()
  ]);

  await expect(page.locator('.list-pick').first()).toBeVisible();
  const lists = page.locator('.list-pick');
  let listId;
  let itemId;
  let wasChecked;

  for (let i = 0; i < await lists.count(); i += 1) {
    const list = lists.nth(i);
    listId = await list.getAttribute('data-list');
    await list.click();
    const entry = page.locator('.entries [data-entry-toggle]').first();
    if (await entry.count()) {
      itemId = await entry.getAttribute('data-entry-toggle');
      wasChecked = (await entry.getAttribute('aria-pressed')) === 'true';
      break;
    }
  }

  test.skip(!itemId, 'No list entries are available to exercise the checked state.');

  const entrySelector = `.entries [data-entry-toggle="${itemId}"]`;
  const expected = String(!wasChecked);
  let toggled = false;
  try {
    const patchResponse = page.waitForResponse(response =>
      response.request().method() === 'PATCH' &&
      response.url().includes(`/entries/${itemId}`)
    );
    await page.locator(entrySelector).click();
    const response = await patchResponse;
    if (response.status() !== 200) {
      await expect(page.locator('#notice')).toBeVisible();
      await expect(page.locator('#notice')).toContainText('save');
    }
    expect(response.status(), `PATCH ${response.url()} returned: ${await response.text()}`).toBe(200);
    toggled = true;
    await expect(page.locator(entrySelector)).toHaveAttribute('aria-pressed', expected);

    await page.reload();
    await page.locator(`#lists [data-list="${listId}"]`).click();
    await expect(page.locator(entrySelector)).toHaveAttribute('aria-pressed', expected);
  } finally {
    if (toggled && await page.locator('.list-pick').count()) {
      const listButton = page.locator(`#lists [data-list="${listId}"]`);
      if (await listButton.count()) await listButton.click();
      const entry = page.locator(entrySelector);
      if (await entry.count() && (await entry.getAttribute('aria-pressed')) !== String(wasChecked)) {
        const patchResponse = page.waitForResponse(response =>
          response.request().method() === 'PATCH' &&
          response.url().includes(`/entries/${itemId}`)
        );
        await entry.click();
        await patchResponse;
      }
    }
  }
});
