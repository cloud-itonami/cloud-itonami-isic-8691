(ns navigator.sim
  "Demo driver -- `clojure -M:dev:run`. Walks a clean seeker through
  intake -> eligibility verification -> urgent-health-risk screening
  -> referral-finalization proposal (always escalates) -> human
  approval -> commit, then through health-information-disclosure
  proposal (always escalates) -> human approval -> commit, then shows
  five HARD holds (a jurisdiction with no spec-basis, an eligibility
  window that has aged past its own validity window, an unresolved
  urgent health risk screened directly via `:risk/screen` [never via
  an actuation op against an unscreened seeker -- see this actor's own
  governor ns docstring / the lesson `parksafety`'s ADR-2607071922
  Decision 5, `eldercare`'s, `museum`'s, `conservation`'s, `salon`'s,
  `entertainment`'s, `casework`'s, `hospital`'s, `facility`'s,
  `school`'s, `association`'s, `leasing`'s, `behavioral`'s,
  `secondary`'s, `card`'s, `water`'s, `telecom`'s, `aerospace`'s,
  `recovery`'s, `consulting`'s, `union`'s, `congregation`'s, `fab`'s,
  `energy`'s and `care`'s ADR-0001s already recorded], and a double
  referral-finalization/disclosure of an already-processed seeker)
  that never reach a human at all, and prints the audit ledger + the
  draft referral and disclosure records."
  (:require [langgraph.graph :as g]
            [navigator.store :as store]
            [navigator.operation :as op]))

(def operator {:actor-id "op-1" :actor-role :care-navigator :phase 3})

(defn- exec! [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}} {:thread-id tid :resume? true}))

(defn -main [& _]
  (let [db (store/seed-db)
        actor (op/build db)]
    (println "== seeker/intake seeker-1 (JPN, clean; eligibility within validity window, no urgent health risk) ==")
    (println (exec! actor "t1" {:op :seeker/intake :subject "seeker-1"
                                :patch {:id "seeker-1" :seeker-name "Sato Aiko"}} operator))

    (println "== eligibility/verify seeker-1 (escalates -- human approves) ==")
    (println (exec! actor "t2" {:op :eligibility/verify :subject "seeker-1"} operator))
    (println (approve! actor "t2"))

    (println "== risk/screen seeker-1 (clean; escalates -- human approves) ==")
    (println (exec! actor "t3" {:op :risk/screen :subject "seeker-1"} operator))
    (println (approve! actor "t3"))

    (println "== actuation/finalize-referral seeker-1 (always escalates -- actuation/finalize-referral) ==")
    (let [r (exec! actor "t4" {:op :actuation/finalize-referral :subject "seeker-1"} operator)]
      (println r)
      (println "-- human care-navigator approves --")
      (println (approve! actor "t4")))

    (println "== actuation/disclose-health-information seeker-1 (always escalates -- actuation/disclose-health-information) ==")
    (let [r (exec! actor "t5" {:op :actuation/disclose-health-information :subject "seeker-1"} operator)]
      (println r)
      (println "-- human care-navigator approves --")
      (println (approve! actor "t5")))

    (println "== eligibility/verify seeker-2 (no spec-basis -> HARD hold) ==")
    (println (exec! actor "t6" {:op :eligibility/verify :subject "seeker-2" :no-spec? true} operator))

    (println "== eligibility/verify seeker-3 (escalates -- human approves; sets up the eligibility-window-expired test) ==")
    (println (exec! actor "t7" {:op :eligibility/verify :subject "seeker-3"} operator))
    (println (approve! actor "t7"))

    (println "== actuation/finalize-referral seeker-3 (elapsed 100 days > validity 90 days -> HARD hold) ==")
    (println (exec! actor "t8" {:op :actuation/finalize-referral :subject "seeker-3"} operator))

    (println "== risk/screen seeker-4 (unresolved -> HARD hold, never reaches a human) ==")
    (println (exec! actor "t9" {:op :risk/screen :subject "seeker-4"} operator))

    (println "== actuation/finalize-referral seeker-1 AGAIN (double-referral -> HARD hold) ==")
    (println (exec! actor "t10" {:op :actuation/finalize-referral :subject "seeker-1"} operator))

    (println "== actuation/disclose-health-information seeker-1 AGAIN (double-disclosure -> HARD hold) ==")
    (println (exec! actor "t11" {:op :actuation/disclose-health-information :subject "seeker-1"} operator))

    (println "== audit ledger ==")
    (doseq [f (store/ledger db)] (println f))

    (println "== draft referral records ==")
    (doseq [r (store/referral-history db)] (println r))

    (println "== draft disclosure records ==")
    (doseq [r (store/disclosure-history db)] (println r))))
