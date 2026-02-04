# Security Policy

## Reporting a Vulnerability

If you discover a security issue in Constellation Engine, please report it
responsibly. **Do not open a public GitHub issue for security concerns.**

### How to Report

Email the project maintainers using the contact information in the repository's
GitHub profile, with the subject line: `[SECURITY] <brief description>`.

Please include:

1. **Description** of the issue
2. **Steps to reproduce** (if applicable)
3. **Affected versions** (e.g., 0.4.0, all versions)
4. **Impact assessment** — what an attacker could achieve
5. **Suggested fix** (if you have one)

### Response Timeline

- **Acknowledgement:** Within 48 hours of receiving the report
- **Initial assessment:** Within 1 week
- **Fix and release:** Depends on severity, typically within 2-4 weeks for
  critical issues

### What to Expect

- We will acknowledge receipt of your report promptly
- We will work with you to understand and validate the issue
- We will credit you in the release notes (unless you prefer to remain anonymous)
- We will coordinate disclosure timing with you

## Supported Versions

| Version | Supported |
|---------|-----------|
| 0.4.x   | Yes       |
| < 0.4   | No        |

## Security Model

For a detailed description of the trust model, sandboxing properties, HTTP
hardening features, and security recommendations, see
[docs/security.md](docs/security.md).

### Key Points

- **constellation-lang scripts are sandboxed** — they cannot execute arbitrary
  code, access the filesystem, or make network calls
- **Module implementations run with full JVM permissions** — review all module
  code before registering in production
- **HTTP security features are opt-in** — authentication, CORS, and rate
  limiting are disabled by default
- **No telemetry or phone-home behavior** — the library makes no outbound
  network calls

## Dependencies

Constellation Engine depends on the Typelevel ecosystem (cats, cats-effect,
http4s, circe). All dependencies are open-source with permissive licenses
(MIT/Apache 2.0). We monitor for known issues in upstream dependencies.
