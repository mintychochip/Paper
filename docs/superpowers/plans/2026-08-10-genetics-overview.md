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
- HTML controls expose `#species`, generic parent selectors, equine parent selectors (`#parent-a-speed`, `#parent-a-jump`, `#parent-a-health`, and matching Parent B controls), `#seed`, `#breed`, and `#reset`.
- JavaScript functions `createRng(seed)`, `breed(parents, seed)`, `renderResult(result)`, and `renderRejection(result)` keep generic and equine simulation behavior isolated from page rendering.
- Result rendering updates the shared status/log plus generic result fields or equine result fields (`#equine-child-plan`, `#equine-child-color`, `#equine-child-markings`, `#equine-child-speed`, `#equine-child-jump`, and `#equine-child-health`).

- [ ] Add the document shell, metadata, and page sections for hero, architecture, inheritance flow, evidence cards, profile matrix, coverage gaps, and simulator.
- [ ] Add inline CSS for the dark Alkahest palette, responsive two-column layout, flow connectors, badges, result states, focus styles, and narrow-screen stacking.
- [ ] Add repository-grounded explanatory copy with exact API/server/hook paths and the current 72-test/server-compile status.
- [ ] Add parent selectors and seed controls using labels, descriptions, and native buttons.
- [ ] Implement seeded deterministic randomness with a small integer PRNG so the same seed reproduces the same result.
- [ ] Implement opposite-sex validation, child-sex selection, X-linked coat inheritance, calico phenotype resolution, maternal vigor inheritance, and an event log.
- [ ] Add a Generic/Equine profile switcher; preserve the existing generic coat/maternal demo and expose the five equine loci from `EquineGeneticsProfile`.
- [ ] Implement vanilla-equivalent reflected numeric offspring values for speed `[0.1125, 0.3375]`, jump `[0.4, 1.0]`, and health `[15.0, 30.0]`.
- [ ] Resolve horse × donkey to a visible Mule plan and reject mule parents as sterile.
- [ ] Add reset behavior that restores the initial parents, seed, and result state.
- [ ] Add a visible `illustrative browser model` disclaimer beside the simulator.
- [ ] Confirm the HTML contains no external resource references.

### Task 2: Verify the browser artifact

**Files:**
- Verify: `docs/genetics-overview.html`

- [ ] Open the file in Chromium and inspect the rendered desktop layout.
- [ ] Resize to a narrow viewport and confirm the layout stacks without clipping or horizontal overflow.
- [ ] Breed valid opposite-sex generic parents and confirm the result shows sex, coat, maternal trait, seed, and event log.
- [ ] Switch to Equine mode, breed the default horse × donkey parents, and confirm the result shows Mule plan, color, markings, speed, jump, health, and equine events.
- [ ] Set both generic parents to the same sex and set an equine parent to Mule; confirm both rejection states appear without a child result.
- [ ] Change the seed and confirm the displayed deterministic result or event log changes.
- [ ] Use Reset and confirm Generic mode, initial controls, and a Ready-to-breed result return.
