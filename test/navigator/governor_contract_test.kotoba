(ns navigator.governor-contract-test
  "The governor contract as executable tests -- the navigator analog
  of `cloud-itonami-isic-6512`'s `casualty.governor-contract-test`.
  The single invariant under test:

    NavigatorOps-LLM never finalizes a referral or discloses health
    information the Health Access Governor would reject, `:actuation/
    finalize-referral`/`:actuation/disclose-health-information` NEVER
    auto-commit at any phase, `:seeker/intake` (no direct capital
    risk) MAY auto-commit when clean, and every decision (commit OR
    hold) leaves exactly one ledger fact."
  (:require [clojure.test :refer [deftest is testing]]
            [langgraph.graph :as g]
            [navigator.store :as store]
            [navigator.operation :as op]))

(defn- fresh []
  (let [db (store/seed-db)]
    [db (op/build db)]))

(def operator {:actor-id "op-1" :actor-role :care-navigator :phase 3})

(defn- exec-op [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}} {:thread-id tid :resume? true}))

(defn- verify!
  "Walks `subject` through verify -> approve, leaving an eligibility
  assessment on file. Uses distinct thread-ids per call site by
  suffixing `tid-prefix`."
  [actor tid-prefix subject]
  (exec-op actor (str tid-prefix "-verify") {:op :eligibility/verify :subject subject} operator)
  (approve! actor (str tid-prefix "-verify")))

(defn- screen!
  "Walks `subject` through urgent-health-risk screening -> approve,
  leaving a screening on file. Only safe to call for a seeker whose
  risk status has already resolved -- an unresolved risk HARD-holds
  the screen itself (see
  `urgent-health-risk-is-held-and-unoverridable`)."
  [actor tid-prefix subject]
  (exec-op actor (str tid-prefix "-screen") {:op :risk/screen :subject subject} operator)
  (approve! actor (str tid-prefix "-screen")))

(deftest clean-intake-auto-commits
  (let [[db actor] (fresh)
        res (exec-op actor "t1"
                  {:op :seeker/intake :subject "seeker-1"
                   :patch {:id "seeker-1" :seeker-name "Sato Aiko"}} operator)]
    (is (= :commit (get-in res [:state :disposition])))
    (is (= "Sato Aiko" (:seeker-name (store/seeker db "seeker-1"))) "SSoT actually updated")
    (is (= 1 (count (store/ledger db))))))

(deftest eligibility-verify-always-needs-approval
  (testing "verify is never in any phase's :auto set -- always human approval, even when clean"
    (let [[db actor] (fresh)
          res (exec-op actor "t2" {:op :eligibility/verify :subject "seeker-1"} operator)]
      (is (= :interrupted (:status res)))
      (let [r2 (approve! actor "t2")]
        (is (= :commit (get-in r2 [:state :disposition])))
        (is (some? (store/eligibility-of db "seeker-1")))))))

(deftest fabricated-jurisdiction-is-held
  (testing "an eligibility/verify proposal with no official spec-basis -> HOLD, never reaches a human"
    (let [[db actor] (fresh)
          res (exec-op actor "t3"
                    {:op :eligibility/verify :subject "seeker-1" :no-spec? true} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:no-spec-basis} (-> (store/ledger db) first :basis)))
      (is (nil? (store/eligibility-of db "seeker-1")) "no eligibility assessment written"))))

