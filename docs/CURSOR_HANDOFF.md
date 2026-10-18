# Cursor session handoff (17 Oct 2026, evening IST)

For the next Claude Code session on `C:\Users\Harsh\Desktop\ghack\Graph-hackathon`, branch `homeos-build`. Product name is **HomeOS**, not Vagus. Hackathon deadline is **18 Oct 2026, 23:59 IST**.

This file is a status report. It is not a new product spec. `CONTEXT.md` and `README.md` are still the project docs. Do not commit ring passwords, Wi-Fi passwords, `.env`, or bugreports.

## What was already working before this session

Claude Code session `e5d3365c-43c2-4d3b-90b6-190001f7c075` (auto-named `vagus-graph-hacks-build-plan`) built HomeOS and then died on a stretch goal: live BLE to the Gabit ring. Its plan is `C:\Users\Harsh\.claude\plans\so-the-thing-is-cheeky-clock.md`. The last Claude commit on `homeos-build` is `cfb6afe`: the watch holds and binds to Wi-Fi so it stays connected with the screen off.

Already true at the start of this Cursor session:

- FalkorDB agents, 59 Cypher queries, UDFs, MCP, dashboard, ablation, Scientist on public PMData person `p01` (150 nights).
- Galaxy Watch **SM-R865F** (`freshus`) streams live HR. A recorded session showed about 103 bpm. Alias `harsh`.
- Phone app **HomeOS Bridge** (`com.homeos.bridge`) reads Gabit via Health Connect and POSTs `/api/health/<alias>`.
- ESP32 room node is flashed. AC and fan in the demo are virtual devices.
- There is **no** `bridges/ring` bleak client in this repo. Direct BLE notes live in `CONTEXT.md` and in `C:\Users\Harsh\Desktop\ringarose\docs\GABIT_RING.md`.

## Devices and network right now

Phone: OnePlus **CPH2649**, Android 16, adb serial **`de22830a`** over USB. This is the stable connection. Wireless debugging ports expire and should not be trusted from this note.

Watch: SM-R865F. It was online earlier tonight at `192.168.215.52:40663`, then the laptop left that network. `apps/watch/local.properties` still says `ws://192.168.215.167:3001/ws/device` and alias `harsh`. If the watch shows offline, that URL is stale. The laptop Wi-Fi address while this note was written was **`10.129.129.167`**. The phone was **`10.129.129.165`** on `wlan2`.

Backend was restarted and was listening on `http://0.0.0.0:3001` with `graph=on` and Sarvam voice. People it knows: `harsh`, `shashwath`, `naomi`, `p01`. `adb reverse tcp:3001 tcp:3001` was set, so the phone can also use `http://127.0.0.1:3001` while USB is plugged in. Prefer the LAN address so background sync works without USB.

`apps/phone/local.properties` now has:

- `homeos.url=http://10.129.129.167:3001`
- `homeos.alias=naomi`

The installed Bridge app was updated on the phone. Its on-screen **Person** field was saved as `naomi`. **Server** was saved as `http://10.129.129.167:3001`.

## What was tried and did not work

### 1. morluto/rea

Not useful here. REA is a generic reverse-engineering MCP (JADX, Ghidra, ADB, TShark on an existing capture). It does not speak J-Style 2301 and its packet tool does not capture traffic. It was not installed.

`saksham2001/Smart-Ring-Protocol` is a different ring (`0x56FF`, 20-byte frames). This Gabit ring is J-Style 2301 (`FFF0` / `FFF6` / `FFF7`, 16-byte frames).

### 2. Live BLE / HCI snoop for the ring password

The ring password (`0x3C` authenticate) is still unknown. Do not brute-force it. Do not send reset, password-change (`0x7B`), or delete-history commands.

`ringarose` already connected to **this** ring on 4 Oct 2026 (`Gabit Smart Ring 5F73`, `C5:1C:C2:34:5F:73`). Subscribe plus MTU 247 produced no health notifications. SDK reads that answered: version, MAC, name. SDK reads that stayed silent without authenticate: battery, time, steps, sleep, HR, HRV, temperature, SpO2, live steps (`0x09`), live measurement (`0x28`).

Tonight, with the ring worn and Developer options → Bluetooth HCI snoop log on:

- `settings get secure bluetooth_hci_log` was `1`. Bluetooth was restarted so Gabit would reconnect while logging. Gabit (`com.gabitrn`) held GATT on `…:5F:73`.
- `adb bugreportz` completed over USB. Three bugreports were pulled. The binary snoop was **not** in them. OnePlus writes it to `/data/misc/bluetooth/cached_hci/btsnoop_hci_20261017_204227.cfa`. Shell cannot read that directory (`Permission denied`). Copies under `logs/bthci/*.cfa` inside the bugreport were **16 bytes** (header only).
- The text log `bluetooth_20261017_204228_05175.log` shows `FFF6` value handle **`0x000d`** and ATT writes of length 19 (a 16-byte frame plus opcode and handle). It does **not** print the payload. `DumpSnoozLogToFile` said `btsnoop log is enabled, skip dumping btsnooz log`.
- Turning `bluetooth_hci_log` to `0` and force-stopping `com.android.bluetooth` did not change that. The new Bluetooth process still skipped the in-memory btsnooz dump. `persist.vendor.bluetooth.btsnooprootpath` cannot be set without root. There is no `su`.
- The snoop setting was turned back to `1`. Local bugreport zips were deleted. Do not ask the user for another bugreport unless the approach changes. This OxygenOS build will not hand the HCI bytes to adb.

### 3. Gabit period data

Gabit’s own women’s-health screen (cycle, symptoms, ovulation, pregnancy) does **not** show up in Health Connect. Sahha’s public Gabit integration lists sleep, heart rate, resting heart rate, VO2max, steps, and calories. It does not list menstruation.

