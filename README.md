# AeroSafe

AeroSafe is a Java-based desktop application focused on monitoring, predicting, and mitigating air pollution. It provides tailored experiences for everyday users, researchers, and government officials—supporting air quality visualization, next-day AQI predictions, alerts with safety guidance, community reports, and verified escalation to authorities.

## Key Features
- **User authentication**: Sign-up / login, profile management.
- **Location-aware AQI**: Select a location to view the current Air Quality Index (AQI) and pollutant breakdown.
- **Interactive globe view**: Map/globe UI to inspect AQI for selected locations and surface key metrics at-a-glance.
- **Historical trends & charts**: Visualize time series of AQI and pollutants with graphs.
- **Next-day prediction**: Predict air quality for the next day using the previous 7 days of data.
- **Alerts & guidance**: Level-based alerting plus safety and caution messages.
- **Researcher role**: Researchers can submit additional data or calibration info to improve precision and can export/download necessary datasets.
- **Government/admin role**: Officials can receive verified citizen reports about suspected man-made pollution, review them through a validation workflow, and trigger actions/notifications.
- **Incident reporting & verification**: Users submit concerns; the system routes through checks to reduce false reports before notifying the responsible official.

## Architecture Overview
- **Desktop app**: Java (e.g., JavaFX or Swing for UI).
- **Data layer**: Pluggable data source. Start with SQLite for local/offline prototyping; plan API-based ingestion for live AQI.
- **Services**:
  - **AQI ingestion**: Fetch from public APIs (e.g., OpenAQ) or load from SQLite/local cache when offline.
  - **Prediction engine**: 7-day window model (e.g., simple regression/time-series baseline) to generate next-day AQI.
  - **Alerting**: Threshold-driven messaging mapped to AQI categories (Good, Moderate, Unhealthy…).
  - **Reporting workflow**: Citizen reports → validation steps → admin review → status updates.
  - **Researcher portal**: Data submission and export capabilities.
- **Visualization**: Map/globe widget + charts (line/area/bar) for AQI and pollutants over time.

## Suggested Tech Stack
- **Language**: Java 17+
- **UI**: JavaFX (preferred) or Swing
- **Data**: SQLite (initial), with an interface to swap in live API sources; consider Hibernate/JPA or direct JDBC.
- **HTTP**: `HttpClient` (Java 11+)
- **JSON**: Jackson or Gson
- **Charts**: JavaFX Charts or third-party charting libs
- **Mapping/Globe**: JavaFX 3D or embedded webview with a JS globe (Cesium/Leaflet/Mapbox via WebView)

## Data & Roles
- **Entities**: User (roles: user, researcher, admin), Location, AQIRecord (timestamp, pollutants), Prediction, Alert, Report (citizen concerns), VerificationStep.
- **SQLite schema (starter)**:
  - `users(id, name, email, password_hash, role, created_at)`
  - `locations(id, name, lat, lon)`
  - `aqi_records(id, location_id, ts, aqi, pm25, pm10, no2, so2, co, o3)`
  - `predictions(id, location_id, target_date, predicted_aqi, model_version)`
  - `alerts(id, location_id, level, message, created_at)`
  - `reports(id, user_id, location_id, description, status, created_at)`
  - `verification_steps(id, report_id, step, result, reviewer, created_at)`
- **Pluggable data source**:
  - Interface (e.g., `AirQualityProvider`) with implementations for `SQLiteAirQualityProvider` and `ApiAirQualityProvider`.
  - Cache strategy to fall back to local data when APIs fail or offline.

## User Flows
- **End user**: Sign in → pick location → view AQI & charts → see alerts/safety tips → view next-day prediction → submit report if needed.
- **Researcher**: Sign in → upload supplemental measurements/calibration → view/export datasets → refine prediction parameters (future).
- **Admin (gov official)**: Review incoming reports → run verification steps → update status → trigger notifications/actions.

## Prediction (initial baseline)
- Use last 7 days AQI for a simple moving average or linear regression to forecast next-day AQI.
- Persist predictions; display alongside confidence/last-updated metadata.
- Keep model interface-driven to swap in more advanced models later.

## Alerts & Safety Messaging
- Map AQI ranges to alert levels and predefined safety/caution text (e.g., EPA-like categories).
- Surface both banner alerts and contextual tips in the UI; optionally notify on significant changes.

## Setup (proposed)
1. Install JDK 17+.
2. (Optional) Install SQLite CLI for inspection.
3. Create and migrate the local DB (SQL scripts under `db/migrations/`).
4. Configure API keys (if using live providers) via `.env` or `config.properties`.
5. Run the desktop app:
   ```bash
   ./gradlew run
   ```
   or
   ```bash
   mvn clean install
   java -jar build/libs/aerosafe.jar
   ```

## Project Structure (proposed)
```
/src/main/java/com/aerosafe/
  ui/            # JavaFX views, controllers
  services/      # AQI ingestion, prediction, alerts, reporting workflows
  data/          # Repositories, DAOs, providers (SQLite/API)
  models/        # Entities and DTOs
  util/          # Helpers (config, logging, validation)
resources/
  application.conf / config.properties
  db/migrations/  # SQL schema and seed data
```

## Roadmap
- MVP: Auth, location selection, live AQI fetch (or SQLite sample data), charts, predictions, alerting, incident reporting flow.
- Phase 2: Researcher data ingestion/export, richer prediction models, improved verification workflow.
- Phase 3: Mobile/Android client reusing services via shared modules or API.
- Phase 4: Advanced mapping/globe UX, notifications, and admin analytics.

## Notes on SQLite vs APIs
- SQLite works well for prototyping and offline caching. For production-grade AQI, integrate an API provider (e.g., OpenAQ, WAQI).
- Implement a provider interface so you can start with SQLite data, then switch to live APIs without major refactors.

## Contributing
1. Fork & branch.
2. Add tests for new features.
3. Open a PR with a clear description and screenshots/GIFs for UI changes.

