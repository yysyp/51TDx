# 51TDx

An Android self-motivation app built around a single closed loop:

> **action → stars → reward → motivation for the next action**

Every module feeds the same star economy, so completing a pomodoro, checking
in on a plan, and finishing a todo all move the same progress bar. Nothing in
the app is a dead end.

## Features

Detailed behaviour, psychology rationale, and known limitations are documented
in [`docs/PRODUCT.md`](docs/PRODUCT.md).

| Module | What it does |
| --- | --- |
| 抽奖 Lottery | Weighted random draw over a user-managed prize pool. Add / edit / delete prizes, view draw history, jackpot detection. |
| 任务计划 Plans | Repeating plans with a target count, daily check-in (once per day), star progress bar, completed-plan archive. |
| TODO Todos | Priority-ordered to-dos, one-tap completion with strikethrough, completed list, star reward per item. |
| 吃什么 Food | Random food picker to break decision fatigue, with a user-managed food pool. Rolls in ~0.8s — the decision is the point, so the animation stays short. |
| 复习 Review | Self-test spaced repetition (SM-2): hide → recall → reveal → grade. Three recall/difficulty levels drive per-card ease factors. |
| 宠物 Pet | A companion that gets hungry and acts cute. Feeding, playing and dressing it spend stars; hunger and mood bars create the pull to come back. |

## Psychological design

The positive-feedback behaviour is deliberate, not decorative. Each mechanism
is labelled in `core/RewardEngine.kt`:

- **Variable-ratio reinforcement** (Skinner) — the lottery is weighted-random,
  so reward frequency is unpredictable. This resists extinction far better
  than a fixed schedule. The decelerating roll animation preserves the
  suspense instead of revealing the result instantly.
- **Goal-gradient effect** (Kivetz) — progress bars are eased with a power
  curve so the bar visibly accelerates near the target, which is when
  motivation peaks.
- **Endowed-progress effect** (Nunes & Drèze) — the daily goal starts from a
  non-zero baseline, and a first-run gift grants 10 stars, so the user
  completes a full reward cycle before earning anything.
- **Loss aversion / streak continuity** (Kahneman) — daily streaks are
  tracked and displayed; breaking one is avoided because it reads as a loss.
- **Self-determination** (Deci & Ryan) — all copy targets competence and
  autonomy. The app never guilt-trips the user; there is no failure state.
- **Zeigarnik effect** — unfinished plans stay visible on the home screen,
  keeping mild attentional tension until the task is closed.
- **Low-friction smallest action** — completing a todo is a single tap. Friction
  on the smallest achievable unit is the main cause of stalled habits.

Star costs are calibrated so roughly three check-ins fund one draw. A reward
that is hard to reach stops behaving as a reward.

## Star economy

| Action | Stars |
| --- | --- |
| Plan check-in | +3 |
| Plan completed | +23 (20 bonus + 3) |
| Todo completed | +5 (configurable per item) |
| Review item recorded | +2 / +3 / +4 (by difficulty) |
| Review completed | +1 to +6 (difficulty x recall quality), ~15% chance of doubling |
| First-run gift | +10 |

Stars are also *spent*: pet care costs 5-12 stars per interaction and
accessories cost 15-70 stars once. Without a sink, a currency inflates and
stops motivating — the pet exists to make those stars go somewhere durable.

All star movements are written to an append-only `star_ledger` table, so the
balance is always derivable and auditable. Spending is refused when the
balance is insufficient rather than going negative.

## Tech stack

- Kotlin 2.0.20, Jetpack Compose (BOM 2024.09.03), Material 3
- Room 2.6.1 with KSP, single database `ran.db`
- MVVM: `RanViewModel` + `RanRepository`, StateFlow throughout
- Coroutines for the draw animation and the day-boundary tickers
- minSdk 26, target/compile SDK 34

## Architecture

