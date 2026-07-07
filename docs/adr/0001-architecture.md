# ADR-0001: Care Navigator ⊣ Health Access Governor architecture

## Status

Accepted. `cloud-itonami-isic-8691` promoted from `:blueprint` to
`:implemented` in the `kotoba-lang/industry` registry.

## Context

`cloud-itonami-isic-8691` publishes an OSS business blueprint for
health-access navigation: care navigation, referral coordination,
appointment support and evidence-backed health-access workflows,
helping people (uninsured, underinsured, or otherwise underserved)
find and enroll in the right health-benefits program or care provider.
Like every prior actor in this fleet, the blueprint alone is not an
implementation: this ADR records the governed-actor architecture that
promotes it to real, tested code, following the same langgraph-clj
StateGraph + independent Governor + Phase 0→3 rollout pattern
established by `cloud-itonami-isic-6511` (life insurance) and applied
across fifty prior siblings, most recently `cloud-itonami-isic-8810`
(community care coordination).

## Decision

### Decision 1: entity and op shape

The primary entity is a `seeker` (a person seeking health-access
navigation). Five ops: `:seeker/intake` (directory upsert, no capital
risk), `:eligibility/verify` (per-jurisdiction health-access-
navigation evidence checklist, never auto), `:risk/screen` (urgent-
health-risk screening, unconditional-evaluation discipline, never
auto), `:actuation/finalize-referral` (POSITIVE, high-stakes --
routing a seeker to a real care provider), and `:actuation/disclose-
health-information` (POSITIVE, high-stakes -- disclosing a real
seeker's health information to a third party under consent). This
matches the dual-actuation-on-one-entity shape every recent dual-
actuation sibling uses, grounded directly in this blueprint's own
published Core Contract ("The advisor can help route people to
services, but cannot diagnose, conceal urgent risk, or disclose health
information outside consent and purpose") and Trust Controls
("referral recommendations cite source eligibility rules", "health
data disclosure requires consent and purpose").

### Decision 2: `eligibility-window-elapsed-exceeds-validity?` -- the 6th MAXIMUM-ceiling check

Following `facility.registry/occupancy-exceeds-capacity?` (1st),
`school.registry/class-size-exceeds-maximum?` (2nd), `card.registry/
settlement-amount-exceeds-authorized?` (3rd), `recovery.registry/
contamination-percentage-exceeds-maximum?` (4th) and `care.registry/
caregiver-workload-exceeds-maximum?` (5th), `navigator.registry/
eligibility-window-elapsed-exceeds-validity?` applies the SAME
ceiling-only comparison to a seeker's own elapsed days since
eligibility was determined against their own recorded eligibility-
validity-window days -- a direct, natural mapping onto real health-
benefits practice (an eligibility determination that has aged past
its own validity window must be re-verified before a real referral is
finalized on it, not silently acted on as still current). Gates only
`:actuation/finalize-referral`.

### Decision 3: `urgent-health-risk-unresolved-violations` -- the 35th unconditional-evaluation screening grounding, correctly distinguished from two adjacent siblings

Before writing this check, this fleet's existing `-unresolved-
violations` check names were grepped in full (`academic-integrity-
flag`, `adverse-credit-flag`, `allergy-flag`, `billing-dispute`,
`complaint`, `compliance-flag`, `conflict-of-interest`, `contamination-
flag`, `fraud-flag`, `grid-instability-flag`, `incident-flag`,
`integrity-flag`, `medication-adherence-flag`, `ndt-defect`, `patron-
flag`, `process-defect-flag`, `rights-clearance`, `risk-flag`,
`safeguarding-concern`, `safeguarding-signal`, `surveillance-flag`,
`threshold-breach`, `welfare-flag`). Two are semantically closest and
needed explicit distinguishing:

- `casework.governor/risk-flag-unresolved-violations` (read directly,
  not just grepped) verifies a fraud/misrepresentation risk against an
  eligibility determination -- a financial-integrity concept.
- `care.governor/safeguarding-signal-unresolved-violations`/
  `congregation.governor/safeguarding-concern-unresolved-violations`
  verify neglect/abuse-adjacent safeguarding concerns.

This actor's `urgent-health-risk-unresolved-violations` verifies a
distinct concept: an urgent HEALTH/safety risk surfaced during triage
(e.g. an acute symptom or crisis flag requiring emergency escalation
rather than routine referral), grounded directly in this blueprint's
own Trust Control "urgent-risk escalation cannot be suppressed" and
operator-guide's own Certification requirement ("that urgent risks
escalate to humans"). It reuses the unconditional-evaluation
DISCIPLINE (`casualty.governor/sanctions-violations`'s original fix)
for the 35th distinct application overall, continuing the count
established across this fleet's builds (water=25th, telecom=26th,
aerospace=27th, recovery=28th, consulting=29th, union=30th,
congregation=31st, fab=32nd, energy=33rd, care=34th,
navigator=35th). Gates `:risk/screen` and `:actuation/finalize-
referral` specifically -- matching the Core Contract's own "cannot
... conceal urgent risk" invariant.

### Decision 4: dedicated double-actuation-guard booleans

`:referral-finalized?`/`:disclosure-made?` are dedicated booleans on
the `seeker` record, never a single `:status` value -- the same
discipline every prior sibling governor's guards establish, informed
by `cloud-itonami-isic-6492`'s real status-lifecycle bug
(ADR-2607071320).

### Decision 5: Store protocol, MemStore + DatomicStore parity

`navigator.store/Store` is implemented by both `MemStore` (atom-
backed, default for dev/tests/demo) and `DatomicStore` (`langchain.
db`-backed), proven to satisfy the same contract in `test/navigator/
store_contract_test.clj` -- the same seam every sibling actor uses so
swapping the SSoT backend is a configuration change, not a rewrite.
Unlike `care.store` (ISIC 8810, this fleet's immediately prior build),
the per-entity accessor here is safely named `seeker` directly --
`seeker` is not a Clojure special form, so no `-of` suffix workaround
was needed (that workaround was specific to `care.store`'s `case`
entity colliding with `clojure.core/case`).

### Decision 6: Phase 0→3 rollout

Phase 3's `:auto` set has exactly one member, `:seeker/intake` (no
capital risk). `:eligibility/verify` and `:risk/screen` are never
auto-eligible at any phase (matching every sibling's screening-op
posture), and `:actuation/finalize-referral`/`:actuation/disclose-
health-information` are permanently excluded from every phase's
`:auto` set -- a structural fact, not a rollout milestone, enforced by
BOTH `navigator.phase` and `navigator.governor`'s `high-stakes` set
independently.

### Decision 7: no bespoke domain capability lib

This vertical's seeker records are practice-specific rather than a
shared cross-operator data contract, so `navigator.*` runs on the
generic robotics/identity/forms/dmn/bpmn/audit-ledger/optimization
stack only -- the same posture `9412`/`8720`/`8521`/`3030`/`3830`/
`7020`/`9420`/`9491`/`3512`/`8810` and others without a bespoke
capability lib already establish.

### Decision 8: mock + LLM advisor pair

`navigator.navigatoradvisor` provides `mock-advisor` (deterministic,
default everywhere -- the actor graph and governor contract run
offline) and `llm-advisor` (backed by `langchain.model/ChatModel`,
with a defensive EDN-proposal parser so a malformed LLM response
degrades to a safe low-confidence noop rather than ever auto-
finalizing a referral or auto-disclosing health information).

### Decision 9: blueprint.edn field-sync fixes

Two stale-scaffold inconsistencies in `blueprint.edn`, discovered
during the standard "survey blueprint scaffold" step before writing
any code, were fixed as part of this promotion (the same class of fix
`card.6619`'s, `water.3600`'s, `telecom.6190`'s, `aerospace.3030`'s,
`fab.2610`'s, `energy.3512`'s and `care.8810`'s own ADR-0001s
document):

1. `:itonami.blueprint/id` was the stale pre-rename value
   `"cloud-itonami-8691"` (missing `isic-`), while the repo folder,
   README title and this actor's own `:business-id` already use the
   corrected `cloud-itonami-isic-8691`. Fixed to match.
2. `:itonami.blueprint/required-technologies`/`:optional-technologies`
   were missing entirely despite the `kotoba-lang/industry` registry's
   own entry for `"8691"` already stating `[:robotics :identity :forms
   :dmn :bpmn :audit-ledger :optimization]` / `[:telemetry]`. Fixed to
   match the registry exactly.

## Alternatives considered

- **Reusing `casework.governor/risk-flag-unresolved-violations`'s
  name for this actor's screening check.** Rejected: casework's check
  is specifically a fraud/misrepresentation-risk concept against an
  eligibility determination, not a health/safety-risk concept -- the
  precedent-verification discipline (established by `leasing`'s
  ADR-0001, reinforced by `union`'s, `congregation`'s, `fab`'s and
  `care`'s) requires checking the actual sibling semantics, not just
  the shared word "risk," before claiming reuse or novelty.
- **A single "seeker-safety" check merging eligibility-window and
  urgent-health-risk concerns.** Rejected: eligibility-window elapsed
  is a ground-truth numeric recompute needing no proposal inspection;
  urgent-health-risk status is an unconditionally-evaluated flag that
  must also HARD-hold the screening op itself on its own finding --
  merging them would lose the screening op's self-hold property, the
  same reasoning `care`'s and `energy`'s ADR-0001s document for their
  own analogous ground-truth/unconditional-flag distinctions.
- **Modeling a third actuation for "urgent-risk escalation"** (as its
  own real-world act, alongside referral and disclosure). Rejected for
  this R0: escalating an urgent risk is exactly what the unconditional
  `urgent-health-risk-unresolved` HARD-hold already forces (the
  referral cannot finalize until the risk is addressed and re-
  screened), so a separate escalation actuation would duplicate the
  same invariant with a less-established actuation shape.

## Consequences

- Fifty-first actor in this fleet (50 implemented before this build).
- Confirms the MAXIMUM-ceiling check family generalizes to a sixth,
  genuinely distinct domain (eligibility-window currency).
- Establishes a genuinely NEW unconditional-evaluation-screening
  concept (urgent-health-risk), explicitly distinguished from two
  semantically adjacent siblings (`casework`'s fraud-risk-flag,
  `care`'s/`congregation`'s safeguarding concepts) by reading their
  actual source, not just grepping a shared word.
- `MemStore` ‖ `DatomicStore` parity is proven by `test/navigator/
  store_contract_test.clj`, the same `:db-api`-driven swap pattern
  every sibling actor uses.
- Two pre-existing `blueprint.edn` inconsistencies (stale ID, missing
  required/optional-technologies fields) fixed as in-scope minor
  consistency work.
