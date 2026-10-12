# figo.li

Small Spring Boot apps under one home page, backed by PostgreSQL. **Market Day** lives at `/market` and provides multiple stores per account, each with its own aisle layout, categories, catalog, and shopping lists. Users register with email and password, then an admin approves their account. Passwords are stored as BCrypt hashes. Signed-in sessions are stored in PostgreSQL and expire after 90 days of inactivity; the browser cookie also lasts up to 90 days. The app and Cloudflare Tunnel share a private Compose network; the app's host port is bound to `127.0.0.1` for local checks.

## Start locally

Add a database password to your private `.env` file. Keep any existing `TUNNEL_TOKEN` line. If `POSTGRES_PASSWORD` is not already present, add it:

```bash
openssl rand -base64 32
```

Add `POSTGRES_PASSWORD=<generated-value>` to `.env` using your preferred text editor. Do not commit `.env` or share it. Then start the app and database:

```bash
docker compose up --build -d
```

Open `http://localhost:8080/` for the landing page or `http://localhost:8080/market` for Market Day. Accounts created at `/register` need admin approval before sign-in. PostgreSQL data persists in the `postgres_data` Docker volume; `docker compose down -v` deletes it.

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

- Create multiple stores and switch between them; each has a separate layout, category set, item catalog, and lists.
- Position aisles on an 8-column by 6-row grid, with a length and horizontal/vertical direction.
- Assign catalog items to an aisle and optionally record a percentage down the aisle.
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
