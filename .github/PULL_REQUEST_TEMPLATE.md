## What

<!-- One paragraph: what changed and why. Link the issue if there is one. -->

## How it was verified

- [ ] `./gradlew build` is green (unit + golden tests, ktlint)
- [ ] Generator or parser change: golden files regenerated with `./gradlew updateGolden` and the diff reviewed
- [ ] Generated output still builds: `heno generate` + `heno verify` on `fixtures/henosis.yaml` (or e2e is green)
- [ ] Docs updated (`docs/`, `CHANGELOG.md` under Unreleased)

## Clean room

- [ ] This contribution contains no code, assets or internal knowledge from an employer or client.
