# Oxide Launcher 1.5.0 — `IllegalStateException: Vertically scrollable component was measured with an infinity maximum height constraints`

Investigated: `/tmp/opencode/work/ox` @ `6180c7a`. No Gradle run, no commit.

## Root cause (confirmed, fixed)

**`OxideLauncher/src/main/java/dev/oxide/launcher/ui/screens/main/oxide/OxideHomePage.kt:806`** (pre-fix), composable
`OxideHomeEnvironmentCard()` — a `verticalScroll` nested inside another `verticalScroll`.

Modifier chain, exactly as written:

```
OxideHomeCompactBody()                                   OxideHomePage.kt:466
└─ Column(Modifier.fillMaxSize().verticalScroll(scroll)) OxideHomePage.kt:484-488   <-- OUTER scroll
   └─ OxideReveal { }                                    OxideComponents.kt:483  (AnimatedVisibility: no height clamp)
      └─ OxideHomeEnvironmentCard(fillHeight = false)     OxideHomePage.kt:535 / :555
         └─ OxideSurface -> Column(base.padding(...))    OxideComponents.kt:93    (no height clamp)
            └─ Column(Modifier.verticalScroll(state))    OxideHomePage.kt:806     <-- INNER scroll => CRASH
```

`Modifier.verticalScroll` measures its content with `Constraints(minHeight = 0, maxHeight = Constraints.Infinity)`
(`androidx.compose.foundation.Scrollable`). Nothing between line 484 and 806 clamps `maxHeight` (`fillMaxSize`,
`AnimatedVisibility`, `OxideSurface`'s `Column` all pass it through), so the inner `verticalScroll` receives
`maxHeight == Infinity` and `ScrollerMeasurePolicy.measure` hits
`require(mainAxisMax != Constraints.Infinity)` — the exact reported message.

`fillHeight = false` is passed from **both** compact call sites (`:539`, `:559`), so the `else` branch is always taken.

### Why it fires in normal flow
* `OxideHomePage` is the launcher's landing screen.
* Layout is chosen at `OxideHomePage.kt:308`: `metrics.widthClass == OxideWidthClass.Compact` -> `OxideHomeCompactBody`.
* `OxideMetrics.kt:42` `CompactMax = 900`; width comes from `LocalConfiguration.current.screenWidthDp`
  (`OxideMetrics.kt:485-497`), i.e. the whole window, not the page column.
  On a landscape phone that is most devices (1920x1080@420dpi -> 731dp, 2400x1080@440dpi -> 873dp, 2560x1440@560dpi -> 731dp).
  Only >=901dp windows take the wide body.
* The only escape today is the transient `rows.isEmpty()` loading branch (`:796`); `envRows` is populated from real
  loader/renderer/java/device/arch values (`OxideHomePage.kt:235-278`), so it is non-empty in practice.

### Fix applied
`OxideHomePage.kt:799-811` — the `fillHeight = false` branch now returns a plain `Modifier` instead of
`Modifier.verticalScroll(...)`. Rationale: that branch is only used by the compact body, whose page column already
scrolls, so the rows must simply flow into the outer scroll. `fillHeight = true` (wide body, line 432) keeps its
bounded `weight(1f).verticalScroll(...)` because the wide body's chain is height-bounded
(`Column(Modifier.fillMaxSize())` -> `Column(weight(1f))` -> `OxideReveal(weight(1f))`).

Deliberately **not** used: `heightIn(max = ...)`, `wrapContentSize`, or catching the exception — all would mask a
real layout contract violation rather than restore it.

### Verification
* Reproduce: `oxideMetricsFor(800, 480).widthClass == OxideWidthClass.Compact`; force `revealed = true` and
  `envRows` non-empty, then render `OxideHomePage`. Before: throws on first measure of the env card.
  After: renders; the whole compact page scrolls as one.
* Regression net: `OxideHomeDiscoverGeometryTest` / `OxideShellGeometryTest` already sweep `oxideMetricsFor(width, height)`
  across breakpoints — nothing here changes metrics, so they must stay green.
* Manual: on a <=900dp-wide window, open the launcher and confirm the Home page env card scrolls with the page and
  the wide-body path still scrolls the env card independently.

## What the audit ruled out (evidence)

| Search | Result |
| --- | --- |
| `layout_weight` in any XML | none — the project has exactly **one** layout file, `OxideLauncher/src/main/res/layout/player_texture_view.xml` (a `PlayerView`, no weights, no ComposeView) |
| `ComposeView` / `AbstractComposeView` in XML or Kotlin | none. The only `setContentView(...)` in Kotlin/Java is `SDLActivity.java:1649` |
| `wrapContentSize(unbounded = true)` | none. Two `wrapContentSize()` calls (`GameBall.kt:147`, `LayerController/utils/Buttons.kt:329`) use the bounded form |
| `SubcomposeLayout`, `AnimatedContent` producing unbounded children | none that can reach a scroll container |
| Brace-aware scan of every `.kt` for scroll-inside-scroll (same file) | 7 hits, all `if/else` siblings or `horizontalScroll` under a `LazyColumn` (maxWidth is finite) |
| Cross-composable call-graph sweep (scroll-bearing composable called from inside a `verticalScroll` scope) | 36 raw hits; all but the two below are false positives (siblings, or `Dialog`/`Popup` sub-windows which get their own `AT_MOST` root) |

## Ranked candidates considered and rejected

1. **`OxideAccountPage.kt:397/439/461`** — `OxideAccountListCard` (LazyColumn, `:801`) and `OxideAccountSheetHost`
   (`:997`) next to the scroll Columns at `:370/:423`. Verified **siblings** inside a `Row`/weighted `Column`, and
   the sheet host renders in its own `Dialog` window. Safe.
2. **`OxideMultiplayerPage.kt:205/217`** — `OxideMpGuideCard` (internal scroll at `:431`) is a weighted **sibling**
   of the scroll Column at `:187`. Safe.
3. **`OxideExportPage.kt:731`** — `OxideExportFilesStep` is a `when` **sibling** of the scroll Columns at `:702/:717`;
   its own LazyColumn (`:1194`) sits under `OxideSurface(weight(1f))`. Safe.
4. **`EditControlLayerDialog.kt:152` and `EditJoystickConfig.kt:59`** — `InfoLayoutListItem`'s `LazyColumn`
   (`control_editor/_Layout.kt:195`) is inside a `verticalScrollWithBar` Column, but it carries
   `.heightIn(max = maxListHeight)` with `maxListHeight: Dp = 200.dp` (`_Layout.kt:158, 195-197`), so `maxHeight` is
   finite. **Latent footgun**: any caller passing `maxListHeight = Dp.Unspecified` turns it into the same crash.
5. **`EditStyleDialog.kt:223/255/261`** — `StyleConfigEditor` under `Column(weight(0.6f))` inside a `verticalScroll`;
   `RowColumnImpl` allocates weight against `constraints.minHeight` when the max is infinite, so the child gets
   `maxHeight = 0` (a zero-height editor bug, not this crash).
6. **`OxidePopover.kt:418` / `OxideInstancesPage.kt:902`** — scroll containers inside a Compose `Popup`, but the panel
   chains apply `.heightIn(max = ...)` *before* `.verticalScroll(...)`, which clamps `maxHeight`. Safe.
7. **`MultiplayerDialog.kt:514`** — `ProfileListPanel` is a weighted sibling of the scroll Column at `:468-476`. Safe.
8. **`SDLActivity.messageboxShowMessageBox` (`SDLActivity.java:1630-1650`)** — the only `MaterialAlertDialogBuilder
   `setView(...)` in the project; its `LinearLayout` + `ScrollView` hosts `TextView`s only, no Compose.

## Caveat on the stack trace

The reported frames `LinearLayout.measureChildBeforeLayout` -> `measureVertical` -> `onMeasure` and
`DecorView -> FrameLayout -> androidx.appcompat.widget.ContentFrameLayout -> FrameLayout -> LinearLayout -> ComposeView`
do **not** correspond to any window this app builds:

* Every Compose root in the app is a `setContent { }` `ComposeView` (`MainActivity.kt:314`, `VMActivity.kt:484`,
  `ControlEditorActivity.kt:91`, `ErrorActivity.kt:131`, `SplashActivity.kt:86`, `FileManagerActivity.kt:90`). Its
  parent chain is `DecorView -> PhoneWindow$ContentFrameLayout -> FrameLayout(R.id.action_bar_activity_content) ->
  ComposeView` — there is no `LinearLayout` and no `androidx.appcompat.widget.ContentFrameLayout` (that class is not
  used by `AlertController`; `abc_alert_dialog_material`'s root is `androidx.appcompat.widget.AlertDialogLayout`, and
  `AlertController.setupCustomContent` inserts a custom view into `FrameLayout#custom` with `MATCH_PARENT`).
* The only `LinearLayout`s in the repo are `SDLActivity.java:1634` and the AppCompat/Material dialog layouts, and none
  of them ever hosts a `ComposeView`.

So those frames are most likely mis-reconstructed from the R8 output (with `-renamesourcefileattribute SourceFile`
the frames collapse to `Unknown Source`). They are **not** needed to explain the crash: the exception text plus the
code audit above identify the site uniquely. If a future report really does show a `ComposeView` under a
`LinearLayout`, the only in-repo candidate is `SDLActivity.messageboxShowMessageBox`, and it would need its
`ScrollView`/Compose interaction re-examined separately.

## Suggested follow-up (not done — out of scope of the single confirmed fix)

A regression test that asserts "no vertical scroll container is composed under another one" would have caught this at
build time; the codebase already has pure-geometry tests under
`OxideLauncher/src/test/java/dev/oxide/launcher/ui/screens/main/oxide/` that would be the natural home. The
`InfoLayoutListItem` `maxListHeight` default (§4 above) is worth making non-overridable while the audit is fresh.