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
