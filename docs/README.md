# Developer Documentation

Start with [the developer guide](guides/index.md). Chapters cover installation, supported language, state/account/CPI behavior, CLI artifacts, tests/errors, actual compiler architecture, and the separate browser adapter.

The guide Markdown is canonical and stays with the independently extractable utility. Technical references [LANGUAGE.md](LANGUAGE.md), [ARCHITECTURE.md](ARCHITECTURE.md), and [MVP.md](MVP.md) retain their more detailed contracts. guides/SOURCE-MAP.json records inspected implementation hashes so a future update can identify changed sources. Browser-adapter source is separate from the utility and is a development snapshot.

The [website](https://koral.tools) is maintained in the separate website repository.
Its rendering and deployment pipeline is not included in this product checkout.
Documentation changes must be explicitly synchronized to that repository when
updating the hosted guide; changing product Markdown alone does not deploy the site.
The website and renderer are not required to build or use the compiler.

Paths in guides/SOURCE-MAP.json retain the original review workspace layout.
The external website adapter entries describe reviewed sources, not files bundled
with this product.

No public hosting, package coordinate, arbitrary CPI, general Kotlin compatibility, or production-readiness claim follows from this documentation.

Release maintainers use [RELEASING.md](RELEASING.md), which applies the organization
release policy to this compiler and separates mandatory evidence from selective
announcements. A merged source change is not a package release.

[The SOL vault guide](VAULT.md) covers owned-state deposits/withdrawals,
local verification, authority/rent checks and selected-update comparison evidence.

[The 0.2.0-preview.1 release notes](release-candidates/0.2.0-preview.1.md) record the published
preview, compatibility and exact verified source. See CHANGELOG.md for the version history.

[Custom programs](CUSTOM-PROGRAM.md) covers the Unreleased user-authored test scaffold
and ABI client; this workflow is not in the published 0.2.0-preview.1 archive.
