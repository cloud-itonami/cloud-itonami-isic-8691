(ns navigator.registry-test
  (:require [clojure.test :refer [deftest is]]
            [navigator.registry :as r]))

;; ----------------------------- eligibility-window-elapsed-exceeds-validity? -----------------------------

(deftest not-exceeded-when-within-validity-window
  (is (not (r/eligibility-window-elapsed-exceeds-validity? {:eligibility-elapsed-days 10 :eligibility-validity-window-days 90})))
  (is (not (r/eligibility-window-elapsed-exceeds-validity? {:eligibility-elapsed-days 90 :eligibility-validity-window-days 90}))))

(deftest exceeded-when-past-validity-window
  (is (r/eligibility-window-elapsed-exceeds-validity? {:eligibility-elapsed-days 100 :eligibility-validity-window-days 90}))
  (is (r/eligibility-window-elapsed-exceeds-validity? {:eligibility-elapsed-days 91 :eligibility-validity-window-days 90})))

(deftest exceeded-is-false-on-missing-fields
  (is (not (r/eligibility-window-elapsed-exceeds-validity? {})))
  (is (not (r/eligibility-window-elapsed-exceeds-validity? {:eligibility-elapsed-days 100}))))

;; ----------------------------- register-referral -----------------------------

(deftest referral-is-a-draft-not-a-real-referral
  (let [result (r/register-referral "seeker-1" "JPN" 0)]
    (is (nil? (get-in result ["certificate" "proof"])))
    (is (= (get-in result ["certificate" "issued_by_registry"]) false))
    (is (= (get-in result ["certificate" "status"]) "draft-unsigned"))))

(deftest referral-assigns-referral-number
  (let [result (r/register-referral "seeker-1" "JPN" 7)]
    (is (= (get result "referral_number") "JPN-REF-000007"))
    (is (= (get-in result ["record" "seeker_id"]) "seeker-1"))
    (is (= (get-in result ["record" "kind"]) "referral-draft"))
    (is (= (get-in result ["record" "immutable"]) true))))

(deftest referral-validation-rules
  (is (thrown? Exception (r/register-referral "" "JPN" 0)))
  (is (thrown? Exception (r/register-referral "seeker-1" "" 0)))
  (is (thrown? Exception (r/register-referral "seeker-1" "JPN" -1))))

;; ----------------------------- register-disclosure -----------------------------

(deftest disclosure-is-a-draft-not-a-real-disclosure
  (let [result (r/register-disclosure "seeker-1" "JPN" 0)]
    (is (nil? (get-in result ["certificate" "proof"])))
    (is (= (get-in result ["certificate" "issued_by_registry"]) false))
    (is (= (get-in result ["certificate" "status"]) "draft-unsigned"))))

(deftest disclosure-assigns-disclosure-number
  (let [result (r/register-disclosure "seeker-1" "JPN" 3)]
    (is (= (get result "disclosure_number") "JPN-DIS-000003"))
    (is (= (get-in result ["record" "seeker_id"]) "seeker-1"))
    (is (= (get-in result ["record" "kind"]) "disclosure-draft"))
    (is (= (get-in result ["record" "immutable"]) true))))

(deftest disclosure-validation-rules
  (is (thrown? Exception (r/register-disclosure "" "JPN" 0)))
  (is (thrown? Exception (r/register-disclosure "seeker-1" "" 0)))
  (is (thrown? Exception (r/register-disclosure "seeker-1" "JPN" -1))))

(deftest history-is-append-only
  (let [c1 (r/register-referral "seeker-1" "JPN" 0)
        hist (r/append [] c1)
        c2 (r/register-referral "seeker-2" "JPN" 1)
        hist2 (r/append hist c2)]
    (is (= 2 (count hist2)))
    (is (= "JPN-REF-000000" (get-in hist2 [0 "record_id"])))
    (is (= "JPN-REF-000001" (get-in hist2 [1 "record_id"])))))
