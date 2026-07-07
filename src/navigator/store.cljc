(ns navigator.store
  "SSoT for the navigator actor, behind a `Store` protocol so the
  backend is a swap, not a rewrite -- the same seam every prior
  `cloud-itonami-isic-*` actor in this fleet uses:

    - `MemStore`     -- atom of EDN. The deterministic default for
                        dev/tests/demo (no deps).
    - `DatomicStore` -- backed by `langchain.db`, a Datomic-API-compatible
                        EAV store (datalog q / pull / upsert). Pure `.cljc`,
                        so it runs offline AND can be pointed at a real
                        Datomic Local or a kotoba-server pod by swapping
                        `langchain.db`'s `:db-api` (see langchain.kotoba-db).

  Both implement the same protocol and pass the same contract
  (test/navigator/store_contract_test.clj), which is the whole point:
  the actor, the Health Access Governor and the audit ledger never
  know which SSoT they run on.

  Like every prior dual-actuation sibling, this actor has TWO
  actuation events (finalizing a referral, disclosing health
  information) acting on the SAME entity (a `seeker`), each with its
  OWN history collection, sequence counter and dedicated double-
  actuation-guard boolean (`:referral-finalized?`/`:disclosure-made?`,
  never a `:status` value) -- the same discipline every prior sibling
  governor's guards establish, informed by `cloud-itonami-isic-6492`'s
  status-lifecycle bug (ADR-2607071320).

  The ledger stays append-only on every backend: 'which seeker was
  screened for an unresolved urgent health risk, which referral was
  finalized, which disclosure was made, on what jurisdictional basis,
  approved by whom' is always a query over an immutable log -- the
  audit trail a seeker trusting a navigation operator needs, and the
  evidence an operator needs if a referral or disclosure decision is
  later disputed."
  (:require #?(:clj  [clojure.edn :as edn]
               :cljs [cljs.reader :as edn])
            [navigator.registry :as registry]
            [langchain.db :as d]))

(defprotocol Store
  (seeker [s id])
  (all-seekers [s])
  (risk-screen-of [s seeker-id] "committed urgent-health-risk screening verdict for a seeker, or nil")
  (eligibility-of [s seeker-id] "committed eligibility evidence assessment, or nil")
  (ledger [s])
  (referral-history [s] "the append-only referral history (navigator.registry drafts)")
  (disclosure-history [s] "the append-only disclosure history (navigator.registry drafts)")
  (next-referral-sequence [s jurisdiction] "next referral-number sequence for a jurisdiction")
  (next-disclosure-sequence [s jurisdiction] "next disclosure-number sequence for a jurisdiction")
  (seeker-already-referred? [s seeker-id] "has this seeker's referral already been finalized?")
  (seeker-already-disclosed? [s seeker-id] "has this seeker's health information already been disclosed?")
  (commit-record! [s record] "apply a committed op's record to the SSoT")
  (append-ledger! [s fact]   "append one immutable decision fact")
  (with-seekers [s seekers] "replace/seed the seeker directory (map id->seeker)"))

;; ----------------------------- demo data -----------------------------

(defn demo-data
  "A small, self-contained seeker set covering both actuation
  lifecycles (finalizing a referral, disclosing health information) so
  the actor + tests run offline."
  []
  {:seekers
   {"seeker-1" {:id "seeker-1" :seeker-name "Sato Aiko"
               :eligibility-elapsed-days 10 :eligibility-validity-window-days 90
               :urgent-health-risk-unresolved? false
               :referral-finalized? false :disclosure-made? false
               :jurisdiction "JPN" :status :intake}
    "seeker-2" {:id "seeker-2" :seeker-name "Atlantis Doe"
               :eligibility-elapsed-days 10 :eligibility-validity-window-days 90
               :urgent-health-risk-unresolved? false
               :referral-finalized? false :disclosure-made? false
               :jurisdiction "ATL" :status :intake}
    "seeker-3" {:id "seeker-3" :seeker-name "鈴木花子"
               :eligibility-elapsed-days 100 :eligibility-validity-window-days 90
               :urgent-health-risk-unresolved? false
               :referral-finalized? false :disclosure-made? false
               :jurisdiction "JPN" :status :intake}
    "seeker-4" {:id "seeker-4" :seeker-name "田中一郎"
               :eligibility-elapsed-days 10 :eligibility-validity-window-days 90
               :urgent-health-risk-unresolved? true
               :referral-finalized? false :disclosure-made? false
               :jurisdiction "JPN" :status :intake}}})

