(ns navigator.governor
  "Health Access Governor -- the independent compliance layer that
  earns the NavigatorOps-LLM the right to commit. The LLM has no
  notion of health-access-navigation regulatory law, whether a
  seeker's own eligibility determination has actually aged past its
  own recorded validity window, whether an urgent health risk against
  a seeker has actually stayed unresolved, or when an act stops being
  a draft and becomes a real-world referral finalization or health-
  information disclosure, so this MUST be a separate system able to
  *reject* a proposal and fall back to HOLD -- the navigator analog of
  `cloud-itonami-isic-6512`'s CasualtyGovernor.

  Six checks, in priority order, ALL HARD violations: a human approver
  CANNOT override them (you don't get to approve your way past a
  fabricated jurisdiction spec-basis, incomplete evidence, an expired
  eligibility window, an unresolved urgent health risk, or a double
  referral/disclosure). The confidence/actuation gate is SOFT: it asks
  a human to look (low confidence / actuation), and the human may
  approve -- but see `navigator.phase`: for `:stake :actuation/
  finalize-referral`/`:actuation/disclose-health-information` (a real
  routing act or a real health-information disclosure) NO phase ever
  allows auto-commit either. Two independent layers agree that
  actuation is always a human call.

    1. Spec-basis                  -- did the eligibility proposal cite
                                       an OFFICIAL source (`navigator.
                                       facts`), or invent one?
    2. Evidence incomplete         -- for `:actuation/finalize-
                                       referral`/`:actuation/disclose-
                                       health-information`, has the
                                       seeker actually been assessed
                                       with a full consent-record/
                                       eligibility-determination-
                                       record/provider-directory-
                                       verification-record/purpose-
                                       limitation-record evidence
                                       checklist on file?
    3. Eligibility window elapsed
       exceeds validity               -- for `:actuation/finalize-
                                       referral`, INDEPENDENTLY
                                       recompute whether the seeker's
                                       own elapsed days since
                                       eligibility was determined
                                       exceeds their own recorded
                                       validity-window days
                                       (`navigator.registry/
                                       eligibility-window-elapsed-
                                       exceeds-validity?`) -- needs no
                                       proposal inspection at all. The
                                       SIXTH instance of this fleet's
                                       MAXIMUM-ceiling check family
                                       (`facility.governor/occupancy-
                                       exceeds-capacity-violations`/
                                       `school.governor/class-size-
                                       exceeds-maximum-violations`/
                                       `card.governor/settlement-
                                       amount-exceeds-authorized-
                                       violations`/`recovery.governor/
                                       contamination-percentage-
                                       exceeds-maximum-violations`/
                                       `care.governor/caregiver-
                                       workload-exceeds-maximum-
                                       violations` established the
                                       first five).
    4. Urgent health risk
       unresolved                     -- reported by THIS proposal
                                       itself (a `:risk/screen` that
                                       just found one), or already on
                                       file for the seeker (`:risk/
                                       screen`/`:actuation/finalize-
                                       referral`). Evaluated
                                       UNCONDITIONALLY (not scoped to a
                                       specific op), the SAME
                                       discipline `casualty.governor/
                                       sanctions-violations`/...
                                       (thirty-four prior siblings,
                                       most recently `care.governor/
                                       safeguarding-signal-unresolved-
                                       violations`)... established --
                                       the THIRTY-FIFTH distinct
                                       application of this exact
                                       discipline overall. Distinct
                                       from `casework.governor/risk-
                                       flag-unresolved-violations`
                                       (which verifies a fraud/
                                       misrepresentation risk against
                                       an eligibility determination)
                                       and from `care.governor/
                                       safeguarding-signal-unresolved-
                                       violations`/`congregation.
                                       governor/safeguarding-concern-
                                       unresolved-violations` (which
                                       verify neglect/abuse-adjacent
                                       safeguarding concerns) -- this
                                       check instead verifies an
                                       urgent HEALTH/safety risk
                                       surfaced during triage (e.g. an
                                       acute symptom or crisis flag
                                       requiring emergency escalation
                                       rather than routine referral),
                                       grep-verified absent from every
                                       prior sibling before this claim
                                       was finalized. Exercised in
                                       tests/demo via `:risk/screen`
                                       DIRECTLY, not via the actuation
                                       op against an unscreened
                                       seeker -- see this ns's own test
                                       suite.
    5. Confidence floor / actuation
       gate                          -- LLM confidence below threshold,
                                       OR the op is `:actuation/
                                       finalize-referral`/`:actuation/
                                       disclose-health-information`
                                       (REAL health-access acts) ->
                                       escalate.

  Two more guards, double-referral/double-disclosure prevention, are
  enforced but NOT listed as numbered HARD checks above because they
  need no upstream comparison at all -- `already-referred-violations`/
  `already-disclosed-violations` refuse to finalize a referral/
  disclose health information for the SAME seeker twice, off dedicated
  `:referral-finalized?`/`:disclosure-made?` facts (never a `:status`
  value) -- the SAME 'check a dedicated boolean, not status'
  discipline every prior sibling governor's guards establish, informed
  by `cloud-itonami-isic-6492`'s status-lifecycle bug
  (ADR-2607071320)."
  (:require [navigator.facts :as facts]
            [navigator.registry :as registry]
            [navigator.store :as store]))

(def confidence-floor 0.6)

(def high-stakes
  "Stakes grave enough to always require a human, even when clean.
  Finalizing a real referral and disclosing real health information
  are the two real-world actuation events this actor performs -- a
  two-member set, matching every prior dual-actuation sibling's shape.
  Both are POSITIVE actuations (finalizing/issuing a record), matching
  this fleet's majority actuation shape (3600/6190 remain the only
  negative-actuation exceptions)."
  #{:actuation/finalize-referral :actuation/disclose-health-information})

