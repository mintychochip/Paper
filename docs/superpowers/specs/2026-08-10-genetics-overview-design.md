# Genetics overview dashboard

**Date:** 2026-08-10
**Status:** design approved
**Artifact:** `docs/genetics-overview.html`

## Goal

Provide a fun, self-contained browser artifact that explains the current Alkahest genetics implementation and demonstrates one simplified breeding cross without requiring a build, server, Java runtime, or external dependency.

## Audience

A developer or curious player who wants to understand where genetics lives, what the tests prove, and how a parent cross becomes a child genome and phenotype.

## Experience

The page has two connected sections:

1. **Explainer dashboard** — architecture, inheritance flow, profile coverage, test evidence, and explicit coverage gaps.
2. **Browser breeding sandbox** — selectable parent sex/traits, deterministic seed control, a Breed action, and a child result showing sex, inherited alleles, maternal inheritance, phenotype, and a short event log.

The sandbox is clearly labeled as a visual model. It must not claim to execute the Java `BreedingEngine`; its displayed rules mirror the documented contracts: opposite-sex mating, X-linked coat inheritance, maternal loci, and deterministic seeded randomness.

## Visual design

- Single dark page with Alkahest copper, violet, blue, and green accents.
- Responsive two-column desktop layout that collapses to one column on narrow screens.
- High-contrast cards, compact pills, large numeric evidence, and a clear flow diagram.
- No images, fonts, analytics, network requests, or external resources.

## Content

- API path: `alkahest-api/src/main/java/dev/mintychochip/genetics/`.
- Server path: `paper-server/src/main/java/dev/mintychochip/genetics/`.
- Hook path: `paper-server/patches/sources/net/minecraft/`.
- Test result: 72 API genetics tests passed; server tests are present but currently stop at test compilation due to the Mockito generic mismatch at `AnimalGeneticsTest.java:165`.
- Coverage gap: no multi-generation Monte Carlo, statistical distribution, or live-server end-to-end test yet.

## Simulator contract

- Parent selectors expose sex and representative alleles, not every production profile.
- Breed is disabled for same-sex parents.
- Child sex comes from the seeded RNG.
- Male child receives the X-linked coat allele from the mother.
- Female child receives one coat allele from each parent and can display `calico` for orange/black.
- Maternal vigor is copied from the mother.
- The result includes the seed so the cross is reproducible.
- Reset restores the initial example and rerolls only when Breed is pressed.

## Technical boundary

- One HTML file containing semantic HTML, inline CSS, and inline JavaScript.
- JavaScript owns only the small presentation model and simulator state; no production source is modified.
- Use native controls and keyboard-accessible buttons.
- Keep all claims visible in the page grounded in the current repository and label the simulator as illustrative.

## Verification

- Confirm the file exists and contains no external `<script src>` or stylesheet dependencies.
- Open it in Chromium through the browser tool.
- Verify the page renders at desktop and narrow viewport sizes.
- Verify Breed changes the result for a changed seed, same-sex parents show a clear rejection, and a valid cross shows inherited alleles plus phenotype.
