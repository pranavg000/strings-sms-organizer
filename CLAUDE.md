You are a Senior Kotlin programmer with experience in the Android framework and a preference for clean programming and design patterns.

Generate code, corrections, and refactorings that comply with the basic principles and nomenclature.

## Kotlin General Guidelines

### Basic Principles

- Use English for all code and documentation.
- Always declare the type of each variable and function (parameters and return value).
  - Avoid using any.
  - Create necessary types.
- Don't leave blank lines within a function.

IMPORTANT GUIDELINES - 
1. This is an android studio project, so don't mess with the project settings or dependency before consulting with me
2. Don't assume on tricky feature and design decisions. Ask me using the AskUserQuestionTool
3. After creating a plan of action, reevaluate it from the eyes of a senior software engineer - I am always in for big rewrites/changes if it means a cleaner, scalable and long lasting solutions

---

## App Overview: Strings

**Strings** is a read-only SMS organizer for Android. It does NOT replace the default SMS app -- it reads, categorizes, and displays messages. The app is for personal use only (minSdk 35).

**Package:** `com.strings.app`

### Architecture

- **MVVM** with ViewModels + Repository pattern
- **3-layer separation:** UI (Compose) -> Domain (use cases, models, FilterEngine) -> Data (Room, repositories)
- **DI:** Koin (Kotlin-native DSL, no annotation processing). Modules: `dataModule`, `domainModule`, `appModule`
- **Navigation:** Jetpack Compose Navigation with type-safe `@Serializable` routes (kotlinx-serialization)
- **Database:** Single Room database (`StringsDatabase`) with KSP for annotation processing
- **UI:** Jetpack Compose + Material 3 with dynamic color

### Key Design Decisions

- **No hardcoded categories.** Categorization is entirely through a hierarchical tag system (many-to-many). Tags have a `parentTagId` for hierarchy (e.g., Finance > HDFC > Credit Card).
- **Tabs are configurable.** Each tab in the inbox maps to a top-level tag via a `TabConfig` entity. Users can add/remove/reorder tabs.
- **Messages appear in ALL matching tabs** (not just one). A message tagged "Finance" + "HDFC" shows in both tabs.
- **Flat message list** (like Gmail) -- NOT conversation threads. Each message is its own row sorted by timestamp descending.
- **Filters have a condition tree.** `FilterCondition` rows support AND/OR grouping with operators: contains, equals, regex, starts_with. A single filter can have multiple actions (tag, archive, trash, mark read, suppress notification).
- **Filters apply to existing messages by default** when created/edited.

### Design Guidelines (UI/UX)

Follow these when building or changing any screen. They reflect decisions already applied across the app.

**Material 3 - color**
- **Dynamic (wallpaper) color is the default identity** (always available on minSdk 35). The non-dynamic fallback is a deliberate brand seed (app blue) in `ui/theme/Color.kt` + `Theme.kt` -- not leftover template colors. Don't change the `dynamicColor` default.
- **Always use `MaterialTheme.colorScheme` roles** -- never hardcode hex colors in UI.
- **Surface hierarchy (3 levels) for separation:** header / top app bar = `surfaceContainer`, Scaffold body = `surface`, list cards = `surfaceContainerHigh`. Apply consistently on list/management screens.
- **Unified pastel card palette** (`ui/theme/TagPalette.kt`): a single `AppPalette` of 10 muted pastel/beige entries (Almond, Rose, Sage, Vanilla, Lavender, Peach, Sand, Slate, Clay, Ivory), each with a light variant (use with dark text) and dark variant (use with light text). Both tags and account cards use this same palette. Use `resolveCardColors(index)` which auto-switches on `isSystemInDarkTheme()`. Never create a separate color palette for a new feature -- extend or reuse `AppPalette`.
- **Tag colors are auto-assigned, not user-chosen.** Use `rememberTagColors(tag)` from `ui/theme/TagPalette.kt` (deterministic hash of tag id/name -> palette index -> `accent` + `container`). The stored `Tag.color` hex is dead for display; never add a color picker or parse hex for tags.
- **Account card colors are user-assigned.** Each user-configured `Account` row stores a `colorIndex` mapping to `AppPalette` (auto-assigned as the least-used slot on create via `nextAccountColorIndex`, overridable from the swatch row in the account edit form). Use `rememberAccountCardColors(account)` from `FinanceUtils.kt`, which reads `account.colorIndex` (hash fallback for legacy rows with `colorIndex = -1`).
- **Transaction amount formatting is centralized.** Use `formatSignedAmount(amount, type)` from `FinanceUtils.kt` for all signed amounts -- it produces a uniform `"+ ₹22,500.00"` or `"- ₹636.94"` (sign, space, currency symbol, Indian grouping, 2 decimals). Use `formatCurrency(amount)` for unsigned balances. Never hand-format amounts with string interpolation or local `NumberFormat` instances.
- **Credit/debit colors are semantic.** Use `creditColor()` (muted green) and `debitColor()` (muted red) composables from `FinanceUtils.kt`, or `amountColor(type)` for the shortcut. These resolve light/dark variants from `TransactionColors` in `TagPalette.kt`. Never use `MaterialTheme.colorScheme.primary` or `onSurface` for transaction amounts -- always green for credit, red for debit.
- Use `TopAppBarDefaults.topAppBarColors(...)` (the `centerAlignedTopAppBarColors` overload is deprecated).

