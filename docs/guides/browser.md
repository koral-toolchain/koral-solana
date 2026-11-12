# Browser preview

The website has a separate preview client and server adapter. This is distinct from the local CLI, and the static documentation itself does not compile code.

## Allowlisted examples

The current adapter code accepts the counter and payments example IDs. It does not accept arbitrary Kotlin source. Source shown by the client comes from the server's configured examples directory.

The worker invokes the independently packaged compiler and local VM verifier. A result can come from the adapter's in-memory cache; cached status must not be described as a fresh compilation.

## Adapter routes

| Method and path | Current source contract |
| --- | --- |
| GET /api/health | Compiler version, allowlisted mode, and onChain false |
| GET /api/examples | Supported IDs, labels, and source |
| POST /api/compile | A JSON object containing only the example ID |

```json
{"example":"counter"}
```

Browser requests are same-origin. The adapter rejects custom-source payloads, enforces a small request bound, limits submissions, and runs one worker at a time. Docker workers are configured without networking and with read-only/rootless execution settings and resource limits. These source-level controls are not a completed security audit.

## When the service is unavailable

A static file server can display this documentation and website but does not implement /api routes. The preview client reports backend failures; follow [the local CLI guide](quickstart.md) to compile without the web adapter.

The client and adapter are under active development. Service deployment and successful compilation in a particular browser session must be verified separately. This documentation describes inspected code, not a promise that a public hosted compiler is online.

Implementation basis: design/website/playground.js and the separate web-service/server.py. No wallet, live-chain transaction, or arbitrary public code-execution endpoint is implied.
