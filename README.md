# cloud-itonami-8691

Open Business Blueprint for **ISIC Rev.5 8691**: intermediation service
activities for medical, dental and other human-health services.

This repository designs a forkable OSS business for care navigation, referral
coordination, appointment support, and evidence-backed health access workflows.

## Core Contract

```text
user need + consent + provider directory + eligibility rules
        |
        v
Care Navigator -> Health Access Governor -> refer, hold, or human review
        |
        v
referral ledger + follow-up queue
```

The advisor can help route people to services, but cannot diagnose, conceal
urgent risk, or disclose health information outside consent and purpose.

## Runbook

- Start with provider directory and synthetic cases.
- Add consented intake and eligibility checks.
- Add referral queues with human review.
- Add follow-up and access-outcome reporting.

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md).

## License

Code and implementation templates are AGPL-3.0-or-later.