(deftest finalize-referral-without-eligibility-is-held
  (testing "actuation/finalize-referral before any eligibility verification -> HOLD (evidence incomplete)"
    (let [[db actor] (fresh)
          res (exec-op actor "t4" {:op :actuation/finalize-referral :subject "seeker-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:evidence-incomplete} (-> (store/ledger db) first :basis))))))

(deftest eligibility-window-elapsed-exceeds-validity-is-held
  (testing "a seeker whose own eligibility elapsed-days exceed their own validity-window days -> HOLD"
    (let [[db actor] (fresh)
          _ (verify! actor "t5pre" "seeker-3")
          res (exec-op actor "t5" {:op :actuation/finalize-referral :subject "seeker-3"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:eligibility-window-elapsed-exceeds-validity} (-> (store/ledger db) last :basis)))
      (is (empty? (store/referral-history db))))))

(deftest urgent-health-risk-is-held-and-unoverridable
  (testing "an unresolved urgent health risk on a seeker -> HOLD, and never reaches request-approval -- exercised via :risk/screen DIRECTLY, not via the actuation op against an unscreened seeker (see this actor's governor ns docstring / parksafety's ADR-2607071922 Decision 5 / eldercare's, museum's, conservation's, salon's, entertainment's, casework's, hospital's, facility's, school's, association's, leasing's, behavioral's, secondary's, card's, water's, telecom's, aerospace's, recovery's, consulting's, union's, congregation's, fab's, energy's and care's ADR-0001s)"
    (let [[db actor] (fresh)
          res (exec-op actor "t6" {:op :risk/screen :subject "seeker-4"} operator)]
      (is (= :hold (get-in res [:state :disposition])) "settles immediately, no interrupt")
      (is (not= :interrupted (:status res)))
      (is (some #{:urgent-health-risk-unresolved} (-> (store/ledger db) first :basis)))
      (is (nil? (store/risk-screen-of db "seeker-4")) "no clearance written"))))

(deftest finalize-referral-always-escalates-then-human-decides
  (testing "a clean, fully-assessed seeker still ALWAYS interrupts for human approval -- actuation/finalize-referral is never auto"
    (let [[db actor] (fresh)
          _ (verify! actor "t7pre" "seeker-1")
          r1 (exec-op actor "t7" {:op :actuation/finalize-referral :subject "seeker-1"} operator)]
      (is (= :interrupted (:status r1)) "pauses for human approval even when governor-clean")
      (testing "approve -> commit, referral record drafted"
        (let [r2 (approve! actor "t7")]
          (is (= :commit (get-in r2 [:state :disposition])))
          (is (true? (:referral-finalized? (store/seeker db "seeker-1"))))
          (is (= 1 (count (store/referral-history db))) "one draft referral record"))))))

(deftest disclose-health-information-always-escalates-then-human-decides
  (testing "a clean, fully-assessed, risk-resolved seeker still ALWAYS interrupts for human approval -- actuation/disclose-health-information is never auto"
    (let [[db actor] (fresh)
          _ (verify! actor "t8pre" "seeker-1")
          _ (screen! actor "t8pre2" "seeker-1")
          r1 (exec-op actor "t8" {:op :actuation/disclose-health-information :subject "seeker-1"} operator)]
      (is (= :interrupted (:status r1)) "pauses for human approval even when governor-clean")
      (testing "approve -> commit, disclosure record drafted"
        (let [r2 (approve! actor "t8")]
          (is (= :commit (get-in r2 [:state :disposition])))
          (is (true? (:disclosure-made? (store/seeker db "seeker-1"))))
          (is (= 1 (count (store/disclosure-history db))) "one draft disclosure record"))))))

(deftest finalize-referral-double-referral-is-held
  (testing "finalizing the same seeker's referral twice -> HOLD on the second attempt"
    (let [[db actor] (fresh)
          _ (verify! actor "t9pre" "seeker-1")
          _ (exec-op actor "t9a" {:op :actuation/finalize-referral :subject "seeker-1"} operator)
          _ (approve! actor "t9a")
          res (exec-op actor "t9" {:op :actuation/finalize-referral :subject "seeker-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:already-referred} (-> (store/ledger db) last :basis)))
      (is (= 1 (count (store/referral-history db))) "still only the one earlier referral"))))

(deftest disclose-health-information-double-disclosure-is-held
  (testing "disclosing the same seeker's health information twice -> HOLD on the second attempt"
    (let [[db actor] (fresh)
          _ (verify! actor "t10pre" "seeker-1")
          _ (screen! actor "t10pre2" "seeker-1")
          _ (exec-op actor "t10a" {:op :actuation/disclose-health-information :subject "seeker-1"} operator)
          _ (approve! actor "t10a")
          res (exec-op actor "t10" {:op :actuation/disclose-health-information :subject "seeker-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:already-disclosed} (-> (store/ledger db) last :basis)))
      (is (= 1 (count (store/disclosure-history db))) "still only the one earlier disclosure"))))

(deftest every-decision-leaves-one-ledger-fact
  (testing "write-only-through-ledger: N operations -> N ledger facts"
    (let [[db actor] (fresh)]
      (exec-op actor "a" {:op :seeker/intake :subject "seeker-1"
                          :patch {:id "seeker-1" :seeker-name "Sato Aiko"}} operator)
      (exec-op actor "b" {:op :eligibility/verify :subject "seeker-1" :no-spec? true} operator)
      (is (= 2 (count (store/ledger db)))
          "one commit + one hold, both recorded"))))
