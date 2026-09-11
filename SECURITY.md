# Security Policy

## Reporting a Vulnerability

Please **do not open a public issue** for security vulnerabilities. Email **contribution@nubons.com** with a description, impact, and reproduction steps. We aim to acknowledge within 5 working days.

## Zero-Secrets Mandate

This repository must never contain secrets, API tokens, passwords, private keys, `.env` files, or deployment credentials. Configuration is supplied at runtime via environment variables or secure credential stores.

Any submitted pull request containing committed credentials or tokens will be immediately rejected.
