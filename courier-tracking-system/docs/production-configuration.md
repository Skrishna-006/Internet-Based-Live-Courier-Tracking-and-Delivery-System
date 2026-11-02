# Production Configuration Guide

This guide documents the configuration requirements, security expectations, and deployment instructions for running the **Internet-Based Live Courier Tracking and Delivery System** backend in production.

---

## 1. Overview & Architecture

The application provides a dedicated Spring Boot profile: `application-prod.yml`.
When this profile is activated (`prod`), the backend:
- **Mandates external secrets:** Database credentials, JWT signing secret, and allowed CORS origins must be provided via environment variables. Fallback development credentials are intentionally absent to prevent accidental exposure or insecure defaults.
- **Enforces SSL for PostgreSQL:** Defaults `sslmode` to `require`.
- **Disables test OTP leakage:** `app.otp.expose-in-response` is strictly set to `false`.
- **Configures connection pooling:** Uses HikariCP with sensible production defaults.

---

## 2. Environment Variables Reference

### 2.1 Required Environment Variables

The application **will fail to start** if any of these variables are missing when running under `SPRING_PROFILES_ACTIVE=prod`.

| Variable | Description | Example Format |
| :--- | :--- | :--- |
| `DB_URL` | PostgreSQL JDBC connection URL | `jdbc:postgresql://postgres.example.com:5432/courier_db` |
| `DB_USERNAME` | PostgreSQL database user | `courier_admin` |
| `DB_PASSWORD` | PostgreSQL database password | `StrongSecretPassphrase_2026!` |
| `JWT_SECRET` | HMAC-SHA secret for signing JWTs (must be at least 256 bits / 32 characters) | Base64 or 32+ character high-entropy string |
| `CORS_ALLOWED_ORIGINS` | Comma-separated list of allowed frontend origins | `https://courier.example.com,https://admin.courier.example.com` |

> [!CAUTION]
> **Zero Fallback Security Guarantee:** In the `prod` profile, no hardcoded defaults exist for `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, or `CORS_ALLOWED_ORIGINS`. If any of these are unset, Spring context initialization halts immediately with an `IllegalArgumentException`.

---

### 2.2 Optional Environment Variables with Production Defaults

| Variable | Default Value | Description |
| :--- | :--- | :--- |
| `PORT` or `SERVER_PORT` | `8080` | Port on which the Spring Boot web server listens (Render and Heroku inject `PORT`). |
| `DB_SSL_MODE` | `require` | PostgreSQL SSL mode (`require`, `verify-ca`, `verify-full`, or `disable`). |
| `DB_POOL_MAX_SIZE` | `10` | Maximum number of connections in the HikariCP connection pool. |
| `DB_POOL_MIN_IDLE` | `2` | Minimum number of idle connections maintained by HikariCP. |
| `DB_DRIVER_CLASS_NAME`| `org.postgresql.Driver` | JDBC driver class name. |
| `JWT_EXPIRATION_MS` | `86400000` (24h) | JWT validity duration in milliseconds. |
| `APP_PRICING_BASE_FEE` | `50.00` | Base delivery fee in INR. |
| `APP_PRICING_PER_KM_RATE`| `8.50` | Per-kilometer delivery rate in INR. |
| `APP_PRICING_STANDARD_MULTIPLIER` | `1.0` | Pricing multiplier for standard delivery. |
| `APP_PRICING_EXPRESS_MULTIPLIER` | `1.5` | Pricing multiplier for express delivery. |

---

## 3. Secret Generation

### Generating a Secure JWT Secret
Do not use guessable phrases. Generate a cryptographically secure 256-bit (or 512-bit) secret using one of the following methods:

**Using OpenSSL:**
```bash
# Generate 32 cryptographically random bytes (Base64-encoded)
openssl rand -base64 32

# Or generate 64 hex characters (256-bit)
openssl rand -hex 32
```

**Using PowerShell (Windows):**
```powershell
$bytes = New-Object byte[] 32
[System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
[Convert]::ToBase64String($bytes)
```

---

## 4. Activating the Production Profile

Activate the production profile by setting the Spring profile environment variable:

```bash
export SPRING_PROFILES_ACTIVE=prod
```

Or pass it as a JVM argument / command line switch:

```bash
java -Dspring.profiles.active=prod -jar target/courier-tracking-backend-0.0.1-SNAPSHOT.jar
```
or
```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=prod
```

---

## 5. Deployment Platform Guides

### 5.1 Docker / Container Deployment

Pass the environment variables via `-e` or an orchestrator secret store:

```bash
docker run -d \
  -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e DB_URL="jdbc:postgresql://db.cloud-provider.com:5432/courier_db" \
  -e DB_USERNAME="prod_user" \
  -e DB_PASSWORD="SuperSecretPassword" \
  -e JWT_SECRET="v9f83nF982nfk...YOUR_GENERATED_SECRET..." \
  -e CORS_ALLOWED_ORIGINS="https://courier.example.com" \
  courier-tracking-backend:latest
```

### 5.2 Render / Railway

1. **Environment Variables Panel:** In your service dashboard, configure:
   - `SPRING_PROFILES_ACTIVE` = `prod`
   - `DB_URL` = `jdbc:postgresql://${{Postgres.HOST}}:${{Postgres.PORT}}/${{Postgres.DATABASE}}`
   - `DB_USERNAME` = `${{Postgres.USER}}`
   - `DB_PASSWORD` = `${{Postgres.PASSWORD}}`
   - `JWT_SECRET` = `<Paste generated 256-bit secret>`
   - `CORS_ALLOWED_ORIGINS` = `https://your-frontend.vercel.app`
2. **Port Binding:** Render automatically sets `PORT`, which `application-prod.yml` honors via `${PORT:${SERVER_PORT:8080}}`.

### 5.3 AWS Elastic Beanstalk / ECS

Set environment properties in your task definition or configuration settings:
- Map secrets from **AWS Secrets Manager** or **AWS Systems Manager (SSM) Parameter Store** to container environment variables (`DB_URL`, `DB_PASSWORD`, `JWT_SECRET`).
- Set `SPRING_PROFILES_ACTIVE` to `prod`.

---

## 6. Pre-Deployment Validation

To verify that the configuration properly resolves without connecting to external production databases, you can run the configuration test suite:

```bash
cd backend
mvn test -Dtest=ProductionProfileConfigTests
```

This executes:
1. Context loading verification under `prod` profile.
2. Negative assertion checks confirming startup fails if `JWT_SECRET`, `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, or `CORS_ALLOWED_ORIGINS` are omitted.

---

## 7. Security Best Practices

> [!WARNING]
> **Never commit secrets:** Never commit `.env`, `application-prod.yml` populated with real secrets, or credentials to any Git repository.
> Always inject secrets at runtime through environment variables or secure secret managers.
