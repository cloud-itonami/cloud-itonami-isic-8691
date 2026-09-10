(ns navigator.phase-test
  "The phase table as executable tests. The invariant this repo cannot
  regress on: `:actuation/finalize-referral`/`:actuation/disclose-
  health-information` must NEVER be a member of any phase's `:auto`
  set."
  (:require [clojure.test :refer [deftest is testing]]
            [navigator.phase :as phase]))

(deftest finalize-referral-never-auto-at-any-phase
  (testing "structural invariant: no phase, now or in the future entries, auto-commits a real referral"
    (doseq [[n {:keys [auto]}] phase/phases]
      (is (not (contains? auto :actuation/finalize-referral))
          (str "phase " n " must not auto-commit :actuation/finalize-referral")))))

(deftest disclose-health-information-never-auto-at-any-phase
  (testing "structural invariant: no phase auto-commits a real health-information disclosure"
    (doseq [[n {:keys [auto]}] phase/phases]
      (is (not (contains? auto :actuation/disclose-health-information))
          (str "phase " n " must not auto-commit :actuation/disclose-health-information")))))

(deftest risk-screen-never-auto-at-any-phase
  (testing "screening carries no direct capital risk, but is still never auto-eligible, matching every sibling screening op in this fleet"
    (doseq [[n {:keys [auto]}] phase/phases]
      (is (not (contains? auto :risk/screen))
          (str "phase " n " must not auto-commit :risk/screen")))))

(deftest phase-0-is-fully-read-only
  (is (empty? (:writes (get phase/phases 0)))))

(deftest phase-3-auto-commits-only-no-capital-risk-ops
  (testing ":seeker/intake carries no direct capital risk -- auto-eligible; it is the ONLY auto-eligible op in this domain"
    (is (= #{:seeker/intake} (:auto (get phase/phases 3))))))

(deftest gate-hold-always-wins
  (is (= :hold (:disposition (phase/gate 3 {:op :seeker/intake} :hold)))))

(deftest gate-escalates-a-clean-non-auto-write
  (is (= :escalate (:disposition (phase/gate 3 {:op :actuation/finalize-referral} :commit))))
  (is (= :escalate (:disposition (phase/gate 3 {:op :actuation/disclose-health-information} :commit)))))

(deftest gate-holds-a-write-disabled-in-this-phase
  (is (= :hold (:disposition (phase/gate 0 {:op :seeker/intake} :commit)))))
