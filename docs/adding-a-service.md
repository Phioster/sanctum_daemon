# Adding a service

Every place a new `ServiceType` has to touch, in order. Steps 1–3 are enough for a
service that only shows a status card; 4–6 add a screen and dashboard cards.

1. **`model/ServiceConfig.kt`**. Add the enum entry (label + brand accent colour). If it
   authenticates with an `X-Api-Key` header, add it to `usesApiKeyHeader`.

2. **Logo**. Drop `svc_<name>.png` into `res/drawable-nodpi/` (source:
   [homarr-labs/dashboard-icons](https://github.com/homarr-labs/dashboard-icons)).

3. **`service/ServiceRegistry.kt`**. Add a `ServiceSpec`: logo, plus any quick actions.
   `ServiceRegistryTest` fails if a type has no entry, so this is not optional.
   Everything that reads a logo or an action list, app, status widget, calendar widget,
   dashboard quick buttons, quick-action widget, picks it up from here.

4. **`net/<Name>Api.kt`**, the Retrofit interface and the calls. Shared pieces
   (`apiFor`, `okClient`, `apiKeyHeader`, `basicHeader`, `okOr`, the `js*` JSON helpers)
   live in `net/Http.kt`.
   **Anything that changes state on the server must be wrapped in `destructive("…") { }`**
   (see `net/Destructive.kt`). That is what safe mode blocks.

5. **`net/Status.kt`**. Extend `fetchStatus` so the service card shows its stats, and
   `serviceSearch` if it can answer the global search.

6. **`ui/<name>/<Name>Screen.kt`**, the detail screen, plus the `when` branches in
   `ui/services/ServiceScreens.kt` (detail route + add form) and, for dashboard cards,
   new `CardType` entries in `model/Dashboard.kt` and their branch in
   `ui/dashboard/DashCardView.kt`.

7. **Tests**, a `net/<Name>ApiTest.kt` with a recorded response, following
   `ArrApiTest`: parsing, the auth headers, and one guarded call staying off the network.

## Watch out

- A new enum entry breaks every non-exhaustive `when` over `ServiceType`, and Kotlin 2.1
  makes that a hard error. Build early; the registry test catches the common case first.
- Keep new DataStore keys and Keystore aliases as they are once shipped, renaming one
  orphans existing installs' data.