;; ----------------------------- checks -----------------------------

(defn- spec-basis-violations
  "A `:eligibility/verify` (or actuation) proposal with no spec-basis
  citation is a HARD violation -- never invent a jurisdiction's
  health-access-navigation requirements."
  [{:keys [op]} proposal]
  (when (contains? #{:eligibility/verify :actuation/finalize-referral :actuation/disclose-health-information} op)
    (let [value (:value proposal)]
      (when (or (empty? (:cites proposal))
                (and (contains? value :spec-basis) (nil? (:spec-basis value))))
        [{:rule :no-spec-basis
          :detail "公式spec-basisの引用が無い提案は医療アクセス・ナビゲーション運営基準として扱えない"}]))))

(defn- evidence-incomplete-violations
  "For `:actuation/finalize-referral`/`:actuation/disclose-health-
  information`, the jurisdiction's required consent-record/
  eligibility-determination-record/provider-directory-verification-
  record/purpose-limitation-record evidence must actually be satisfied
  -- do not trust the advisor's self-reported confidence alone."
  [{:keys [op subject]} st]
  (when (contains? #{:actuation/finalize-referral :actuation/disclose-health-information} op)
    (let [sk (store/seeker st subject)
          elig (store/eligibility-of st subject)]
      (when-not (and elig
                     (facts/required-evidence-satisfied?
                      (:jurisdiction sk) (:checklist elig)))
        [{:rule :evidence-incomplete
          :detail "法域の必要書類(同意記録/受給資格判定記録/提供者名簿確認記録/目的制限記録等)が充足していない状態での提案"}]))))

(defn- eligibility-window-elapsed-exceeds-validity-violations
  "For `:actuation/finalize-referral`, INDEPENDENTLY recompute whether
  the seeker's own elapsed days since eligibility determination
  exceeds their own recorded validity-window days via `navigator.
  registry/eligibility-window-elapsed-exceeds-validity?` -- needs no
  proposal inspection at all, since its inputs are permanent ground-
  truth fields already on the seeker."
  [{:keys [op subject]} st]
  (when (= op :actuation/finalize-referral)
    (let [sk (store/seeker st subject)]
      (when (registry/eligibility-window-elapsed-exceeds-validity? sk)
        [{:rule :eligibility-window-elapsed-exceeds-validity
          :detail (str subject " の資格判定経過日数(" (:eligibility-elapsed-days sk)
                      ")が有効期間(" (:eligibility-validity-window-days sk) ")を超過")}]))))

(defn- urgent-health-risk-unresolved-violations
  "An unresolved urgent health risk -- reported by THIS proposal (e.g.
  a `:risk/screen` that itself just found one), or already on file in
  the store for the seeker (`:risk/screen`/`:actuation/finalize-
  referral`) -- is a HARD, un-overridable hold. Evaluated
  UNCONDITIONALLY (not scoped to a specific op) so the screening op
  itself can HARD-hold on its own finding."
  [{:keys [op subject]} proposal st]
  (let [hit-in-proposal? (= :unresolved (get-in proposal [:value :verdict]))
        seeker-id (when (contains? #{:risk/screen :actuation/finalize-referral} op) subject)
        hit-on-file? (and seeker-id (= :unresolved (:verdict (store/risk-screen-of st seeker-id))))]
    (when (or hit-in-proposal? hit-on-file?)
      [{:rule :urgent-health-risk-unresolved
        :detail "未解決の緊急健康リスクがある状態での紹介確定提案は進められない"}])))

(defn- already-referred-violations
  "For `:actuation/finalize-referral`, refuses to finalize a referral
  for the SAME seeker twice, off a dedicated `:referral-finalized?`
  fact (never a `:status` value)."
  [{:keys [op subject]} st]
  (when (= op :actuation/finalize-referral)
    (when (store/seeker-already-referred? st subject)
      [{:rule :already-referred
        :detail (str subject " は既に紹介確定済み")}])))

(defn- already-disclosed-violations
  "For `:actuation/disclose-health-information`, refuses to disclose
  health information for the SAME seeker twice, off a dedicated
  `:disclosure-made?` fact (never a `:status` value)."
  [{:keys [op subject]} st]
  (when (= op :actuation/disclose-health-information)
    (when (store/seeker-already-disclosed? st subject)
      [{:rule :already-disclosed
        :detail (str subject " は既に健康情報開示済み")}])))

(defn check
  "Censors a NavigatorOps-LLM proposal against the governor rules.
  Returns {:ok? bool :violations [..] :confidence c :escalate? bool
  :high-stakes? bool :hard? bool}."
  [request _context proposal st]
  (let [hard (into []
                   (concat (spec-basis-violations request proposal)
                           (evidence-incomplete-violations request st)
                           (eligibility-window-elapsed-exceeds-validity-violations request st)
                           (urgent-health-risk-unresolved-violations request proposal st)
                           (already-referred-violations request st)
                           (already-disclosed-violations request st)))
        conf (:confidence proposal 0.0)
        low? (< conf confidence-floor)
        stakes? (boolean (high-stakes (:stake proposal)))
        hard? (boolean (seq hard))]
    {:ok?          (and (not hard?) (not low?) (not stakes?))
     :violations   hard
     :confidence   conf
     :hard?        hard?
     :escalate?    (and (not hard?) (or low? stakes?))
     :high-stakes? stakes?}))

(defn hold-fact
  "The audit fact written when a proposal is rejected (HOLD)."
  [request context verdict]
  {:t          :governor-hold
   :op         (:op request)
   :actor      (:actor-id context)
   :subject    (:subject request)
   :disposition :hold
   :basis      (mapv :rule (:violations verdict))
   :violations (:violations verdict)
   :confidence (:confidence verdict)})