HomeOS can now read Health Connect menstruation **if some app writes it**. A 30-day sync after the user logged period details in Gabit returned:

- person `naomi`
- `granted_permissions: 10`
- `errors: {}`
- `menstruation: []`
- `menstruation_flow: []`

So the read permission works and Gabit wrote nothing. The user said to leave this until they can supply the data later. Do not scrape the Gabit UI, do not call a private Gabit API, and do not go back to BLE for this.

The manual **Period** chip on the phone still works. It sets a private `menstrual` condition for about 4 days. The home graph only receives the catalog shift: room temperature minimum 25 °C, priority 2. The reason stays in `body:<alias>`.

## What did work

### Health Connect sync on the new Wi-Fi

The Bridge was still aimed at `192.168.215.167`. From the phone that address was unreachable (an earlier attempt even tried to leave via mobile data `192.0.0.2`). The phone can reach `http://10.129.129.167:3001` and, with `adb reverse`, `http://127.0.0.1:3001`. Both returned HTTP 200.

After the server field was changed and **Sync now** was tapped, Gabit data arrived. Counts moved roughly from 38 HR / 7 HRV / 14 step records to about **48 HR / 8 HRV / 35 step records**, origin `com.gabitrn`. **0 nights.** The server stored an `rmssd` baseline. A 7-minute Gabit walk increased those counts. It did **not** create a dashboard feed line.

Why the dashboard looked empty: `/api/health` only posts a feed event when a **new sleep night** is ingested. Exercise sessions are stored on the batch and only affect a later night’s `workoutEveningMin`. Recent HR (last 15 minutes) is pushed into the in-memory buffer. The dashboard chart only keeps about the last 6 minutes of websocket `signal` events, and only if the page was open when they arrived. To make a walk visible, the user taps **Workout** under **How are you?** on the Bridge. That opens a recovery episode for that alias.

### Naomi

`HOMEOS_PEOPLE` was not in `.env`, so the process defaulted to `harsh,shashwath`. It is now:

```text
HOMEOS_PEOPLE=harsh,shashwath,naomi
HOMEOS_DATASET_PEOPLE=p01
```

The backend was restarted. Startup log included `naomi is in the bedroom` and the websocket alias list `harsh|shashwath|naomi|p01`. `shashwath` still has the earlier ring samples. New Bridge syncs go to **`naomi`**. Do not copy one person’s Health Connect batch into another alias.

Cast that matches the hardware:

| Person | Alias | Source |
|---|---|---|
| Harshendra, watch | `harsh` | Live HR, about 1 Hz. Watch `local.properties` may still point at the old LAN IP. |
| Naomi, ring + phone | `naomi` | Gabit → Health Connect → Bridge. Female profile. Period export not working yet. |
| Earlier ring samples | `shashwath` | Same phone, before the alias was changed. Leave this graph alone. |
| Public dataset | `p01` | 150 real nights. This is the Scientist demo (PageRank, betweenness, label propagation, SPpaths). Not in the room. |

## Code changes in this session (not committed)

- `apps/phone/app/src/main/AndroidManifest.xml`: `READ_MENSTRUATION`.
- `apps/phone/app/src/main/java/com/homeos/bridge/HealthSync.kt`: reads `MenstruationPeriodRecord` and `MenstruationFlowRecord`. The sync summary now includes period and flow counts. Read failures stay in `errors` instead of looking like “no data”.
- `backend/src/ingest/health.ts`: if a period window is current, calls `setCondition(alias, "menstrual", …)` from Health Connect. A very short Gabit window is treated as 4 days, same as the Period chip. Feed text says the period is private and the home only learns the warmer range.
- `apps/phone/local.properties`: URL and alias above. Gitignored.
- `.env`: `HOMEOS_PEOPLE`. Gitignored. Do not commit it.
- `apps/phone/gradle/wrapper/gradle-wrapper.properties`: `networkTimeout` raised from 10000 to 120000 because the wrapper download timed out. The install then succeeded **offline** with `GRADLE_USER_HOME=%USERPROFILE%\.gradle` (the agent shell had pointed `GRADLE_USER_HOME` at a sandbox cache). APK installed on `CPH2649` with `gradlew installDebug`.

The phone app on the device includes the menstruation reader. The backend process that was started after the `.env` edit includes Naomi. If you start a new backend, use the same `.env`.

## What the user should still do, and what you should not redo

User, when they are ready, for a real period sample: Health Connect → App permissions → Gabit → allow Menstruation write, save the period again in Gabit, then Bridge **Sync now** as `naomi`. Until `period records` is greater than 0, there is nothing to ingest.

User, for the two-person demo: phone **Sick** as Naomi, watch **Workout done** as Harshendra, dashboard `http://localhost:3001`. Sleep with the ring on is the only way to get a real night for Naomi. `p01` already covers the algorithm shot.

Do not:

- Install REA or decompile `com.gabitrn`.
- Run another HCI bugreport on this OnePlus expecting the password bytes.
- Invent health samples for Naomi or Harshendra. The project rule is that demo numbers come from devices or from the labelled PMData import.
- Force-stop Gabit while expecting Health Connect to update. The ring accepts one BLE connection. Gabit has to be the one connected if the official export is the path.

## Useful commands

```text
adb -s de22830a shell am broadcast -n com.homeos.bridge/.SyncReceiver --el days 2
```

Phone and laptop must be on the same LAN, or `adb reverse tcp:3001 tcp:3001` and the Bridge server set to `http://127.0.0.1:3001`.

Backend, from `backend/`:

```text
npx tsx src/index.ts
```

`tsx` here is not watch mode. `.env` and `health.ts` are picked up only on process start.