;; ----------------------------- shared commit logic -----------------------------

(defn- finalize-referral!
  "Backend-agnostic `:seeker/mark-referred` -- looks up the seeker via
  the protocol and drafts the referral record, and returns {:result ..
  :seeker-patch ..} for the caller to persist."
  [s seeker-id]
  (let [sk (seeker s seeker-id)
        seq-n (next-referral-sequence s (:jurisdiction sk))
        result (registry/register-referral seeker-id (:jurisdiction sk) seq-n)]
    {:result result
     :seeker-patch {:referral-finalized? true
                   :referral-number (get result "referral_number")}}))

(defn- disclose-health-information!
  "Backend-agnostic `:seeker/mark-disclosed` -- looks up the seeker via
  the protocol and drafts the disclosure record, and returns {:result
  .. :seeker-patch ..} for the caller to persist."
  [s seeker-id]
  (let [sk (seeker s seeker-id)
        seq-n (next-disclosure-sequence s (:jurisdiction sk))
        result (registry/register-disclosure seeker-id (:jurisdiction sk) seq-n)]
    {:result result
     :seeker-patch {:disclosure-made? true
                   :disclosure-number (get result "disclosure_number")}}))

;; ----------------------------- MemStore (default) -----------------------------

(defrecord MemStore [a]
  Store
  (seeker [_ id] (get-in @a [:seekers id]))
  (all-seekers [_] (sort-by :id (vals (:seekers @a))))
  (risk-screen-of [_ id] (get-in @a [:risk-screens id]))
  (eligibility-of [_ seeker-id] (get-in @a [:eligibility-assessments seeker-id]))
  (ledger [_] (:ledger @a))
  (referral-history [_] (:referrals @a))
  (disclosure-history [_] (:disclosures @a))
  (next-referral-sequence [_ jurisdiction] (get-in @a [:referral-sequences jurisdiction] 0))
  (next-disclosure-sequence [_ jurisdiction] (get-in @a [:disclosure-sequences jurisdiction] 0))
  (seeker-already-referred? [_ seeker-id] (boolean (get-in @a [:seekers seeker-id :referral-finalized?])))
  (seeker-already-disclosed? [_ seeker-id] (boolean (get-in @a [:seekers seeker-id :disclosure-made?])))
  (commit-record! [s {:keys [effect path value payload]}]
    (case effect
      :seeker/upsert
      (swap! a update-in [:seekers (:id value)] merge value)

      :eligibility/set
      (swap! a assoc-in [:eligibility-assessments (first path)] payload)

      :risk-screen/set
      (swap! a assoc-in [:risk-screens (first path)] payload)

      :seeker/mark-referred
      (let [seeker-id (first path)
            {:keys [result seeker-patch]} (finalize-referral! s seeker-id)
            jurisdiction (:jurisdiction (seeker s seeker-id))]
        (swap! a (fn [state]
                   (-> state
                       (update-in [:referral-sequences jurisdiction] (fnil inc 0))
                       (update-in [:seekers seeker-id] merge seeker-patch)
                       (update :referrals registry/append result))))
        result)

      :seeker/mark-disclosed
      (let [seeker-id (first path)
            {:keys [result seeker-patch]} (disclose-health-information! s seeker-id)
            jurisdiction (:jurisdiction (seeker s seeker-id))]
        (swap! a (fn [state]
                   (-> state
                       (update-in [:disclosure-sequences jurisdiction] (fnil inc 0))
                       (update-in [:seekers seeker-id] merge seeker-patch)
                       (update :disclosures registry/append result))))
        result)
      nil)
    s)
  (append-ledger! [_ fact] (swap! a update :ledger conj fact) fact)
  (with-seekers [s seekers] (when (seq seekers) (swap! a assoc :seekers seekers)) s))