```
com.ran.app
├── core/          RewardEngine (star economy, easing, copy),
│                  SpacedRepetition (SM-2 schedule), PetEngine, TimeUtils,
│                  DailyCycle (day boundary + same-day check-in filter)
├── data/
│   ├── local/     Room entities, DAOs, RanDatabase
│   └── RanRepository   single entry point for persistence + reward accounting
├── ui/
│   ├── components/  StarProgressBar, StarBadge, StreakBadge, dialogs, banner,
│   │                PetAvatar (Canvas-drawn expressions)
│   ├── screens/     Home, Review, Lottery, Plans, Todos, Pet, Food
│   ├── RanViewModel
│   └── RanApp.kt    Scaffold + bottom navigation
└── MainActivity.kt
```

Layers depend inward: UI → ViewModel → Repository → Room. The repository is
the only place that mutates the star balance, which keeps reward accounting in
one auditable location.

## Build

Prerequisites: JDK 17+, Android SDK with platform 34 and build-tools 34.0.0.

Set `local.properties` to point at your SDK:

```properties
sdk.dir=C\:\\Users\\<you>\\AppData\\Local\\Android\\Sdk
```

Then either use the scripts, which run unit tests and produce both variants:

```bash
./build.bat          # Windows
./build.sh           # macOS / Linux / Git Bash
```

or call Gradle directly:

```bash
./gradlew testDebugUnitTest assembleDebug     # debug APK
./gradlew installDebug                          # build and install on a device
./gradlew assembleRelease                       # release APK
```

Output:

- `app/build/outputs/apk/debug/app-debug.apk` (~17 MB, package `com.ran.app.debug`)
- `app/build/outputs/apk/release/app-release.apk` (~11 MB, package `com.ran.app`)

`run.bat` / `run.sh` install the debug build onto a connected device and launch
it.

> The release build is signed with the debug keystore so it is installable out
> of the box. Configure a real signing config before distributing.

### Gradle installation

The scripts prefer a local Gradle install over the wrapper, because the
wrapper must download a ~130 MB distribution on first use and that download is
prone to timing out on slower networks. To use one:

```bash
export GRADLE_HOME=/path/to/gradle-8.9     # build.sh
set GRADLE_HOME=D:\app\gradle-8.9           # build.bat
```

Without `GRADLE_HOME` they fall back to `./gradlew`.

## Tests

**54 tests, all green** (run for both the debug and release variants):

- `RewardEngineTest.kt` (16) — the reward economy, the part that silently
  corrupts the whole feedback loop if it drifts:
  - weighted random selection, including zero/negative weights and an empty pool
  - goal-gradient easing monotonicity and bounds
  - the endowed-progress daily goal never regressing
  - level thresholds and `levelFor` being mutual inverses
- `SpacedRepetitionTest.kt` (11) — SM-2 scheduling: the seeded 1/3-day start,
  earned, ease bounds, strict interval growth even at minimum ease, mastery
  defined by interval length, harder material graduating later, failure still
  paying something, and the observed bonus rate matching the advertised 15%.
- `PetEngineTest.kt` (15) — decay rates (hunger ~20h, mood ~40h), no
  double-charging elapsed time, negative clock deltas not restoring state,
  expression precedence (eating > absence > hunger > mood), dialogue variety,
  catalogue integrity, and that twice-daily care actually keeps it content.
- `TimeUtilsTest.kt` (4) — stable sortable day keys and signed day gaps.
- `DailyCycleTest.kt` (8) — the day boundary: a yesterday check-in must not
  disable today's button, same-day records are collected and de-duplicated, day
  keys and start-of-day timestamps are mutual inverses, and "now" always falls
  inside today's window.

The day-boundary tests guard a real bug found after release: "today" was read
once and cached, while the flows behind the UI only emit when the database
changes. Nothing changes in the database at midnight, so yesterday's date
survived the whole next day and every checked-in plan stayed disabled until
some unrelated write refreshed it. The day key is now a stream
(`DailyCycle` + `RanRepository.todayKey`) that everything "today" derives from.

The level-inverse test guards a real bug that shipped during development: a
closed-form `pow` inverse truncates to a level one too low at every boundary, which made the
level bar jump to ~100% immediately after each level-up. Thresholds are now
precomputed into a table and the level is a binary search, so the two
directions agree by construction.

Run with:

```bash
./gradlew testDebugUnitTest
```

## Design notes

The app opens on a gradient level card rather than a task list. The card
answers "where am I" before "what can I do", which keeps the user oriented
instead of overwhelmed. Counts and the daily goal follow, then open plans.