**Material 3 - spacing**
- **Use the central spacing scale** in `ui/theme/Spacing.kt` (`xs=4, sm=8, md=12, lg=16, xl=24, xxl=32`, on the 4dp grid). Don't introduce ad-hoc `dp` literals for padding/gaps.
- Conventions: list/screen horizontal margin = `Spacing.lg`; inter-card vertical spacing = `Spacing.sm`; card inner padding = `horizontal Spacing.lg, vertical Spacing.md`.
- Exception: true sizing/radii constants (icon sizes, corner radii, alignment offsets like the drawer label inset) may stay literal -- the scale is for spacing, not sizes.

**Destructive actions**
- **Every destructive action MUST show a Material 3 `AlertDialog` confirmation** before committing (tag delete, filter delete, message trash -- single and bulk). Use the **M3 basic dialog**: NO hero icon (so the title + supporting text are start-aligned and actions sit bottom-end, per https://m3.material.io/components/dialogs/guidelines). Pattern: a clear question headline, body explaining the consequence, a confirm `TextButton` and a **Cancel** dismiss button. **Both buttons use the default text-button color (`primary`)** -- don't override the confirm to `error`/red; M3 dialog actions are primary-colored text buttons (see ss2 in the guidelines). Don't pass `icon = {}` to `AlertDialog` for these (an icon forces center alignment).
- **Use accurate wording.** Reversible actions say "Move to Trash" (messages go to a recoverable trashed state); only truly permanent actions say "Delete ... This can't be undone." (tags, filters).
- **Guard referential integrity.** Block a delete that would break references and explain why instead of cascading silently -- e.g., a tag used by any filter shows a "Can't delete" dialog listing the filters. Deleting a tag also removes it from all messages (`message_tags`).
- **System tags are not deletable** (no delete affordance for `isSystemTag`).
- Keep transient dialog visibility as local `remember { mutableStateOf(false) }` UI state unless the decision/data must live in the ViewModel (e.g. the tag delete needs a repository check, so it lives in `TagViewModel`).

**Detail screens (e.g. message view)**
- **Top app bar = a generic title** (e.g. "Message"), not the record's own name -- don't repeat data the body already shows.
- **Toolbar actions follow M3 toolbar guidance:** a small set of primary icon actions (outlined icons) + an overflow `DropdownMenu` (`MoreVert`) for the rest. On the message view: Archive + Delete are icons; "Mark as read/unread" and "Manage tags" live in the overflow.
- **Tags show ACTIVE only** as colored `TagChip`s (never an inline active+inactive picker). Editing happens in a `ModalBottomSheet` opened from the overflow (`ManageTagsSheet`).
- **Extensible details pattern:** structured per-message fields (OTP now; amount/links later) are modeled as `MessageInfoField(label, value, emphasized, action)` and rendered by `MessageDetailsCard`/`DetailRow`. Add a new field by appending to the builder -- the layout absorbs it. `InfoFieldAction` carries row actions (e.g. `COPY`).
- **Surfaces:** top bar `surfaceContainer`, screen body `surface`, message body card `surfaceContainerHigh`, details card `surfaceContainer`; tonal cards with `elevation = 0.dp` (no shadow). Group content with `Arrangement.spacedBy` instead of dividers + large `Spacer`s.

**Selection controls**
- **Single-select among a small set of mutually exclusive options = M3 connected button group**, not radio buttons or a pair of `FilterChip`s. Use the built-in `SingleChoiceSegmentedButtonRow` + `SegmentedButton`, with `shape = SegmentedButtonDefaults.itemShape(index, count)` for the connected rounded ends; the selected segment shows its check icon automatically (see https://m3.material.io/components/button-groups/guidelines and https://m3.material.io/components/button-groups/specs). Example: the filter "Match ALL / ANY" toggle.
- **Binary on/off setting = M3 `Switch`** (the inbuilt `androidx.compose.material3.Switch`, not a custom wrapper). Always pass `thumbContent` showing a `Check` icon (sized `SwitchDefaults.IconSize`) when `checked`, per https://m3.material.io/components/switch/guidelines -- the tick is not on by default.
- **Multi-select among options = `FilterChip`s** (independent toggles, e.g. filter actions, tag assignment).

**In-app help (explain anything non-obvious)**
- **Whenever a feature or field is not self-explanatory to a first-time user, add help.** New concepts should never ship "bare" -- pick the right tier:
  - **Info tooltip next to a specific control/section** (`InfoTooltipIcon` / `SectionHeaderWithInfo` from `ui/components/InfoTooltip.kt`, tap-to-open M3 `RichTooltip`): for explaining ONE field, toggle, or section in a form -- e.g. the filter editor's Conditions/Actions headers, "Apply to existing messages", the tag editor's Parent selector. Place it inline, right where the confusion happens.
  - **Info tooltip in the top bar** (as a toolbar action): for explaining how the WHOLE screen works when the screen is a list/management surface with no single anchor control -- e.g. "How filters run" on `FilterListScreen` (priority + drag-to-reorder), "How tags work" on `TagListScreen`.
  - **`PlainTooltip` on long-press**: for icon-only buttons whose glyph isn't universally understood (e.g. the `AutoAwesome` "Suggest filter" wand in the selection bar). This is a label, not an explanation -- one or two words.
  - **"Getting started" page** (`ui/help/HelpScreen.kt`, drawer > Help): for cross-cutting concepts spanning multiple screens (what the app is, tags-vs-tabs model, filter pipeline, OTP handling, finance). Tooltips explain a control; the help page explains the mental model.
- **All help copy lives in `ui/help/HelpTexts.kt`** -- one object shared by tooltips and the help page so wording never diverges. Never inline help strings in screens.
- Keep tooltip copy short (2-4 sentences, or a compact bullet list for enumerations like filter actions). If it needs more, it belongs on the help page with a short tooltip pointing at the concept.
- A **first-run example template** (seeded by `DatabaseSeeder`: `Example: Shopping`/`Example: Orders` tags + the disabled `Example: Order updates` filter) doubles as living documentation -- when adding a flagship feature, consider extending the template so users can inspect a working example instead of reading about it.

**Top app bars (consistency)**
- **Use one toolbar pattern everywhere a screen has back navigation** (edit pages, management lists, tag-message list, search, message detail). Reference implementation: `MessageDetailScreen`. Pattern: a **start-aligned `TopAppBar`** (not `CenterAlignedTopAppBar`), `colors = TopAppBarDefaults.topAppBarColors(containerColor = surfaceContainer)`, a back `IconButton` (`AutoMirrored.ArrowBack`) as `navigationIcon`, and the `Scaffold` body on `surface` (`containerColor = MaterialTheme.colorScheme.surface`).
- **Toolbar action icons are outlined with default tint** -- e.g. delete is `Icons.Outlined.Delete` with NO `error` tint (the destructive confirmation lives in the dialog, not the icon color). Save/confirm uses `Icons.Default.Check`.
- **Exception: the inbox home** uses a custom search header (hamburger + search field) and a contextual multi-select bar (`primaryContainer`) -- these are deliberate and not the back-nav toolbar.

**General**
- Prefer standard Material 3 components over custom ones; check for a built-in, scalable option first.
- Typography comes from the theme (Inter via downloadable Google Fonts) -- use `MaterialTheme.typography`, don't hardcode font sizes. `fontWeight` overrides layered on a theme style (e.g. SemiBold on `titleMedium` for emphasis, unread-state weight in `MessageCard`) are deliberate and acceptable -- don't "fix" them.
- Run `ReadLints` on edited files; remember a full Gradle build is needed to validate Room/KSP codegen.

### Data Flow

- On first launch, the `DatabaseSeeder` seeds the system tags (`Inbox`, `OTP`) and the default Inbox tab; their ids are stored in `SettingsDataStore`.
- The `InboxViewModel` loads visible tabs, then for the selected tab queries messages by tag ID (including descendant tags via recursive CTE).
- The `FilterEngine` is a pure domain class that evaluates filter conditions against messages. It's used in the filter edit screen (preview/apply) and in the live ingest pipeline (`ApplyFiltersUseCase`).
- **Ingest pipeline (single hook):** both the live `SmsReceiver` and bulk `importAll` funnel through `SyncSmsUseCase.persist()`, which runs `OtpDetector`, stores `isOtp`/`otpCode`, assigns the Inbox tag (+ OTP tag when detected), applies filters, then notifies.

### Contact names (resolved at runtime, never stored)

- `ContactNameResolver` (`data/contacts`) maps a sender phone number -> contact display name via `ContactsContract.PhoneLookup`, with an in-memory cache (incl. negative results). It skips alphanumeric shortcodes and returns null without `READ_CONTACTS`.
- The stored `MessageEntity.senderName` column and its write flow are kept as-is (address/shortcode). Resolution is a **display override only**: `senderName = resolver.resolve(sender) ?: storedSenderName`, applied in `MessageRepositoryImpl.toDomain` (display) and in `SyncSmsUseCase.persist` (so `SENDER_NAME` filters and the notification title prefer the contact name). Never persist the resolved contact name.
- `READ_CONTACTS` is requested alongside SMS but is non-blocking (only `READ_SMS` gates the app).

### OTP detection + notifications

- `OtpDetector` (`domain/otp`) is a pure class: requires an OTP keyword, extracts a 4-8 digit code via keyword proximity, and rejects currency/amount digits (so bank alerts aren't false positives). Covered by `OtpDetectorTest`.
- `SmsNotifier` (`notification/SmsNotifier.kt`) has three channels: `Messages` (default), `OTP Codes` (high importance), and `Transactions` (default). All three notification types join the same group (`strings_messages`) with an `InboxStyle` summary so rapid-fire messages stack cleanly.
- **Transaction notifications** use `DecoratedCustomViewStyle` with custom `RemoteViews` layouts (`notification_transaction.xml` collapsed, `notification_transaction_expanded.xml` expanded). The layout is ledger-style: "Bank · Card ••1234" leads on the left with the signed amount right-aligned, colored green/red for credit/debit via `RemoteViews.setTextColor()`, plus a colored left-edge accent strip (a `FrameLayout`). Collapsed shows a 1-line ellipsized raw-SMS preview; expanded shows balance (when available) and the full SMS body.
- **OTP notifications** use `DecoratedCustomViewStyle` with digit-box `RemoteViews` layouts (`notification_otp.xml` collapsed, `notification_otp_expanded.xml` expanded). Each OTP digit gets its own bordered `TextView` (styles `OtpDigitSmall`/`OtpDigitLarge` in `styles.xml`, background `otp_digit_bg.xml`). Sender display uses the contact name when resolved, else `SenderPrincipal.principal()` (so `JM-HDFCBK-T` shows as `HDFCBK`) — collapsed shows digits left + sender right. The "Copy OTP" action + content-tap fire `OtpCopyReceiver` (clipboard copy, toast, dismiss), and a 5-minute `setTimeoutAfter` auto-expiry.
- **Normal message notifications** use standard `BigTextStyle` with `setSubText(sender)` for shortcode context, `setWhen(timestamp)` for accurate time.
- **Notification color constraints**: `NotificationCompat.setColor()` only tints the small icon circle and is unreliable on Android 12+. It does NOT color title, body, or background. For colored content (amounts, accent strips), use custom `RemoteViews` layouts where `setTextColor()` / `setBackgroundColor()` give full control. Always provide day/night color variants (`res/values-night/colors.xml`) for custom accent colors since notification panels switch theme with the system.
- **RemoteViews view whitelist**: notification layouts may ONLY use RemoteViews-compatible classes (`TextView`, `ImageView`, `FrameLayout`, `LinearLayout`, `RelativeLayout`, `Button`, `Chronometer`, `ProgressBar`, ...). A bare `<View>` is NOT allowed — SystemUI throws "Class not allowed to be inflated android.view.View" and silently drops the entire notification. For color strips/spacers use a `FrameLayout` (accepts `setInt(..., "setBackgroundColor", ...)` the same way).
- **Notification text styling**: Never use `?android:attr/textColorPrimary` or hardcoded colors for text in notification `RemoteViews` -- these don't reliably resolve across OEMs and dark/light modes. Instead, use the AndroidX compat text appearance styles: `@style/TextAppearance.Compat.Notification.Title` for primary text and `@style/TextAppearance.Compat.Notification` for secondary/body text. These are the official recommendation from the Android docs and auto-adapt to every notification panel theme. Custom styles (like the OTP digit boxes) should inherit from these via `parent="TextAppearance.Compat.Notification.Title"`.

### Filter ordering

- Filters apply in `priority` order (ascending). `FilterDao.getAllFilters()` / `getEnabledFilters()` already `ORDER BY priority ASC`, and `ApplyFiltersUseCase` runs them in that order, so order is meaningful for conflicting filters.
- `FilterListScreen` supports drag-to-reorder via `sh.calvin.reorderable` (`rememberReorderableLazyListState` + `ReorderableItem`), with a trailing `Icons.Filled.DragHandle` (6-dot) carrying `Modifier.draggableHandle()`. `FilterViewModel.moveFilter(from, to)` reorders an `orderedFilters` state locally for smooth dragging; `persistFilterOrder()` writes `priority = index` for every row via `FilterRepository.setFilterOrder(orderedIds)` on drag stop.
- New filters append at the end: `saveFilter` sets `priority = getMaxPriority() + 1` (was 0 = top).

### Auto-suggest filters from selection

- In any message-list multi-select bar, an `AutoAwesome` action ("Suggest filter") drafts a filter from the selected messages and opens the editor prefilled, **inactive, with no actions** (the user adds actions and saves). Reference: `MessageSelectionTopBar` in [SelectableMessageList.kt](app/src/main/java/com/strings/app/ui/components/SelectableMessageList.kt).
- `FilterSuggester` ([domain/filter/FilterSuggester.kt](app/src/main/java/com/strings/app/domain/filter/FilterSuggester.kt)) is a pure, deterministic heuristic (no ML): **sender** - if all selected share the same `sender` -> `SENDER EQUALS`, else if they share the same principal -> `SENDER CONTAINS principal`. `principalOf` is DLT-aware: it splits the header on `-`, drops a trailing single-char category code (P/T/S/G) and a leading <=2-char operator access code, so `AX-BSELTD-S` -> `BSELTD` and `VM-HDFCBK` -> `HDFCBK` (single-token/numeric senders pass through unchanged). **Body** - greedy longest-common-phrase extraction over the shortest message as reference: scan its word-spans longest-first, keep a phrase only if every other selected body contains it (word-boundary aware, so `noon` does not match `afternoon`), greedily take the longest, mask consumed words, repeat for non-overlapping phrases (caps in the companion: max 4 phrases, 8 words each). Variable parts (OTPs, amounts, dates) differ across messages so they become phrase boundaries.
- **Body representation (regex/AND hybrid):** the extracted phrases are sorted by reference position, then chained in order as long as the chain still matches every selected message in that order. A chain of 2+ phrases becomes one `BODY MATCHES_REGEX` joining the `Regex.escape`'d phrases with a `[\s\S]*?` wildcard gap (order-enforced, spans variable parts and newlines); a lone chained phrase stays a plain `BODY CONTAINS`. Any phrase whose relative order is not consistent across all messages falls back to a separate AND `BODY CONTAINS`. All leaves (sender + body) are AND-combined; returns `null` (UI shows a snackbar) when nothing is shared. Covered by `FilterSuggesterTest`.
- The draft is handed to the editor via `FilterDraftHolder` ([domain/filter/FilterDraftHolder.kt](app/src/main/java/com/strings/app/domain/filter/FilterDraftHolder.kt)), a single, consume-once holder: `suggestFilterFromSelection()` stores it, navigation opens the **new-filter** route (`FilterEditRoute()`), and `FilterViewModel.loadFilterForEdit(-1)` consumes it to prefill. Consume-once means a normal "create filter" is unaffected and nothing is persisted until the user saves.

### Unified selectable message lists

- Every message list (inbox tabs, All messages, tag messages, filter messages, search results) shares the **same row, multi-select, tap-to-open, and contextual action bar** (Archive / Trash / Suggest filter). Don't fork per-screen list behavior.
- Selection state + actions live in `SelectableMessagesViewModel` ([ui/common/SelectableMessagesViewModel.kt](app/src/main/java/com/strings/app/ui/common/SelectableMessagesViewModel.kt)): `selectedMessageIds`, `toggleMessageSelection`, `clearSelection`, `archiveSelected`, `trashSelected`, `suggestFilterFromSelection`. `InboxViewModel`, `AllMessagesViewModel`, `TagMessagesViewModel`, `FilterMessagesViewModel`, and `SearchViewModel` all extend it.
- Shared UI is in [ui/components/SelectableMessageList.kt](app/src/main/java/com/strings/app/ui/components/SelectableMessageList.kt): `MessageSelectionTopBar` (the `primaryContainer` contextual bar), `PagedMessageList` (loading/empty/list body with the paging-aware empty check), and `SelectableMessageScaffold` (full scaffold with the title<->selection top-bar swap, trash-confirm dialog, and suggest snackbar) used by the three back-stack screens. The inbox and search keep their custom shells but reuse `MessageSelectionTopBar`/`PagedMessageList`/`messageItems`.

### Data export / import (versioned JSON backup)

- Entry points live in Settings > Data ("Export data" / "Import data"). File I/O uses SAF (`CreateDocument`/`OpenDocument`, MIME `application/json`, default name `strings-backup.json`) inside `SettingsScreen`; the use cases stay Context-free and operate on JSON strings + repos (settings go through the `BackupSettingsStore` interface in `domain/backup`, implemented by `DataStoreBackupSettings` over `SettingsDataStore`).
- Format: `BackupBundle(version, tags, filters, tabs, accounts, messageStates, settings)` in `domain/backup/BackupModels.kt` (`BACKUP_VERSION = 4`); keep the import guard (reject `version > BACKUP_VERSION`) and give new sections defaults so older bundles still import. Cross-device ids differ, so the bundle references tags **by name** (`TagDto.parentName`, `FilterActionDto.targetTagName`, `TabConfigDto.tagName`, `MessageStateDto.tagNames`) and accounts **by (bankCode, accountTail)** (`AccountDto.parentBankCode`/`parentAccountTail` for family links). Account import find-or-creates by that key, re-links parents in a second pass, then re-runs recategorization so transactions exist before message-state balance overrides are applied.
- **BACKUP COMPLETENESS RULE: whenever a new kind of user-curated data is added (a new entity, a user-editable field, a setting), it MUST be added to the export/import bundle in the same change**, bumping `BACKUP_VERSION` if the format changes shape. Re-derivable data stays out (parser output, OTP flags, contact names -- anything the ingest pipeline regenerates from the device SMS store); anything the user creates or edits by hand goes in.
- **Backup is state-only, not message bodies.** Messages re-import from the device SMS store; the bundle carries per-message state (`MessageStateDto`: read/archive/trash flags, full tag-name set, `balanceAfter` override) keyed by `deviceMessageId` (stable per device) with a `(sender, timestamp)` fallback. Export includes a message only when its state differs from what ingest auto-derives (flags set, balance override, or tag set != Inbox/OTP baseline).
- Import collision rules (`ImportDataUseCase`): tags are **reused by name** (system tags resolve to existing, never duplicated; missing non-system tags are inserted then re-parented by name). Filters: if an existing filter has the same name AND a structurally equal pruned `root` -> **drop** the import; otherwise insert, adding a `" (n)"` suffix if the name collides; the exported `isEnabled` is preserved. `ASSIGN_TAG` actions are remapped by tag name (dropped if the tag is absent). Imported filters get `priority = currentMax + i` to append in order. Tabs are matched by tag name: existing tab for that tag -> update position/visibility, else insert. Message-state restore replaces the message's tag set (skipped when no exported tag name resolves) and writes `balanceAfter` onto the message's re-parsed transaction. Import is idempotent -- re-importing the same bundle changes nothing.
- `BackupViewModel.import()` runs `SyncSmsUseCase.importAll()` first (idempotent, mutex-guarded; `SecurityException` swallowed) so every device SMS exists and is parsed before message states are matched; unmatched states are counted and reported in `ImportResult`.
- Export is pretty-printed (`ExportDataUseCase` builds its own pretty `Json`); the shared `Json` (DataModule) stays `ignoreUnknownKeys = true` for forward-compatible imports. `ActionType` is `@Serializable`.
- Round-trip coverage lives in `ExportImportDataUseCaseTest` (JVM, in-memory fakes of the repository interfaces) -- extend it when adding a new backup section.

### Build System

- AGP 9.2.1 with built-in Kotlin (no standalone `kotlin-android` plugin needed)
- KSP 2.3.6+ (standalone, not tied to Kotlin version -- required for AGP 9 compatibility)
- Room 2.8.4+ (required to avoid "unexpected jvm signature V" bug with KSP2)
- Koin 4.1.x (4.2+ requires Kotlin 2.3, which this project doesn't use yet)
- Version catalog at `gradle/libs.versions.toml`

### Building & Testing

- There is no `java` on the default `PATH`. Use the JDK bundled with Android Studio: `/Applications/Android Studio.app/Contents/jbr/Contents/Home`. Set it before any Gradle command:

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
```

- Run all JVM unit tests: `./gradlew testDebugUnitTest`.
- Run a single test class (faster): `./gradlew testDebugUnitTest --tests "com.strings.app.domain.transaction.TransactionParserTest"`. Repeat `--tests` for multiple classes.
- Compile/assemble without installing: `./gradlew assembleDebug`. A full Gradle build is what validates Room/KSP codegen (lints alone won't catch schema/DAO errors).
- The first invocation downloads the Gradle distribution and starts a daemon, so it is slow; give it a few minutes and don't abort early. Subsequent runs reuse the daemon.

### Future Plans

See `FUTURE_PLANS.md` for the roadmap. The next phases are:
1. Real SMS reading (ContentResolver + BroadcastReceiver + WorkManager sync)
2. OTP detection with copy-from-notification
3. Bank/CC transaction tracking (India)

### Things to Watch Out For

- Room DAO queries that JOIN message_tags MUST use `SELECT DISTINCT` to avoid duplicate messages (a message can match multiple tag IDs in a single query).
- The `TagRepository` constructor takes both `TagDao` and `TabConfigDao`.
- When AGP 9 is in use, never add `android.disallowKotlinSourceSets=false` to gradle.properties -- use KSP 2.3.1+ instead.
- Navigation routes use kotlinx-serialization `@Serializable` -- the `kotlin-serialization` plugin must be applied.
- **Room Flow auto-propagation**: Any `@Query` that returns `Flow` auto-emits when the underlying table changes (Room invalidation tracker). A `suspend` UPDATE/INSERT/DELETE on the same table triggers all active Flow observers -- no manual refresh/reload needed. ViewModels using `stateIn(WhileSubscribed(5_000))` will resume and pick up changes when the screen comes back into view.
- **Discoverability over gestures**: Long-press and swipe gestures are power-user shortcuts, not primary affordances. For infrequent actions (e.g. "Set balance"), always surface them in the overflow menu (`MoreVert` dropdown) or a visible button. Long-press can be kept as an additional shortcut but never as the sole entry point.
- **Don't duplicate palettes/registries across features.** When multiple features need colored cards (tags, accounts, future features), extend the single `AppPalette` rather than creating parallel palette systems. Centralize public institution metadata (bank codes, display names, sender principals, supported types) in `BankCatalog` -- never scatter it across parsers, ViewModels, or UI files. User account identity (tails, names, colors, family links) lives only in the Room `accounts` table.

### Domain Layer Principles

- **Pure domain classes have no Android imports.** Classes in `domain/` (parsers, detectors, engines, suggesters, models) must be pure Kotlin with zero Android SDK dependencies. This keeps them JVM-unit-testable without Robolectric or instrumentation. If a class needs `Context`, it belongs in `data/` or `notification/`, never `domain/`.
- **Per-bank parser pattern for detection.** The `TransactionParser` routes each SMS by sender principal through `BankCatalog` to a single `BankParser` (one class per bank in `BankParsers.kt`, shared regex helpers in `BankParsing`). Parsers receive only the user's enabled accounts for their bank -- account identity is data, never code. Add new detection by implementing `BankParser`, adding a `BankCatalog` entry, and registering it in `defaultBankParsers()`. `parse` returns a `ParseOutcome` (`Match` / `UnconfiguredAccount` / `NoMatch`); unconfigured tails become account suggestions surfaced on the Manage accounts screen.
- **Idempotent message re-processing.** `TransactionCategorizer.categorize()` always deletes existing transactions for a message before creating new ones. This means re-running after a parser change cleanly replaces stale results. Any new per-message processing (e.g. link extraction, metadata) should follow this delete-then-recreate pattern to stay idempotent.
- **Find-or-create for reference data.** Accounts and finance tags use `findOrCreate` (look up by natural key, insert if missing). Never assume a reference row exists -- always resolve through `findOrCreateAccount()` or `ensureTag()`. This makes the ingest pipeline order-independent and idempotent.

### Repository & Data Layer Conventions

- **Entity <-> Domain mapping lives in the repository impl.** Each `*RepositoryImpl` has private `toDomain()` and `toEntity()` extension functions. Domain models (`domain/model/`) are simple data classes with no Room annotations; entity models (`data/local/db/entity/`) carry `@Entity`, `@PrimaryKey`, and store enums as `String`. Never expose entities above the repository boundary.
- **Enums are stored as `.name` strings** in Room (e.g. `AccountType.CREDIT_CARD` -> `"CREDIT_CARD"`). Restore with `EnumType.valueOf(stored)`. Don't use ordinals (fragile across code changes).
- **DAO queries return `Flow` for observation, `suspend` for one-shot.** If the UI needs live updates (list screens, balances), use `Flow<List<...>>`. For actions triggered by user interaction (insert, delete, find-by-id), use `suspend`. Never return a plain (blocking) list from a DAO.

### ViewModel Reactive Patterns

- **Standard reactive chain**: `source Flow` -> `.map` / `.flatMapLatest` -> `.stateIn(viewModelScope, WhileSubscribed(5_000), initialValue)`. Every public `StateFlow` exposed to UI uses `WhileSubscribed(5_000)` so it stops collecting when the screen is off and resumes when it returns.
- **Dependent multi-source queries use `combine` + `flatMapLatest`**: when a query depends on both a user-controlled input (e.g. month selector) and a derived value (e.g. family account IDs), `combine` them into a single emission, then `flatMapLatest` into the repository query. This ensures atomic recomposition when either input changes.
- **Cache expensive computations inside `combine` lambdas.** When multiple accounts share the same family balance, compute it once into a local `Map` (keyed by family root) rather than re-running `computeEstimatedBalance()` N times. Use `getOrPut` on a mutable map inside the lambda -- it's safe because the lambda is not concurrent.

### Account Family Grouping (Parent-Child Accounts)

- Accounts sharing the same underlying statement (e.g. add-on credit cards) are linked via `parentAccountId` on the user-configured `Account` row (set from the "Linked primary card" selector in the account edit form; same-bank credit cards only, one level).
- **Family resolution utilities** in `AccountFamilies` (`domain/transaction/`): `rootId(account, accountsById)` returns the parent id or self; `familyAccountIds(account, accounts)` returns all member ids of the family (parent + children).
- **Balance and monthly stats are family-aggregated.** `computeEstimatedBalance()` receives all family transactions merged together. The same balance and income/expense appear on both parent and child account cards.
- **`totalBalance` deduplicates by family root** -- counts each family's balance once, not per-member.
- **Account detail view shows family transactions.** `AccountDetailViewModel` resolves family account IDs and queries all of them via the multi-account DAO methods (`getTransactionsByAccounts`, `getTransactionsByAccountsInRange`). Each transaction row shows the specific child account name so the user can distinguish.
- **Child account cards show "Linked with [Parent]"** subtitle in the Accounts tab. Family members sort together (parent first, then children), grouped by family activity.
- **Referential safety**: deleting an account clears `parentAccountId` on its children first (`AccountDao.clearParentReferences`), and `AccountFamilies.rootId` treats a dangling parent id as self, so a family never breaks at runtime.

### Link Detection in Message Body

- Message body text in `MessageDetailScreen` uses `buildLinkedText()` to detect and render clickable URLs, emails, and phone numbers as `LinkAnnotation.Url` spans.
- **URL detection uses `android.util.Patterns.WEB_URL`** (the standard Android pattern, same as `Linkify`). This handles bare domains with any TLD (e.g. `b8.to/...`, `bit.ly/...`) -- not just `https://` prefixed URLs. When a matched URL has no protocol prefix, prepend `https://` before passing it as the link URI.
- **Email** uses a standard `user@domain.tld` regex. **Phone** uses an India-aware regex (10-digit mobile starting 6-9, optional +91/91 prefix, 1800 toll-free). Non-overlapping: if ranges conflict, the first-detected link wins.
- Links in the message list preview (`MessageCard`) are NOT clickable -- interaction is the card tap/long-press. Clickable links are only in the detail screen body.

### Testing Conventions

- All tests are JVM unit tests in `app/src/test/` (no instrumentation tests). Run with `./gradlew testDebugUnitTest`.
- Test classes mirror the source path: `domain/otp/OtpDetector` -> `domain/otp/OtpDetectorTest`.
- Only domain-layer pure classes are tested (parsers, detectors, engines, suggesters). ViewModels and repositories are not currently unit-tested (they depend on Room/Koin/Android).
- Tests use JUnit 4 (`@Test`, `@Before`), plain assertions (`assertEquals`, `assertNotNull`, `assertNull`). No mocking framework.
- When adding a new domain class with non-trivial logic, add a test class. When changing parser/detection behavior, add regression test cases for the new edge cases.