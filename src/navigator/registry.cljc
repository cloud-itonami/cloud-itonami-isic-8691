(ns navigator.registry
  "Pure-function referral-finalization + health-information-disclosure
  record construction -- an append-only health-access-navigation
  book-of-record draft.

  Like every sibling actor's registry, there is no single
  international check-digit standard for a referral or disclosure
  reference number -- every navigation provider/jurisdiction assigns
  its own reference format. This namespace does NOT invent one; it
  builds a jurisdiction-scoped sequence number and validates the
  record's required fields, the same honest, non-fabricating
  discipline `navigator.facts` uses.

  `eligibility-window-elapsed-exceeds-validity?` is the SIXTH instance
  of this fleet's MAXIMUM-ceiling check family (`facility.registry/
  occupancy-exceeds-capacity?` established the first, `school.
  registry/class-size-exceeds-maximum?` the second, `card.registry/
  settlement-amount-exceeds-authorized?` the third, `recovery.
  registry/contamination-percentage-exceeds-maximum?` the fourth,
  `care.registry/caregiver-workload-exceeds-maximum?` the fifth),
  applying the SAME ceiling-only comparison to a seeker's own elapsed
  days since eligibility was determined against their own recorded
  eligibility-validity-window -- a direct, natural mapping onto real
  health-benefits practice (an eligibility determination that has
  aged past its own validity window must be re-verified before a real
  referral is finalized on it).

  This namespace is pure data + pure functions -- no I/O, no network
  call to any real health-navigation system. It builds the RECORD a
  navigation operator would keep, not the act of finalizing the
  referral or disclosing health information itself (that is
  `navigator.operation`'s `:actuation/finalize-referral`/`:actuation/
  disclose-health-information`, always human-gated -- see README
  `Actuation`)."
  (:require [clojure.string :as str]))

(defn- unsigned-certificate
  "Every certificate this actor produces is UNSIGNED -- signature is the
  navigation operator's own act, not this actor's. See README
  `Actuation`."
  [kind subject record-id]
  {"@context" ["https://www.w3.org/ns/credentials/v2"]
   "type" ["VerifiableCredential" kind]
   "credentialSubject" {"id" subject "record" record-id}
   "proof" nil
   "issued_by_registry" false
   "status" "draft-unsigned"})

(defn- zero-pad [n w]
  (let [s (str n)]
    (str (apply str (repeat (max 0 (- w (count s))) "0")) s)))

(defn eligibility-window-elapsed-exceeds-validity?
  "Does `seeker`'s own `:eligibility-elapsed-days` exceed their own
  recorded `:eligibility-validity-window-days`? A pure ground-truth
  check against the seeker's own permanent fields -- no upstream
  comparison needed. The SIXTH instance of this fleet's MAXIMUM-
  ceiling check family (see ns docstring)."
  [{:keys [eligibility-elapsed-days eligibility-validity-window-days]}]
  (and (number? eligibility-elapsed-days) (number? eligibility-validity-window-days)
       (> eligibility-elapsed-days eligibility-validity-window-days)))

(defn register-referral
  "Validate + construct the REFERRAL registration DRAFT -- the
  navigation operator's own act of finalizing a real referral routing
  a seeker to a care provider. Pure function -- does not touch any
  real health-navigation system; it builds the RECORD an operator
  would keep. `navigator.governor` independently re-verifies the
  seeker's own eligibility-window currency and urgent-health-risk
  resolution status, and blocks a double-finalization for the same
  seeker, before this is ever allowed to commit."
  [seeker-id jurisdiction sequence]
  (when-not (and seeker-id (not= seeker-id ""))
    (throw (ex-info "referral: seeker_id required" {})))
  (when-not (and jurisdiction (not= jurisdiction ""))
    (throw (ex-info "referral: jurisdiction required" {})))
  (when (< sequence 0)
    (throw (ex-info "referral: sequence must be >= 0" {})))
  (let [referral-number (str (str/upper-case jurisdiction) "-REF-" (zero-pad sequence 6))
        record {"record_id" referral-number
                "kind" "referral-draft"
                "seeker_id" seeker-id
                "jurisdiction" jurisdiction
                "immutable" true}]
    {"record" record "referral_number" referral-number
     "certificate" (unsigned-certificate "Referral" referral-number referral-number)}))

(defn register-disclosure
  "Validate + construct the HEALTH-INFORMATION-DISCLOSURE registration
  DRAFT -- the navigation operator's own act of disclosing a real
  seeker's health information to a third party under consent. Pure
  function -- does not touch any real health-navigation system; it
  builds the RECORD an operator would keep. `navigator.governor`
  independently re-verifies the seeker's own consent/purpose-
  limitation evidence and blocks a double-disclosure for the same
  seeker, before this is ever allowed to commit."
  [seeker-id jurisdiction sequence]
  (when-not (and seeker-id (not= seeker-id ""))
    (throw (ex-info "disclosure: seeker_id required" {})))
  (when-not (and jurisdiction (not= jurisdiction ""))
    (throw (ex-info "disclosure: jurisdiction required" {})))
  (when (< sequence 0)
    (throw (ex-info "disclosure: sequence must be >= 0" {})))
  (let [disclosure-number (str (str/upper-case jurisdiction) "-DIS-" (zero-pad sequence 6))
        record {"record_id" disclosure-number
                "kind" "disclosure-draft"
                "seeker_id" seeker-id
                "jurisdiction" jurisdiction
                "immutable" true}]
    {"record" record "disclosure_number" disclosure-number
     "certificate" (unsigned-certificate "HealthInformationDisclosure" disclosure-number disclosure-number)}))

(defn append [history result]
  (conj (vec history) (get result "record")))
