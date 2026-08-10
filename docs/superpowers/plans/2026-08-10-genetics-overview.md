# Genetics Overview Dashboard Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create a dependency-free HTML explainer and browser-only genetics breeding demonstration.

**Architecture:** `docs/genetics-overview.html` is the only runtime artifact. Inline CSS defines the Alkahest visual system; semantic HTML renders repository-grounded facts; inline JavaScript owns a small deterministic simulator with parent selectors, seed input, validation, breeding, and result rendering. The simulator is explicitly illustrative and does not import or claim to execute Java.

**Tech Stack:** HTML5, CSS custom properties/grid, vanilla JavaScript, Chromium browser verification.

## Global Constraints

- Keep the artifact at exactly `docs/genetics-overview.html`.
- Use no external scripts, stylesheets, fonts, images, network requests, or build tooling.
- Do not modify Java production sources or existing genetics tests.
- Preserve the truthful coverage statement: 72 API tests pass; server tests currently stop at test compilation at `AnimalGeneticsTest.java:165`.
- Make all interactive controls keyboard-accessible native controls.

---

### Task 1: Build the explainer and simulator

**Files:**
- Create: `docs/genetics-overview.html`

**Interfaces:**
- HTML controls expose `#parent-a-sex`, `#parent-a-coat`, `#parent-b-sex`, `#parent-b-coat`, `#seed`, `#breed`, and `#reset`.
- JavaScript functions `createRng(seed)`, `breed(parentA, parentB, seed)`, `renderResult(result)`, and `renderRejection(message)` keep simulation behavior isolated from page rendering.
- Result rendering updates `#result-status`, `#child-sex`, `#child-coat`, `#child-maternal`, and `#event-log`.

- [ ] Add the document shell, metadata, and page sections for hero, architecture, inheritance flow, evidence cards, profile matrix, coverage gaps, and simulator.
- [ ] Add inline CSS for the dark Alkahest palette, responsive two-column layout, flow connectors, badges, result states, focus styles, and narrow-screen stacking.
- [ ] Add repository-grounded explanatory copy with exact API/server/hook paths and the current 72-test/server-compile status.
- [ ] Add parent selectors and seed controls using labels, descriptions, and native buttons.
- [ ] Implement seeded deterministic randomness with a small integer PRNG so the same seed reproduces the same result.
- [ ] Implement opposite-sex validation, child-sex selection, X-linked coat inheritance, calico phenotype resolution, maternal vigor inheritance, and an event log.
- [ ] Add reset behavior that restores the initial parents, seed, and result state.
- [ ] Add a visible `illustrative browser model` disclaimer beside the simulator.
- [ ] Confirm the HTML contains no external resource references.

### Task 2: Verify the browser artifact

**Files:**
- Verify: `docs/genetics-overview.html`

- [ ] Open the file in Chromium and inspect the rendered desktop layout.
- [ ] Resize to a narrow viewport and confirm the layout stacks without clipping or horizontal overflow.
- [ ] Breed valid opposite-sex parents and confirm the result shows a child sex, coat, maternal trait, seed, and event log.
- [ ] Set both parents to the same sex and confirm a clear rejection state appears without a child result.
- [ ] Change the seed and confirm the displayed deterministic child result or event log changes.
- [ ] Use Reset and confirm the initial controls and result return.
