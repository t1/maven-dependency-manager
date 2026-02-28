/// Interactive TUI dashboard built on TamboUI.
///
/// Follows an MVC-like structure:
/// - `DashboardModel` — all UI state (phase, tab, updates, selections, build output, diff)
/// - `DashboardView` — renders the model into TamboUI widgets via panel classes
/// - `DashboardController` — translates key events into model mutations
/// - `DashboardApp` — wires everything and runs the event loop
///
/// Actions run on virtual threads and post results back via `TuiRunner.runOnRenderThread`:
/// `ScanAction`, `ApplyUpdatesAction`, `MavenBuildAction`, `GitDiffAction`.
///
/// Most classes are package-private; only `DashboardApp` and `DashboardConfig`
/// are public (used by `cli.TuiCommand`).
package com.github.t1.mavendep.tui;
