# cloud-itonami-8691

Open Business Blueprint for **ISIC Rev.5 8691**: intermediation service
activities for medical, dental and other human-health services.

This repository designs a forkable OSS business for care navigation, referral
coordination, appointment support, and evidence-backed health access workflows.

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot performs
the physical domain work**. Here a triage and navigation robot performs check-in, vitals capture and wayfinding in care settings under an actor that proposes
actions and an independent **Health Access Governor** that gates them. The governor never
dispatches hardware itself; `:high`/`:safety-critical` actions (such as
operating near patients, medical devices or vulnerable people) require human sign-off.

A live sample of the operator console (robotics safety console, shared template) is rendered in [docs/samples/operator-console.html](docs/samples/operator-console.html) — pure-data HTML output of `kotoba.robotics.ui`.

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
