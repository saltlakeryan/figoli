# figo.li

Small Spring Boot apps under one home page, backed by PostgreSQL. **Market Day** lives at `/market` and provides multiple stores per account, each with its own aisle layout, categories, and shopping lists. All users share one item catalog; aisle placement and categories belong to a store-specific placement. Users register with email and password, then an admin approves their account. Passwords are stored as BCrypt hashes. Signed-in sessions are stored in PostgreSQL and expire after 90 days of inactivity; the browser cookie also lasts up to 90 days. The app and Cloudflare Tunnel share a private Compose network; the app's host port is bound to `127.0.0.1` for local checks.

## Build and start

Docker builds the Spring Boot JAR with Maven; you do not need Java, Maven, Gradle, or Node installed on the host to build and run the app. Docker Compose reads configuration from `.env` in the repository root. If it does not exist, copy `.env.example` to `.env`, then replace the placeholder database password with a generated secret:

```bash
cp .env.example .env
openssl rand -base64 32
```

Put the generated value in `POSTGRES_PASSWORD` in `.env`. Keep any existing `TUNNEL_TOKEN` line; it is needed only when starting the Cloudflare Tunnel profile. Never commit or share `.env`.

Build the application image without starting containers:

```bash
docker compose build app
```

Start the app and PostgreSQL for local use:

```bash
docker compose up -d
```

To build and start the app, database, and Cloudflare Tunnel together, use:

```bash
docker compose --profile tunnel up --build -d
```

The app is available locally at `http://localhost:8080/`; Market Day is at `http://localhost:8080/market`. Check service status and logs with:

```bash
docker compose --profile tunnel ps
docker compose logs --tail=100 app db cloudflared
```

Stop the containers while preserving the database with `docker compose --profile tunnel down`. PostgreSQL data lives in the `postgres_data` Docker volume. **Do not use `docker compose down -v` unless you intend to delete all database data.**

Accounts created at `/register` need admin approval before sign-in. The app, authentication sessions, and grocery data persist in PostgreSQL across container restarts.

### Map positioning unit tests

The isolated map-positioning tests use Node.js 22 and have no npm dependencies. Install Node 22 with a version manager such as `nvm`, then run from the repository root:

```bash
npm run test:map
```

These tests validate the positioning calculations and generated CSS values without launching Spring Boot or a browser. They do not check the browser's final pixel rendering.

## Admin account and approvals

The app container includes an admin command mode. It uses the existing Maven-built application and database configuration; no separate Gradle installation is needed. Run commands from this repository directory.

To promote your existing account to admin (this also approves it), replace the email with the one you registered:

```bash
docker compose exec app java -jar /app/app.jar \
  --spring.main.web-application-type=none \
  --figo.admin-command=grant-admin \
  --figo.email=you@example.com
```

To create a separate approved admin account, run:

```bash
docker compose exec -it app java -jar /app/app.jar \
  --spring.main.web-application-type=none \
  --figo.admin-command=create-admin \
  --figo.email=admin@example.com
```

The create command prompts for a password without putting it in shell history. Use at least 12 characters. Then sign in at `/login` and visit `/admin` to approve pending accounts. After promoting an existing account, sign out and sign back in for the admin role to take effect.

## Market Day

- Create multiple stores and switch between them; each has a separate layout, category set, and lists.
- Use the shared item catalog; every user's additions are available to everyone. Add an item on separate store placements when you want different aisle or category data.
- Bulk import names into the shared catalog by pasting one item per line. Blank lines and names already in the catalog are skipped; categories are not imported.
- Position aisles on an 8-column by 6-row grid, with a length and horizontal/vertical direction.
- Assign an item to an aisle in a store and optionally record a percentage down the aisle. Item names are unique across the shared catalog, ignoring case.
- Click an item in the catalog to assign or change its aisle placement; aisle choices show the aisle name and its categories.
- Delete items that are not used on any shopping list. Deleting an aisle keeps its item placements and clears their aisle assignments.
- Create store-specific shopping lists, add items and quantities, and check them off. List display groups items by aisle and location.

Store data uses `/api/stores/{storeId}/...` for categories, aisles, items, lists, and list entries. `/api/stores` lists or creates stores owned by the signed-in user.

## Publish with Cloudflare Tunnel

Use the existing remotely-managed tunnel and route the desired public hostname to `http://app:8080`.

1. In the Cloudflare dashboard, open **Networking → Tunnels**, select the tunnel, and add a **Published application** route such as `figo.li` or `hello.figo.li` with service URL `http://app:8080`.
2. Configure a Cloudflare Access self-hosted application and an Allow policy for invited emails on every public hostname you expose. This restricts who can request an app account; admins approve registrations inside Market Day.
3. Ensure `.env` has `TUNNEL_TOKEN` and `POSTGRES_PASSWORD`, then start the tunnel profile:

   ```bash
   docker compose --profile tunnel up --build -d
   docker compose --profile tunnel ps
   docker compose logs -f cloudflared
   ```

The Cloudflare Tunnel container reaches the app over the private Compose network. No router port forwarding is needed. To stop the app and tunnel while retaining database data, run `docker compose down`. The machine must stay on and connected for the app to remain available.
