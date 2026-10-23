# Handoff: figo.li / Market Day

Updated 2026-10-23.

## Project and commands

Repository: `/home/ryan/codex-sessions/hello-world-spring`.

The project is a Spring Boot application with PostgreSQL, served locally on `127.0.0.1:8080`. Cloudflare Tunnel is an optional Compose profile and routes to `http://app:8080`. See [README.md](README.md) for the full setup and [ARCHITECTURE.md](ARCHITECTURE.md) for the code and data model.

From the repository root:

```bash
# Build the application image only
docker compose build app

# Start the app and database locally
docker compose up -d

# Build and start the app, database, and Cloudflare Tunnel
docker compose --profile tunnel up --build -d

# Inspect status and logs
docker compose --profile tunnel ps
docker compose logs --tail=100 app db cloudflared

# Stop services without deleting application data
docker compose --profile tunnel down
```

The app image builds the JAR with Maven inside Docker. Node.js is only needed on the host to run the isolated JavaScript tests. The tests use Node 22 and run with:

```bash
npm run test:map
```

If installing Node 22 with nvm was interrupted by the machine restart, reopen a terminal and run `nvm install 22`, then verify with `node --version`.

## Current implementation notes

- The Market Day map uses numbered buttons for list entries; tapping a number toggles the checked state across the map, item index, and list view.
- `src/main/resources/static/map-position.js` contains DOM-independent placement helpers. `src/test-js/map-position.test.cjs` tests their mapping calculations; these tests do not measure final browser pixel rendering.
- The latest click fix keeps the server-confirmed PATCH response in the current UI instead of fetching the whole list immediately, avoiding a stale-read visual revert. `market/index.html` has a cache-busted app script URL.
- The entry PATCH now locks and flushes its checked-state update. Client-side store/list loads discard stale responses, and failed item checks roll back with an accessible error notice.
- `npm run test:e2e` runs the Playwright check in `src/test-e2e/list-check-persistence.spec.cjs`; it requires `MARKET_TEST_EMAIL` and `MARKET_TEST_PASSWORD`. The last run skipped because neither variable was configured. `@playwright/test` is pinned to 1.56.1 to match the Playwright browser image.
- One duplicate `Frozen Orange Chicken` entry was removed from Winco Groceries (the newer row); the earlier row remains. The actual failing PATCH target is `/api/stores/1/entries/23` (`Frozen Corn`), which exists locally on list 3/store 1; list and store owner IDs both match. The PATCH handler now returns distinct messages for a missing entry, wrong store, wrong list owner, or wrong store owner. After a hard refresh and retry, use the response message to continue diagnosing.
- Follow-up then produced a 500 for `/api/stores/1/entries/6`. The app trace showed `e.list.owner` was null despite list 3 having `owner_id=1` in PostgreSQL. `setChecked` now reads both store ID and owner email together from `shopping_lists` joined to `app_users`, and no longer relies on either lazy association for authorization. The controller test reproduces null `list.store` and `list.owner` associations with valid persisted scope data. `mvn test` passed in the Maven container (1 test). Anonymous curl still cannot test the authenticated route (403); user confirmation is needed to verify in the signed-in app. Entry 6 was unchecked in the database during diagnosis.
- Map numbers now sit on aisle centerlines. `npm run test:map` passes with the updated expected positions.
- PostgreSQL stores user data and Spring Session JDBC login sessions. Sessions are configured to expire after 90 days of inactivity; they should survive app container and machine restarts. The previous suggestion that rebuilding erased the server-side login was incorrect.
- PostgreSQL data is in the `postgres_data` named volume. Do not run `docker compose down -v` unless intentionally deleting the database.

## State at handoff

The app image was rebuilt and restarted with the store-ID check fix; container is up and PostgreSQL is healthy. The Playwright browser test has not exercised an account yet because test credentials were unavailable.

## Follow-up TODOs

- [ ] Fix early wrapping in the “All list items” view. On the “Winco Groceries” list, aisle groups 2, 4, 5, and 6 appear left to right, but the row wraps before aisles 7, 8, and 9; those groups should continue to the right when space allows.

## Earlier deployment notes

Before the prior machine restart, Cloudflare Tunnel was running and had registered connections in its logs, but a public HTTPS probe from the shell timed out, so the outside route was not confirmed during that check.

After reboot, check `docker compose --profile tunnel ps`. If the services did not restart automatically, run `docker compose --profile tunnel up -d`. Then inspect app and tunnel logs. Test local access at `http://localhost:8080/` and public access at `https://figo.li/market/`. If local access works but public access does not, focus on host network connectivity, cloudflared logs, and the Cloudflare hostname route. If the Market page redirects to login, sign in again; do not assume a restart erased sessions because they are JDBC-backed.

The code and documentation changes described here were committed as `1ae3cf6` (`Improve map shopping list interactions and docs`). Confirm the checkout state with `git status` and `git log -1` after reboot.
