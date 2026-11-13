# Koral Solana release checklist

Apply [organization release policy v1](https://github.com/koral-toolchain/handbook/blob/main/guides/releases.md)
and the [versioning contract](https://github.com/koral-toolchain/handbook/blob/main/guides/versioning.md).
This is an owner checklist, not an automatic publisher. No release is authorized
by merging this document or a product pull request.

## Select a useful release

- Group related Unreleased changes around an outcome a developer can try.
  Do not publish or announce every merged PR. Review readiness roughly every two
  weeks and skip publication when there is insufficient useful change.
- Classify source API, CLI, environment and ABI/model compatibility changes.
  Keep preview versions explicitly prerelease; stable requires a deliberate
  supported-contract decision. Update duplicated version declarations together.
- @tidemark-labs approves the exact version, source commit and candidate identity.
  Record the handbook revision/commit used for the decision.

## Verify the candidate

- [ ] Select the reviewed main commit. Its required CI must succeed, including
  policy, unit, native integration, consumer installation, compatibility and the
  final required gate. Failed, cancelled, missing or skipped results block release.
- [ ] Use the exact CLI TAR produced by that CI run. Verify artifact identity and
  SHA-256; do not substitute an unverified rebuild after approval.
- [ ] Review installed-candidate init/setup/check/build/test evidence on the
  minimum supported host. Distinguish hosted Linux pairs from local macOS evidence;
  do not imply an untested platform matrix.
- [ ] Confirm native Counter, typed state and System payment regressions, including
  permissions, checked arithmetic and CPI rollback. For a candidate containing
  external account constraints, include owner/address/data-size/executable rejection, aliases
  and later body-failure rollback tests.
- [ ] Review benchmark evidence and any performance change using equivalent
  inputs and safety conditions. Explain changed validation costs and comparison
  limitations. Artifact parity is not a newly measured CU benchmark.
- [ ] Check LICENSE, complete third-party notices, archive contents and supported
  examples. Exclude credentials, private history, SDKs, wallets and caches.
- [ ] Align CHANGELOG, usage docs, CLI/Gradle/manifest versions, tag and release
  notes. Explain compatibility/migration, including internal model or ABI changes.

See [the actual CI contract](CI.md) for jobs and reproduction commands. The product
pins an explicit handbook checker; a new policy document does not silently update
that pin. Documentation correctness, benchmark interpretation, exact artifact
promotion and release approval remain owner checks. There is no automatic package
publishing workflow, Maven/Plugin Portal release or live-chain deployment here.

## Publish and announce

- Create an owner-approved GitHub prerelease first while this product is a preview.
  Include tested source/CI, install steps, changes, supported environments,
  limitations, checksums and provenance. Promote the verified bytes.
- Download the public assets again and verify hashes and install links. Existing
  published tags and bytes must not be moved/replaced with different content.
- Only then label the covered CHANGELOG entries as released. Source-only features
  stay explicitly Unreleased until a downloadable version includes them.
- A GitHub Release is required for every published version. A homepage Updates
  article and X post are optional, grouped around meaningful benefits with an
  actual runnable example and a matching download. They are separate publishing
  actions, not automatic effects of a product release.

For security, incorrect execution results or installation-blocking defects, use
an urgent patch without waiting for the routine review. Keep relevant checks and
owner approval, communicate impact/known-good versions as needed, and correct a
bad release with a new version. Do not overwrite the old release in place.

After the next real release, review this checklist and improve only what the
experience shows is missing. This policy task itself does not ship a new version.
