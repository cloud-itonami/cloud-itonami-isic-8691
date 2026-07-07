# cloud-itonami-isic-8691

Open Business Blueprint for **ISIC Rev.5 8691**: intermediation
service activities for medical, dental and other human-health
services.

This repository publishes a health-access-navigation actor -- seeker
intake, eligibility-determination assessment, urgent-health-risk
screening, referral finalization and health-information disclosure --
as an OSS business that any qualified navigation operator can fork,
deploy, run, improve and sell, so a community or independent provider
never surrenders seeker data and ledgers to a closed SaaS.

Built on this workspace's
[`langgraph-clj`](https://github.com/com-junkawasaki/langgraph-clj)
StateGraph runtime (portable `.cljc`, supervised superstep loop,
interrupts, Datomic/in-mem checkpoints) -- the same actor pattern as
every prior actor in this fleet
([`cloud-itonami-isic-6511`](https://github.com/cloud-itonami/cloud-itonami-isic-6511),
[`6512`](https://github.com/cloud-itonami/cloud-itonami-isic-6512),
[`6621`](https://github.com/cloud-itonami/cloud-itonami-isic-6621),
[`6622`](https://github.com/cloud-itonami/cloud-itonami-isic-6622),
[`6629`](https://github.com/cloud-itonami/cloud-itonami-isic-6629),
[`6520`](https://github.com/cloud-itonami/cloud-itonami-isic-6520),
[`6530`](https://github.com/cloud-itonami/cloud-itonami-isic-6530),
[`6820`](https://github.com/cloud-itonami/cloud-itonami-isic-6820),
[`6612`](https://github.com/cloud-itonami/cloud-itonami-isic-6612),
[`6492`](https://github.com/cloud-itonami/cloud-itonami-isic-6492),
[`6920`](https://github.com/cloud-itonami/cloud-itonami-isic-6920),
[`6611`](https://github.com/cloud-itonami/cloud-itonami-isic-6611),
[`7120`](https://github.com/cloud-itonami/cloud-itonami-isic-7120),
[`8620`](https://github.com/cloud-itonami/cloud-itonami-isic-8620),
[`8530`](https://github.com/cloud-itonami/cloud-itonami-isic-8530),
[`9200`](https://github.com/cloud-itonami/cloud-itonami-isic-9200),
[`7500`](https://github.com/cloud-itonami/cloud-itonami-isic-7500),
[`9603`](https://github.com/cloud-itonami/cloud-itonami-isic-9603),
[`9521`](https://github.com/cloud-itonami/cloud-itonami-isic-9521),
[`9321`](https://github.com/cloud-itonami/cloud-itonami-isic-9321),
[`8730`](https://github.com/cloud-itonami/cloud-itonami-isic-8730),
[`9102`](https://github.com/cloud-itonami/cloud-itonami-isic-9102),
[`9103`](https://github.com/cloud-itonami/cloud-itonami-isic-9103),
[`9602`](https://github.com/cloud-itonami/cloud-itonami-isic-9602),
[`9000`](https://github.com/cloud-itonami/cloud-itonami-isic-9000),
[`8890`](https://github.com/cloud-itonami/cloud-itonami-isic-8890),
[`8610`](https://github.com/cloud-itonami/cloud-itonami-isic-8610),
[`9311`](https://github.com/cloud-itonami/cloud-itonami-isic-9311),
[`8510`](https://github.com/cloud-itonami/cloud-itonami-isic-8510),
[`9412`](https://github.com/cloud-itonami/cloud-itonami-isic-9412),
[`6491`](https://github.com/cloud-itonami/cloud-itonami-isic-6491),
[`8720`](https://github.com/cloud-itonami/cloud-itonami-isic-8720),
[`8521`](https://github.com/cloud-itonami/cloud-itonami-isic-8521),
[`6619`](https://github.com/cloud-itonami/cloud-itonami-isic-6619),
[`3600`](https://github.com/cloud-itonami/cloud-itonami-isic-3600),
[`6190`](https://github.com/cloud-itonami/cloud-itonami-isic-6190),
[`3030`](https://github.com/cloud-itonami/cloud-itonami-isic-3030),
[`3830`](https://github.com/cloud-itonami/cloud-itonami-isic-3830),
[`7020`](https://github.com/cloud-itonami/cloud-itonami-isic-7020),
[`9420`](https://github.com/cloud-itonami/cloud-itonami-isic-9420),
[`9491`](https://github.com/cloud-itonami/cloud-itonami-isic-9491),
[`2610`](https://github.com/cloud-itonami/cloud-itonami-isic-2610),
[`3512`](https://github.com/cloud-itonami/cloud-itonami-isic-3512),
[`8810`](https://github.com/cloud-itonami/cloud-itonami-isic-8810)) --
here it is **Care Navigator ⊣ Health Access Governor**.

> **Why an actor layer at all?** An LLM is great at drafting a seeker-
> intake summary, normalizing records, and checking whether a
> seeker's own recorded eligibility determination actually stays
> within their own recorded validity window -- but it has **no notion
> of which jurisdiction's health-access-navigation and eligibility-
> determination law is official, no license to finalize a real
> referral or disclose real health information, and no way to know on
> its own whether an urgent health risk against a seeker has actually
> stayed unresolved**. Letting it finalize a referral or disclose
> health information directly invites fabricated regulatory citations,
> a stale eligibility determination being acted on past its own
> validity window, and an urgent health risk being quietly overlooked
> -- and liability, and seeker-safety risk, for whoever runs it. This
> project seals the NavigatorOps-LLM into a single node and wraps it with
> an independent **Health Access Governor**, a human **approval
> workflow**, and an immutable **audit ledger**.

## Scope: what this actor does and does not do

This actor covers seeker intake through eligibility-determination
assessment, urgent-health-risk screening, referral finalization and
health-information disclosure. It does **not**, by itself, hold any
registration required to operate as a health-access-navigation
provider in a given jurisdiction, and it does not claim to. It also
does **not** diagnose, provide medical judgment, or model the actual
care delivery itself -- `navigator.registry/eligibility-window-
elapsed-exceeds-validity?` is a pure ceiling recompute against the
seeker's own recorded fields, not a clinical assessment. Whoever
deploys and operates a live instance (a registered health-access-
navigation provider) supplies any jurisdiction-specific registration,
the real clinical judgment and the real health-system integrations,
and bears that jurisdiction's liability -- the software supplies the
governed, spec-cited, audited execution scaffold so that provider does
not have to build the compliance layer from scratch.

### Actuation

**Finalizing a real referral or disclosing real health information is
never autonomous, at any phase, by construction.** Two independent
layers enforce this (`navigator.governor`'s `:actuation/finalize-
referral`/`:actuation/disclose-health-information` high-stakes gate
and `navigator.phase`'s phase table, which never puts `:actuation/
finalize-referral`/`:actuation/disclose-health-information` in any
phase's `:auto` set) -- see `navigator.phase`'s docstring and
`test/navigator/phase_test.clj`'s `finalize-referral-never-auto-at-
any-phase`/`disclose-health-information-never-auto-at-any-phase`. The
actor may draft, check and recommend; a human care-navigator/clinical
lead is always the one who actually finalizes a referral or discloses
health information. Like `6512`/`6622`/`6520`/`6530`/`6820`/`6920`/
`6611`/`8530`/`9200`/`9521`/`8730`/`9102`/`9103`/`8890`/`8610`/`8510`/
`9412`/`8720`/`8521`/`6619`/`3600`/`6190`/`3030`/`3830`/`9420`/`9491`/
`2610`/`3512`/`8810`, this actor has TWO actuation events, both
POSITIVE (finalizing/disclosing a real record), matching the majority
pattern in this fleet (`3600`/`6190` are the fleet's two NEGATIVE-
actuation exceptions).

## The core contract

```
seeker intake + jurisdiction facts (navigator.facts, spec-cited)
        |
        v
   ┌──────────────┐   proposal      ┌───────────────────────┐
   │ Care         │ ─────────────▶ │ Health Access                 │  (independent system)
   │ Navigator    │  + citations    │ Governor:                    │
   │ (sealed)     │                 │ spec-basis · evidence-       │
   └──────────────┘         commit ◀────┼──────────▶ hold │ incomplete ·
                                 │             │           │ eligibility-window-
                           record + ledger  escalate ─▶ human   elapsed-exceeds-
                                             (ALWAYS for         validity (ceiling) ·
                                              :actuation/finalize-       urgent-health-risk-
                                              referral /                unresolved
                                              :actuation/disclose-       (unconditional) ·
                                              health-information)        already-referred/
                                                                          -disclosed
```

**The NavigatorOps-LLM never finalizes a referral or discloses health
information the Health Access Governor would reject, and never does
so without a human sign-off.** Hard violations (fabricated regulatory
requirements; unsupported evidence; an eligibility determination past
its own validity window; an unresolved urgent health risk; a double
referral or disclosure) force **hold** and *cannot* be approved past;
a clean referral/disclosure proposal still always routes to a human.

## Run

```bash
clojure -M:dev:run     # walk one clean dual-actuation lifecycle + five HARD-hold cases through the actor
clojure -M:dev:test    # governor contract · phase invariants · store parity · registry conformance · facts coverage
clojure -M:lint        # clj-kondo (errors fail; CI mirrors this)
```

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot
performs the physical domain work**. Here a triage and navigation
robot performs check-in, vitals capture and wayfinding in care
settings under the actor, gated by the independent **Health Access
Governor**. The governor never dispatches hardware itself; `:high`/
`:safety-critical` actions (such as operating near patients, medical
devices or vulnerable people) require human sign-off.

A live sample of the operator console (robotics safety console, shared
template) is rendered in
[docs/samples/operator-console.html](docs/samples/operator-console.html)
-- pure-data HTML output of `kotoba.robotics.ui`.

## Open business

This repository is not only source code. It is a public, forkable
business model:

| Layer | What is open |
|---|---|
| OSS core | Actor runtime, Health Access Governor, referral + disclosure draft records, audit ledger |
| Business blueprint | Customer, offer, pricing, unit economics, sales motion |
| Operator playbook | How to fork, license, deploy and support the service in a jurisdiction |
| Trust controls | Governance, security reporting, actuation invariant, audit requirements |

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md) to start this as an
open business on itonami.cloud, and
[`docs/adr/0001-architecture.md`](docs/adr/0001-architecture.md) for the
full architecture and decision record.

## Capability layer

This blueprint resolves its technology stack via
[`kotoba-lang/industry`](https://github.com/kotoba-lang/industry) (ISIC
`8691`). This vertical's seeker records are practice-specific rather
than a shared cross-operator data contract, so `navigator.*` runs on
the generic robotics/identity/forms/dmn/bpmn/audit-ledger/optimization
stack only -- no bespoke domain capability lib to reference at all.

## Layout

| File | Role |
|---|---|
| `src/navigator/store.cljc` | **Store** protocol -- `MemStore` ‖ `DatomicStore` (`langchain.db`) + append-only audit ledger + separate referral/disclosure history. No dynamically-filed sub-record -- both actuation ops act directly on a pre-seeded seeker, and the double-actuation guards check dedicated `:referral-finalized?`/`:disclosure-made?` booleans rather than a `:status` value |
| `src/navigator/registry.cljc` | Referral + disclosure draft records, plus `eligibility-window-elapsed-exceeds-validity?` -- the SIXTH instance of this fleet's MAXIMUM-ceiling check family (`facility`/`school`/`card`/`recovery`/`care` established the first five) |
| `src/navigator/facts.cljc` | Per-jurisdiction health-access-navigation/eligibility-determination catalog with an official spec-basis citation per entry, honest coverage reporting |
| `src/navigator/navigatoradvisor.cljc` | **NavigatorOps-LLM** -- `mock-advisor` ‖ `llm-advisor`; intake/eligibility-verification/urgent-risk-screening/referral-finalization/disclosure proposals |
| `src/navigator/governor.cljc` | **Health Access Governor** -- 4 HARD checks (spec-basis · evidence-incomplete · eligibility-window-elapsed-exceeds-validity, pure ground-truth ceiling recompute · urgent-health-risk-unresolved, unconditional evaluation, the THIRTY-FIFTH grounding of this discipline, distinct from `casework`'s fraud-risk-flag concept and `care`'s/`congregation`'s safeguarding concepts) + already-referred/already-disclosed guards + 1 soft (confidence/actuation gate) |
| `src/navigator/phase.cljc` | **Phase 0→3** -- read-only → assisted intake → assisted verify → supervised (both referral finalization and health-information disclosure always human; seeker intake is the ONLY auto-eligible op, no direct capital risk) |
| `src/navigator/operation.cljc` | **OperationActor** -- langgraph-clj StateGraph |
| `src/navigator/sim.cljc` | demo driver |
| `test/navigator/*_test.clj` | governor contract · phase invariants · store parity · registry conformance · facts coverage |

## Business-process coverage (honest)

This actor covers seeker intake through eligibility-determination
assessment, urgent-health-risk screening, referral finalization and
health-information disclosure -- the core governed lifecycle this
blueprint's own `docs/business-model.md` names as its Offer:

| Covered | Not covered (out of scope for this R0) |
|---|---|
| Seeker intake + per-jurisdiction eligibility checklisting, HARD-gated on an official spec-basis citation (`:seeker/intake`/`:eligibility/verify`) | Real health-system integration, real clinical care delivery itself (see `navigator.facts`'s docstring) |
| Urgent-health-risk screening, evaluated unconditionally so the screening op itself can HARD-hold on its own finding (`:risk/screen`) | Any diagnosis or medical judgment itself -- deliberately outside this actor's competence |
| Referral finalization, HARD-gated on full evidence and the seeker's own eligibility-window currency, plus a double-referral guard (`:actuation/finalize-referral`) | |
| Health-information disclosure, HARD-gated on full evidence and consent/purpose-limitation, plus a double-disclosure guard (`:actuation/disclose-health-information`) | |
| Immutable audit ledger for every intake/verification/screening/referral/disclosure decision | |

Extending coverage is additive: add the next gate (e.g. a follow-up-
outcome check) as its own governed op with its own HARD checks and
tests, following the SAME "an independent governor re-verifies against
the actor's own records before any real-world act" pattern this
repo's flagship op already establishes.

## Jurisdiction coverage (honest)

`navigator.facts/coverage` reports how many requested jurisdictions
actually have an official spec-basis in `navigator.facts/catalog` --
currently 4 seeded (JPN, USA, GBR, DEU) out of ~194 jurisdictions
worldwide. This is a starting catalog to prove the governor contract
end-to-end, not a claim of global coverage. Adding a jurisdiction is
additive: one map entry in `navigator.facts/catalog`, citing a real
official source -- never fabricate a jurisdiction's requirements to
make coverage look bigger.

## Maturity

`:implemented` -- `NavigatorOps-LLM` + `Health Access Governor` run as
real, tested code (see `Run` above), promoted from the originally-
published `:blueprint`-tier scaffold, modeled closely on the fifty
prior actors' architecture. See `docs/adr/0001-architecture.md` for
the history and design.

## License

Code and implementation templates are AGPL-3.0-or-later.
