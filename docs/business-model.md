# Business Model: Health Access Navigation

## Classification

- Repository: `cloud-itonami-isic-8691`
- ISIC Rev.5: `8691`
- Activity: intermediation service activities for medical, dental and other
  human-health services
- Social impact: access to care, reduced missed appointments, better referral
  follow-up

## Customer

- municipalities
- clinics and provider networks
- NPOs
- insurers and benefit programs
- schools and employers with care-navigation needs

## Offer

- consented intake workflow
- provider directory and eligibility matching
- referral and appointment support
- follow-up queue
- access gap reporting
- operator training

## Revenue

- setup fee
- monthly navigation platform fee
- per-referral support fee
- reporting package
- provider-directory maintenance

## Trust Controls

- no diagnosis by LLM
- urgent-risk escalation cannot be suppressed
- health data disclosure requires consent and purpose
- referral recommendations cite source eligibility rules
- a fabricated jurisdiction citation, incomplete evidence, an eligibility
  determination that has aged past its own validity window, or an
  unresolved urgent health risk -- each forces a hold, not an override
- referral finalization and health-information disclosure are logged and
  escalated, and neither can be finalized twice for the same seeker: a
  double-referral or double-disclosure attempt is held off this actor's
  own seeker facts alone, with no upstream comparison needed

## Health Access Governor: decision rule

`blueprint.edn` fixes `:itonami.blueprint/governor` to `:health-access-
governor` -- this is not a generic "review step," it is the one gate every
proposed action in this business must pass before a referral is finalized
or health information is disclosed. The governor sits between the Care
Navigator and execution, per the README's Core Contract:

```text
Care Navigator -> Health Access Governor -> refer, hold, or human review
```

**Approves**: routine navigation actions proposed against a seeker that
already has a consented intake record on file, a current (unexpired)
eligibility determination, and no unresolved urgent health risk -- e.g. an
eligibility checklist draft, a provider match. These proceed straight to
the referral ledger / follow-up queue.

**Rejects or escalates**: the governor refuses to let the advisor finalize
a referral or disclose health information on its own authority when any of
the following hold -- a fabricated eligibility spec-basis; incomplete
evidence; an eligibility determination that has aged past its own validity
window; an unresolved urgent health risk surfaced during triage. A clean
referral/disclosure proposal still always routes to a human -- neither
`:actuation/finalize-referral` nor `:actuation/disclose-health-information`
is ever auto-committed, at any rollout phase.
