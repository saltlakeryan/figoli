# Handoff: figo.li / Market Day

Updated 2026-10-22.

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
- PostgreSQL stores user data and Spring Session JDBC login sessions. Sessions are configured to expire after 90 days of inactivity; they should survive app container and machine restarts. The previous suggestion that rebuilding erased the server-side login was incorrect.
- PostgreSQL data is in the `postgres_data` named volume. Do not run `docker compose down -v` unless intentionally deleting the database.

## State at handoff

The app image was rebuilt and the app container started successfully before the planned machine restart. At the last local check, the app container was up, PostgreSQL was healthy, and `http://127.0.0.1:8080/` returned HTTP 200. An unauthenticated request to `/market/` redirected to `/login`, as expected. Cloudflare Tunnel was running and had registered connections in its logs, but a public HTTPS probe from the shell timed out, so the outside route was not confirmed during that check.

After reboot, check `docker compose --profile tunnel ps`. If the services did not restart automatically, run `docker compose --profile tunnel up -d`. Then inspect app and tunnel logs. Test local access at `http://localhost:8080/` and public access at `https://figo.li/market/`. If local access works but public access does not, focus on host network connectivity, cloudflared logs, and the Cloudflare hostname route. If the Market page redirects to login, sign in again; do not assume a restart erased sessions because they are JDBC-backed.

The code and documentation changes described here were committed as `1ae3cf6` (`Improve map shopping list interactions and docs`). Confirm the checkout state with `git status` and `git log -1` after reboot.