(defn seed-db
  "A MemStore seeded with the demo seeker set. The deterministic
  default."
  []
  (->MemStore (atom (assoc (demo-data)
                           :eligibility-assessments {} :risk-screens {} :ledger [] :referral-sequences {}
                           :referrals [] :disclosure-sequences {} :disclosures []))))

;; ----------------------------- DatomicStore (langchain.db) -----------------------------

(def ^:private schema
  "DataScript/Datomic-style schema: only constraint attrs are declared.
  Compound values (eligibility/risk-screen payloads, ledger facts,
  referral/disclosure records) are stored as EDN strings so
  `langchain.db` doesn't expand them into sub-entities -- the same
  convention every sibling actor's store uses."
  {:seeker/id                          {:db/unique :db.unique/identity}
   :eligibility/seeker-id              {:db/unique :db.unique/identity}
   :risk-screen/seeker-id              {:db/unique :db.unique/identity}
   :ledger/seq                        {:db/unique :db.unique/identity}
   :referral/seq                      {:db/unique :db.unique/identity}
   :disclosure/seq                    {:db/unique :db.unique/identity}
   :referral-sequence/jurisdiction    {:db/unique :db.unique/identity}
   :disclosure-sequence/jurisdiction  {:db/unique :db.unique/identity}})

(defn- enc [v] (pr-str v))
(defn- dec* [s] (when s (edn/read-string s)))

(defn- seeker->tx [{:keys [id seeker-name eligibility-elapsed-days eligibility-validity-window-days
                          urgent-health-risk-unresolved?
                          referral-finalized? disclosure-made?
                          jurisdiction status referral-number disclosure-number]}]
  (cond-> {:seeker/id id}
    seeker-name                                 (assoc :seeker/seeker-name seeker-name)
    eligibility-elapsed-days                     (assoc :seeker/eligibility-elapsed-days eligibility-elapsed-days)
    eligibility-validity-window-days             (assoc :seeker/eligibility-validity-window-days eligibility-validity-window-days)
    (some? urgent-health-risk-unresolved?)       (assoc :seeker/urgent-health-risk-unresolved? urgent-health-risk-unresolved?)
    (some? referral-finalized?)                  (assoc :seeker/referral-finalized? referral-finalized?)
    (some? disclosure-made?)                     (assoc :seeker/disclosure-made? disclosure-made?)
    jurisdiction                                  (assoc :seeker/jurisdiction jurisdiction)
    status                                        (assoc :seeker/status status)
    referral-number                               (assoc :seeker/referral-number referral-number)
    disclosure-number                             (assoc :seeker/disclosure-number disclosure-number)))

(def ^:private seeker-pull
  [:seeker/id :seeker/seeker-name :seeker/eligibility-elapsed-days :seeker/eligibility-validity-window-days
   :seeker/urgent-health-risk-unresolved? :seeker/referral-finalized? :seeker/disclosure-made?
   :seeker/jurisdiction :seeker/status :seeker/referral-number :seeker/disclosure-number])

(defn- pull->seeker [m]
  (when (:seeker/id m)
    {:id (:seeker/id m) :seeker-name (:seeker/seeker-name m)
     :eligibility-elapsed-days (:seeker/eligibility-elapsed-days m)
     :eligibility-validity-window-days (:seeker/eligibility-validity-window-days m)
     :urgent-health-risk-unresolved? (boolean (:seeker/urgent-health-risk-unresolved? m))
     :referral-finalized? (boolean (:seeker/referral-finalized? m))
     :disclosure-made? (boolean (:seeker/disclosure-made? m))
     :jurisdiction (:seeker/jurisdiction m) :status (:seeker/status m)
     :referral-number (:seeker/referral-number m) :disclosure-number (:seeker/disclosure-number m)}))

