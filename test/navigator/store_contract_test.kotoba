(ns navigator.store-contract-test
  "The Store contract, run against BOTH backends. Proving MemStore and
  the Datomic-backed (langchain.db) store satisfy the same contract is
  what makes 'swap the SSoT for Datomic / kotoba-server' a
  configuration change, not a rewrite -- see `cloud-itonami-isic-6511`'s
  `underwriting.store-contract-test` for the same pattern on the
  sibling actor."
  (:require [clojure.test :refer [deftest is testing]]
            [navigator.store :as store]))

(defn- backends []
  [["MemStore" (store/seed-db)] ["DatomicStore" (store/datomic-seed-db)]])

(deftest read-parity
  (doseq [[label s] (backends)]
    (testing label
      (is (= "Sato Aiko" (:seeker-name (store/seeker s "seeker-1"))))
      (is (= "JPN" (:jurisdiction (store/seeker s "seeker-1"))))
      (is (= 10 (:eligibility-elapsed-days (store/seeker s "seeker-1"))))
      (is (= 90 (:eligibility-validity-window-days (store/seeker s "seeker-1"))))
      (is (false? (:urgent-health-risk-unresolved? (store/seeker s "seeker-1"))))
      (is (= 100 (:eligibility-elapsed-days (store/seeker s "seeker-3"))))
      (is (true? (:urgent-health-risk-unresolved? (store/seeker s "seeker-4"))))
      (is (false? (:referral-finalized? (store/seeker s "seeker-1"))))
      (is (false? (:disclosure-made? (store/seeker s "seeker-1"))))
      (is (= ["seeker-1" "seeker-2" "seeker-3" "seeker-4"]
             (mapv :id (store/all-seekers s))))
      (is (nil? (store/risk-screen-of s "seeker-1")))
      (is (nil? (store/eligibility-of s "seeker-1")))
      (is (= [] (store/ledger s)))
      (is (= [] (store/referral-history s)))
      (is (= [] (store/disclosure-history s)))
      (is (zero? (store/next-referral-sequence s "JPN")))
      (is (zero? (store/next-disclosure-sequence s "JPN")))
      (is (false? (store/seeker-already-referred? s "seeker-1")))
      (is (false? (store/seeker-already-disclosed? s "seeker-1"))))))

(deftest write-and-ledger-parity
  (doseq [[label s] (backends)]
    (testing label
      (testing "partial upsert merges, preserving untouched fields"
        (store/commit-record! s {:effect :seeker/upsert
                                 :value {:id "seeker-1" :seeker-name "Sato Aiko"}})
        (is (= "Sato Aiko" (:seeker-name (store/seeker s "seeker-1"))))
        (is (= 90 (:eligibility-validity-window-days (store/seeker s "seeker-1"))) "unrelated field preserved"))
      (testing "eligibility / risk-screen payloads commit and read back"
        (store/commit-record! s {:effect :eligibility/set :path ["seeker-1"]
                                 :payload {:jurisdiction "JPN" :checklist ["a" "b"]}})
        (is (= {:jurisdiction "JPN" :checklist ["a" "b"]} (store/eligibility-of s "seeker-1")))
        (store/commit-record! s {:effect :risk-screen/set :path ["seeker-1"]
                                 :payload {:seeker-id "seeker-1" :verdict :resolved}})
        (is (= {:seeker-id "seeker-1" :verdict :resolved} (store/risk-screen-of s "seeker-1"))))
      (testing "referral drafts a record and advances the sequence"
        (store/commit-record! s {:effect :seeker/mark-referred :path ["seeker-1"]})
        (is (= "JPN-REF-000000" (get (first (store/referral-history s)) "record_id")))
        (is (= "referral-draft" (get (first (store/referral-history s)) "kind")))
        (is (true? (:referral-finalized? (store/seeker s "seeker-1"))))
        (is (= 1 (count (store/referral-history s))))
        (is (= 1 (store/next-referral-sequence s "JPN")))
        (is (true? (store/seeker-already-referred? s "seeker-1")))
        (is (false? (store/seeker-already-referred? s "seeker-2"))))
      (testing "disclosure drafts a record and advances the sequence"
        (store/commit-record! s {:effect :seeker/mark-disclosed :path ["seeker-1"]})
        (is (= "JPN-DIS-000000" (get (first (store/disclosure-history s)) "record_id")))
        (is (= "disclosure-draft" (get (first (store/disclosure-history s)) "kind")))
        (is (true? (:disclosure-made? (store/seeker s "seeker-1"))))
        (is (= 1 (count (store/disclosure-history s))))
        (is (= 1 (store/next-disclosure-sequence s "JPN")))
        (is (true? (store/seeker-already-disclosed? s "seeker-1")))
        (is (false? (store/seeker-already-disclosed? s "seeker-2"))))
      (testing "ledger is append-only and order-preserving"
        (store/append-ledger! s {:op :a :disposition :commit})
        (store/append-ledger! s {:op :b :disposition :hold})
        (is (= [:commit :hold] (mapv :disposition (store/ledger s))))))))

(deftest datomic-empty-store-is-usable
  (let [s (store/datomic-store)]
    (is (nil? (store/seeker s "nope")))
    (is (= [] (store/all-seekers s)))
    (is (= [] (store/ledger s)))
    (is (= [] (store/referral-history s)))
    (is (= [] (store/disclosure-history s)))
    (is (zero? (store/next-referral-sequence s "JPN")))
    (is (zero? (store/next-disclosure-sequence s "JPN")))
    (store/with-seekers s {"x" {:id "x" :seeker-name "n"
                               :eligibility-elapsed-days 10 :eligibility-validity-window-days 90
                               :urgent-health-risk-unresolved? false
                               :referral-finalized? false :disclosure-made? false
                               :jurisdiction "JPN" :status :intake}})
    (is (= "n" (:seeker-name (store/seeker s "x"))))))