(defrecord DatomicStore [conn]
  Store
  (seeker [_ id]
    (pull->seeker (d/pull (d/db conn) seeker-pull [:seeker/id id])))
  (all-seekers [_]
    (->> (d/q '[:find [?id ...] :where [?e :seeker/id ?id]] (d/db conn))
         (map #(pull->seeker (d/pull (d/db conn) seeker-pull [:seeker/id %])))
         (sort-by :id)))
  (risk-screen-of [_ id]
    (dec* (d/q '[:find ?p . :in $ ?sid
                :where [?k :risk-screen/seeker-id ?sid] [?k :risk-screen/payload ?p]]
              (d/db conn) id)))
  (eligibility-of [_ seeker-id]
    (dec* (d/q '[:find ?p . :in $ ?sid
                :where [?a :eligibility/seeker-id ?sid] [?a :eligibility/payload ?p]]
              (d/db conn) seeker-id)))
  (ledger [_]
    (->> (d/q '[:find ?s ?f :where [?e :ledger/seq ?s] [?e :ledger/fact ?f]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (referral-history [_]
    (->> (d/q '[:find ?s ?r :where [?e :referral/seq ?s] [?e :referral/record ?r]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (disclosure-history [_]
    (->> (d/q '[:find ?s ?r :where [?e :disclosure/seq ?s] [?e :disclosure/record ?r]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (next-referral-sequence [_ jurisdiction]
    (or (d/q '[:find ?n . :in $ ?j
              :where [?e :referral-sequence/jurisdiction ?j] [?e :referral-sequence/next ?n]]
            (d/db conn) jurisdiction)
        0))
  (next-disclosure-sequence [_ jurisdiction]
    (or (d/q '[:find ?n . :in $ ?j
              :where [?e :disclosure-sequence/jurisdiction ?j] [?e :disclosure-sequence/next ?n]]
            (d/db conn) jurisdiction)
        0))
  (seeker-already-referred? [s seeker-id]
    (boolean (:referral-finalized? (seeker s seeker-id))))
  (seeker-already-disclosed? [s seeker-id]
    (boolean (:disclosure-made? (seeker s seeker-id))))
  (commit-record! [s {:keys [effect path value payload]}]
    (case effect
      :seeker/upsert
      (d/transact! conn [(seeker->tx value)])

      :eligibility/set
      (d/transact! conn [{:eligibility/seeker-id (first path) :eligibility/payload (enc payload)}])

      :risk-screen/set
      (d/transact! conn [{:risk-screen/seeker-id (first path) :risk-screen/payload (enc payload)}])

      :seeker/mark-referred
      (let [seeker-id (first path)
            {:keys [result seeker-patch]} (finalize-referral! s seeker-id)
            jurisdiction (:jurisdiction (seeker s seeker-id))
            next-n (inc (next-referral-sequence s jurisdiction))]
        (d/transact! conn
                     [(seeker->tx (assoc seeker-patch :id seeker-id))
                      {:referral-sequence/jurisdiction jurisdiction :referral-sequence/next next-n}
                      {:referral/seq (count (referral-history s)) :referral/record (enc (get result "record"))}])
        result)

      :seeker/mark-disclosed
      (let [seeker-id (first path)
            {:keys [result seeker-patch]} (disclose-health-information! s seeker-id)
            jurisdiction (:jurisdiction (seeker s seeker-id))
            next-n (inc (next-disclosure-sequence s jurisdiction))]
        (d/transact! conn
                     [(seeker->tx (assoc seeker-patch :id seeker-id))
                      {:disclosure-sequence/jurisdiction jurisdiction :disclosure-sequence/next next-n}
                      {:disclosure/seq (count (disclosure-history s)) :disclosure/record (enc (get result "record"))}])
        result)
      nil)
    s)
  (append-ledger! [s fact]
    (d/transact! conn [{:ledger/seq (count (ledger s)) :ledger/fact (enc fact)}])
    fact)
  (with-seekers [s seekers]
    (when (seq seekers) (d/transact! conn (mapv seeker->tx (vals seekers)))) s))

(defn datomic-store
  "A DatomicStore (langchain.db backend) seeded from `data`
  ({:seekers ..}); empty when omitted."
  ([] (datomic-store {}))
  ([{:keys [seekers]}]
   (let [s (->DatomicStore (d/create-conn schema))]
     (with-seekers s seekers))))

(defn datomic-seed-db
  "A DatomicStore seeded with the demo seeker set -- the Datomic-
  backed analog of `seed-db`, used to prove protocol parity."
  []
  (datomic-store (demo-data)))
