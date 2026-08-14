(ns navigator.render-html
  "Build-time HTML renderer for `docs/samples/operator-console.html`.

  Closes flagship checklist item 2 for this repo: `docs/samples/
  operator-console.html` previously existed as a HAND-WRITTEN stub
  (it described a `robotics` domain with placeholder rows `M1`/
  `robot-1`, matching neither this repo's `navigator` namespace nor
  its ISIC 8691 health-access-navigation domain, and there was no
  generator at all). This namespace replaces it with real output.

  It drives the REAL actor stack (`navigator.operation` ->
  `navigator.governor` -> `navigator.store`, through langgraph
  `g/run*`) over a scenario adapted from this repo's own
  `navigator.sim` demo driver (`clojure -M:dev:run`, confirmed BEFORE
  this file was written to produce a sensible ledger against the real
  seeded seeker ids `seeker-1`..`seeker-4`). Every entity, number and
  identifier on the page is read back out of the store after the run
  -- nothing is hand-typed.

  Determinism: the mock advisor is deterministic and the store is
  seeded fresh per run, so the document is byte-identical across
  reruns. No timestamps appear in the page content (verify by diffing
  two consecutive runs into scratch dirs).

  HARD-hold invariant: `-main` THROWS if the run produced zero
  `:governor-hold` facts. A console that shows no real hold is not
  evidence of a governor, so this is enforced at build time rather
  than left to convention (precedent: `cloud-itonami-isic-2513`).

  Usage: `clojure -M:dev:render-html [out-file]`
  (default `docs/samples/operator-console.html`)."
  (:require [jp-go-dds.skin]
            [clojure.string :as str]
            [navigator.facts :as facts]
            [navigator.governor :as governor]
            [navigator.phase :as phase]
            [navigator.store :as store]
            [navigator.operation :as op]
            [langgraph.graph :as g]))

(def ^:private operator
  {:actor-id "op-1" :actor-role :care-navigator :phase 3})

(defn- exec! [actor tid request]
  (g/run* actor {:request request :context operator} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}}
          {:thread-id tid :resume? true}))

(defn run-demo!
  "Runs a fresh seeded store through a scenario mixing every
  disposition this actor can reach.

  seeker-1 clears a full lifecycle -- intake (auto-commits clean at
  phase 3: `:seeker/intake` is the only member of phase 3's `:auto`
  set), an eligibility verification against JPN's official spec-basis
  (phase-gated, approved), an urgent-health-risk screening (approved),
  a referral finalization (ALWAYS escalates -- `:actuation/finalize-
  referral` is never auto-eligible at any phase -- approved) and a
  health-information disclosure (`:actuation/disclose-health-
  information`, same posture -- approved).

  Then FIVE distinct HARD holds, none of which ever reaches a human:
    - seeker-2  `:no-spec-basis`                            -- jurisdiction ATL is deliberately absent from `navigator.facts/catalog`
    - seeker-3  `:eligibility-window-elapsed-exceeds-validity` -- elapsed 100 days > its own recorded 90-day validity window
    - seeker-4  `:urgent-health-risk-unresolved`            -- screened directly via `:risk/screen`, which HARD-holds on its own finding
    - seeker-1  `:already-referred`                         -- double referral finalization
    - seeker-1  `:already-disclosed`                        -- double health-information disclosure

  Returns `{:db store :audit [..]}`. The `:audit` vector is the union
  of every graph run's audit channel, which is where `:approval-
  granted` facts live -- `navigator.operation`'s `:commit` node
  appends only the commit-fact to the store ledger, so the approver's
  identity is NOT on the ledger and has to be joined from here. See
  `commit-attribution-rows`."
  []
  (let [db (store/seed-db)
        actor (op/build db)
        runs (atom [])
        run! (fn [r] (swap! runs conj r) r)]

    (run! (exec! actor "t1" {:op :seeker/intake :subject "seeker-1"
                             :patch {:id "seeker-1" :seeker-name "Sato Aiko"}}))

    (run! (exec! actor "t2" {:op :eligibility/verify :subject "seeker-1"}))
    (run! (approve! actor "t2"))

    (run! (exec! actor "t3" {:op :risk/screen :subject "seeker-1"}))
    (run! (approve! actor "t3"))

    (run! (exec! actor "t4" {:op :actuation/finalize-referral :subject "seeker-1"}))
    (run! (approve! actor "t4"))

    (run! (exec! actor "t5" {:op :actuation/disclose-health-information :subject "seeker-1"}))
    (run! (approve! actor "t5"))

    (run! (exec! actor "t6" {:op :eligibility/verify :subject "seeker-2" :no-spec? true}))

    (run! (exec! actor "t7" {:op :eligibility/verify :subject "seeker-3"}))
    (run! (approve! actor "t7"))

    (run! (exec! actor "t8" {:op :actuation/finalize-referral :subject "seeker-3"}))

    (run! (exec! actor "t9" {:op :risk/screen :subject "seeker-4"}))

    (run! (exec! actor "t10" {:op :actuation/finalize-referral :subject "seeker-1"}))

    (run! (exec! actor "t11" {:op :actuation/disclose-health-information :subject "seeker-1"}))

    {:db db
     :audit (into [] (mapcat #(get-in % [:state :audit])) @runs)}))

;; ----------------------------- helpers -----------------------------

(defn- esc [v]
  (-> (str v)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")))

(defn- nm [v] (if (keyword? v) (name v) (str v)))

(defn- holds
  "Every HARD `:governor-hold` fact on the ledger."
  [db]
  (filterv #(= :governor-hold (:t %)) (store/ledger db)))

(defn- commits [db]
  (filterv #(= :committed (:t %)) (store/ledger db)))

;; ---------------- approver attribution (DERIVED, not asserted) -------------

(def ^:private approver-keys
  "Keys any store backend might use to retain the approving human.
  Checked generically so this page SELF-CORRECTS: if a backend starts
  retaining attribution on a record, the derivation below reports it
  as retained without this file changing."
  #{:approved-by :approver :approved_by "approved-by" "approved_by" "approver"})

(defn- approver-in
  "The approver actually present in a stored register/record, or nil.
  Walks the map rather than assuming a fixed shape."
  [m]
  (when (map? m)
    (some (fn [[k v]] (when (and (contains? approver-keys k) (some? v)) v)) m)))

(defn- register-for
  "The artifact the store actually RETAINED for a committed op --
  i.e. what a later auditor would be able to read back."
  [db {:keys [op subject]}]
  (case op
    :seeker/intake       (store/seeker db subject)
    :eligibility/verify  (store/eligibility-of db subject)
    :risk/screen         (store/risk-screen-of db subject)
    :actuation/finalize-referral
    (first (filter #(= subject (get % "seeker_id")) (store/referral-history db)))
    :actuation/disclose-health-information
    (first (filter #(= subject (get % "seeker_id")) (store/disclosure-history db)))
    nil))

(defn- approver-from-audit
  "The approving human for this op/subject, joined from the run audit
  (`:approval-granted`). This is the only place the identity survives
  for actuation records."
  [audit {:keys [op subject]}]
  (some (fn [f]
          (when (and (= :approval-granted (:t f))
                     (= op (:op f)) (= subject (:subject f)))
            (:by f)))
        audit))

(defn- attribution
  "Classifies a committed op into one of three HONEST states -- the
  reader must be able to tell 'nobody approved' from 'the store did
  not keep it'.

    :retained    -- an approver key is present in the stored record
    :audit-only  -- a human DID approve, but the record dropped it
    :auto-commit -- no human was involved (phase-3 auto set)"
  [db audit fact]
  (let [reg (register-for db fact)
        in-record (approver-in reg)
        in-audit (approver-from-audit audit fact)]
    (cond
      (some? in-record) {:state :retained :by in-record}
      (some? in-audit)  {:state :audit-only :by in-audit}
      :else             {:state :auto-commit :by nil})))

;; ----------------------------- rows -----------------------------

(defn- last-fact-for [ledger id]
  (last (filter #(= (:subject %) id) ledger)))

(defn- status-cell [ledger id]
  (let [f (last-fact-for ledger id)]
    (cond
      (nil? f) "<span class=\"muted\">no activity</span>"
      (= :governor-hold (:t f))
      (str "<span class=\"critical\">HARD hold &middot; "
           (esc (nm (or (-> f :violations first :rule) :unknown))) "</span>")
      (= :committed (:t f)) "<span class=\"ok\">committed</span>"
      :else "<span class=\"muted\">in progress</span>")))

(defn- lifecycle-cell [{:keys [referral-finalized? disclosure-made?]}]
  (cond
    (and referral-finalized? disclosure-made?)
    "<span class=\"ok\">referred &amp; disclosed</span>"
    referral-finalized? "<span class=\"warn\">referred, not yet disclosed</span>"
    :else "<span class=\"muted\">in navigation</span>"))

(defn- window-cell
  "Elapsed vs the seeker's OWN recorded validity window. Renders the
  un-checkable case distinctly -- `navigator.registry` treats a
  missing figure as NOT within limits, and so must the page."
  [{:keys [eligibility-elapsed-days eligibility-validity-window-days]}]
  (cond
    (not (and (number? eligibility-elapsed-days)
              (number? eligibility-validity-window-days)))
    "<span class=\"critical\">un-checkable</span>"

    (> eligibility-elapsed-days eligibility-validity-window-days)
    (format "<span class=\"critical\">%s / %s &mdash; expired</span>"
            (esc eligibility-elapsed-days) (esc eligibility-validity-window-days))

    :else
    (format "<span class=\"ok\">%s / %s</span>"
            (esc eligibility-elapsed-days) (esc eligibility-validity-window-days))))

(defn- seeker-row [ledger {:keys [id seeker-name jurisdiction urgent-health-risk-unresolved?
                                  referral-number disclosure-number] :as sk}]
  (format (str "        <tr><td><code>%s</code></td><td>%s</td><td>%s</td><td class=\"num\">%s</td>"
               "<td>%s</td><td>%s</td><td><code>%s</code></td><td><code>%s</code></td><td>%s</td></tr>")
          (esc id) (esc seeker-name) (esc jurisdiction)
          (window-cell sk)
          (if urgent-health-risk-unresolved?
            "<span class=\"critical\">unresolved</span>"
            "<span class=\"ok\">none</span>")
          (lifecycle-cell sk)
          (esc (or referral-number "—"))
          (esc (or disclosure-number "—"))
          (status-cell ledger id)))

(defn- hold-row [{:keys [op subject confidence violations]}]
  (let [{:keys [rule detail]} (first violations)]
    (format (str "        <tr><td><span class=\"critical\">HARD</span></td><td><code>%s</code></td>"
                 "<td><code>%s</code></td><td><code>%s</code></td><td>%s</td><td class=\"num\">%s</td></tr>")
            (esc (nm rule)) (esc (nm op)) (esc subject) (esc detail) (esc confidence))))

(defn- ledger-row [{:keys [t op subject disposition basis]}]
  (format "        <tr><td>%s</td><td><code>%s</code></td><td><code>%s</code></td><td>%s</td></tr>"
          (esc (nm t)) (esc (nm (or op :n-a))) (esc subject)
          (esc (or (some->> basis (map nm) (str/join ", ")) (some-> disposition nm) ""))))

(defn- jurisdiction-row [iso3]
  (let [{:keys [name owner-authority legal-basis provenance required-evidence]}
        (facts/spec-basis iso3)]
    (format (str "        <tr><td><code>%s</code></td><td>%s</td><td>%s</td><td>%s</td>"
                 "<td class=\"num\">%s</td><td><a href=\"%s\">source</a></td></tr>")
            (esc iso3) (esc name) (esc owner-authority) (esc legal-basis)
            (esc (count required-evidence)) (esc provenance))))

(defn- phase-row [[n {:keys [label writes auto]}]]
  (format "        <tr><td class=\"num\">%s</td><td>%s</td><td>%s</td><td>%s</td></tr>"
          (esc n) (esc label)
          (if (seq writes)
            (str/join ", " (map #(str "<code>" (esc (nm %)) "</code>") (sort (map nm writes))))
            "<span class=\"muted\">none</span>")
          (if (seq auto)
            (str/join ", " (map #(str "<code>" (esc (nm %)) "</code>") (sort (map nm auto))))
            "<span class=\"muted\">none</span>")))

(defn- attribution-row [db audit {:keys [op subject] :as fact}]
  (let [{:keys [state by]} (attribution db audit fact)]
    (format "        <tr><td><code>%s</code></td><td><code>%s</code></td><td>%s</td></tr>"
            (esc (nm op)) (esc subject)
            (case state
              :retained
              (format "<span class=\"ok\">%s</span> &mdash; retained in record" (esc by))
              :audit-only
              (format "<span class=\"warn\">%s</span> &mdash; audit only, not retained in record" (esc by))
              :auto-commit
              "<span class=\"muted\">no human approver &mdash; phase-3 auto-commit</span>"))))

(defn- record-row [rec approver]
  (format (str "        <tr><td><code>%s</code></td><td>%s</td><td><code>%s</code></td>"
               "<td><code>%s</code></td><td>%s</td><td>%s</td></tr>")
          (esc (get rec "record_id")) (esc (get rec "kind"))
          (esc (get rec "seeker_id")) (esc (get rec "jurisdiction"))
          (if (get rec "immutable")
            "<span class=\"ok\">immutable</span>" "<span class=\"muted\">—</span>")
          approver))

;; ----------------------------- gate description -----------------------------

(defn- gate-rows
  "The governor's own contract, read out of the real namespaces
  (`navigator.governor/high-stakes`, `/confidence-floor`,
  `navigator.phase/phases`) rather than re-typed here."
  []
  (let [auto (get-in phase/phases [phase/default-phase :auto])
        writes (get-in phase/phases [phase/default-phase :writes])
        describe (fn [o]
                   (cond
                     (contains? governor/high-stakes o)
                     "<span class=\"critical\">ALWAYS human approval</span> &middot; never auto at any phase (governor high-stakes AND absent from every phase's auto set &mdash; two independent layers)"
                     (contains? auto o)
                     "<span class=\"ok\">phase-3 auto-commit when governor-clean</span>"
                     :else
                     "<span class=\"warn\">phase-3: human approval (not auto-eligible)</span>"))]
    (mapv (fn [o]
            (format "        <tr><td><code>%s</code></td><td>%s</td></tr>"
                    (esc (nm o)) (describe o)))
          (sort-by nm writes))))

(def ^:private hard-rules
  "The six HARD checks `navigator.governor/check` concatenates, in the
  order it evaluates them. A human approver cannot override any of
  them."
  [[:no-spec-basis "eligibility/verify + both actuations" "Did the proposal cite an OFFICIAL source from navigator.facts, or invent one?"]
   [:evidence-incomplete "both actuations" "Is the jurisdiction's full consent / eligibility-determination / provider-directory / purpose-limitation evidence set actually on file?"]
   [:eligibility-window-elapsed-exceeds-validity "actuation/finalize-referral" "Independently recomputes elapsed vs the seeker's own recorded validity window. Un-checkable (either figure missing) is NOT within limits."]
   [:urgent-health-risk-unresolved "evaluated unconditionally" "An unresolved urgent health risk — reported by this proposal itself, or already on file — blocks outright."]
   [:already-referred "actuation/finalize-referral" "Refuses a second referral finalization for the same seeker, off a dedicated :referral-finalized? fact (never a :status value)."]
   [:already-disclosed "actuation/disclose-health-information" "Refuses a second health-information disclosure for the same seeker, off a dedicated :disclosure-made? fact."]])

(defn- hard-rule-rows [fired]
  (mapv (fn [[rule scope detail]]
          (format (str "        <tr><td><code>%s</code></td><td>%s</td><td>%s</td><td>%s</td></tr>")
                  (esc (nm rule)) (esc scope) (esc detail)
                  (if (contains? fired rule)
                    "<span class=\"critical\">fired this run</span>"
                    "<span class=\"muted\">not exercised</span>")))
        hard-rules))

;; ----------------------------- rendering -----------------------------

(defn render
  "Renders the operator console from a `run-demo!` result. Every value
  is read back out of the store/ledger produced by the real run."
  [{:keys [db audit]}]
  (let [ledger (vec (store/ledger db))
        seekers (store/all-seekers db)
        hs (holds db)
        fired (set (map #(-> % :violations first :rule) hs))
        cs (commits db)
        cov (facts/coverage (sort (distinct (keep :jurisdiction seekers))))
        referrals (store/referral-history db)
        disclosures (store/disclosure-history db)
        approver-for (fn [op subject]
                       (let [{:keys [state by]} (attribution db audit {:op op :subject subject})]
                         (case state
                           :retained (format "<span class=\"ok\">%s</span>" (esc by))
                           :audit-only (format "<span class=\"warn\">%s</span> <span class=\"muted\">(audit only &mdash; not retained in record)</span>" (esc by))
                           :auto-commit "<span class=\"muted\">—</span>")))]
    (str
     "<!DOCTYPE html>\n<html lang=\"en\">\n<head><meta charset=\"utf-8\">"
     "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1, viewport-fit=cover\">"
     "<meta name=\"color-scheme\" content=\"light\"><meta name=\"theme-color\" content=\"#ffffff\">"
     "<title>cloud-itonami-isic-8691 &middot; health-access-navigation</title><style>"
     (jp-go-dds.skin/dds+skin)
     "</style></head>\n<body>\n"
     "<header class=\"bar\">\n"
     "  <h1>Health access navigation &amp; eligibility determination (ISIC 8691) — Operator Console</h1>\n"
     "  <span class=\"badge\">read-only sample · governor-gated · referral finalization / health-information disclosure always human-approved</span>\n"
     "</header>\n"
     "<main>\n"

     ;; ---- seekers ----
     "  <section class=\"card\">\n"
     "    <h2>Seeker directory</h2>\n"
     "    <p class=\"subtitle\">Build-time snapshot generated from <code>navigator.store</code> through the real actor graph by <code>navigator.render-html</code> (<code>clojure -M:dev:render-html</code>). Eligibility window is elapsed days / the seeker's OWN recorded validity window.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Seeker</th><th>Name</th><th>Jurisdiction</th><th>Eligibility window</th><th>Urgent health risk</th><th>Lifecycle</th><th>Referral no.</th><th>Disclosure no.</th><th>Last op</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" (map (partial seeker-row ledger) seekers)) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"

     ;; ---- HARD holds ----
     "  <section class=\"card\">\n"
     "    <h2>Governor HARD holds — this run</h2>\n"
     "    <p class=\"subtitle\">A human approver <strong>cannot</strong> override any of these. None of the rows below ever reached a human; each was refused before the approval node.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Class</th><th>Rule</th><th>Op</th><th>Seeker</th><th>Detail (from the governor)</th><th>Advisor confidence</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" (map hold-row hs)) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"

     ;; ---- hard rule contract ----
     "  <section class=\"card\">\n"
     "    <h2>HARD check contract (Health Access Governor)</h2>\n"
     "    <p class=\"subtitle\">The six checks <code>navigator.governor/check</code> evaluates. &ldquo;not exercised&rdquo; means this scenario did not trigger it — not that it is absent.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Rule</th><th>Scope</th><th>What it verifies</th><th>This run</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" (hard-rule-rows fired)) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"

     ;; ---- action gate ----
     "  <section class=\"card\">\n"
     "    <h2>Action gate</h2>\n"
     "    <p class=\"subtitle\">Derived from <code>navigator.governor/high-stakes</code>, <code>/confidence-floor</code> ("
     (esc governor/confidence-floor)
     ") and <code>navigator.phase/phases</code> at phase " (esc phase/default-phase) ".</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Op</th><th>Gate</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" (gate-rows)) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"

     ;; ---- phases ----
     "  <section class=\"card\">\n"
     "    <h2>Rollout phase gate</h2>\n"
     "    <p class=\"subtitle\">Read from <code>navigator.phase/phases</code>. Note that the two actuation ops appear in phase 3's writes but in <em>no</em> phase's auto set — a permanent structural fact, not a milestone still to come.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Phase</th><th>Label</th><th>May write</th><th>May auto-commit</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" (map phase-row (sort-by key phase/phases))) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"

     ;; ---- jurisdictions ----
     "  <section class=\"card\">\n"
     "    <h2>Jurisdiction spec-basis catalog</h2>\n"
     "    <p class=\"subtitle\">From <code>navigator.facts/catalog</code>. Coverage is reported honestly: of the "
     (esc (:requested cov)) " jurisdiction(s) present in the seeker directory above, <strong>"
     (esc (:covered cov)) "</strong> have an official spec-basis"
     (if (seq (:missing-jurisdictions cov))
       (str " and <span class=\"critical\">" (esc (str/join ", " (:missing-jurisdictions cov)))
            "</span> has none — which is exactly why its seeker HARD-holds above. A jurisdiction not in this table has NO spec-basis; the advisor must not invent one.")
       ".")
     "</p>\n"
     "    <table>\n"
     "      <thead><tr><th>ISO3</th><th>Jurisdiction</th><th>Owner authority</th><th>Legal basis</th><th>Required evidence</th><th>Provenance</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" (map jurisdiction-row (sort (keys facts/catalog)))) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"

     ;; ---- draft records ----
     "  <section class=\"card\">\n"
     "    <h2>Draft referral records</h2>\n"
     "    <p class=\"subtitle\">Built by <code>navigator.registry/register-referral</code>. Every certificate this actor produces is UNSIGNED — signing is the navigation operator's own act, not this actor's.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Record</th><th>Kind</th><th>Seeker</th><th>Jurisdiction</th><th>Immutable</th><th>Approved by</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" (map #(record-row % (approver-for :actuation/finalize-referral (get % "seeker_id")))
                         referrals)) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>Draft health-information disclosure records</h2>\n"
     "    <p class=\"subtitle\">Built by <code>navigator.registry/register-disclosure</code>, under the seeker's consent and purpose-limitation evidence.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Record</th><th>Kind</th><th>Seeker</th><th>Jurisdiction</th><th>Immutable</th><th>Approved by</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" (map #(record-row % (approver-for :actuation/disclose-health-information (get % "seeker_id")))
                         disclosures)) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"

     ;; ---- attribution ----
     "  <section class=\"card\">\n"
     "    <h2>Commit attribution</h2>\n"
     "    <p class=\"subtitle\">Derived at render time by walking what the store actually retained, so this table self-corrects if a backend starts keeping attribution. Three states are distinguished deliberately: a reader must be able to tell <em>nobody approved</em> from <em>the store did not keep it</em>. <code>navigator.operation</code>'s commit node appends only the commit-fact to the ledger, so for the two actuation records the approver survives only in the run audit (<code>:approval-granted</code>) and is joined from there.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Op</th><th>Seeker</th><th>Approver</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" (map (partial attribution-row db audit) cs)) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"

     ;; ---- ledger ----
     "  <section class=\"card\">\n"
     "    <h2>Audit ledger — this run</h2>\n"
     "    <p class=\"subtitle\">Append-only decision-fact log: " (esc (count ledger))
     " facts (" (esc (count cs)) " commits, " (esc (count hs)) " HARD holds).</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Fact</th><th>Op</th><th>Seeker</th><th>Basis</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" (map ledger-row ledger)) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"

     "  <footer>\n"
     "    <p>Generated by <code>navigator.render-html</code> from a fresh <code>navigator.store/seed-db</code> run — no hand-written rows. "
     "This actor never dispatches care, never diagnoses, and never finalizes a referral or discloses health information without a human care-navigator.</p>\n"
     "  </footer>\n"
     "</main>\n</body>\n</html>\n")))

(defn -main [& args]
  (let [out (or (first args) "docs/samples/operator-console.html")
        {:keys [db] :as result} (run-demo!)
        hs (holds db)]
    ;; A console that shows no real HARD hold is not evidence of a
    ;; governor. Enforced at build time, not left to convention.
    (when (empty? hs)
      (throw (ex-info (str "no :governor-hold fact on the ledger — refusing to write a "
                           "console that shows no real hold")
                      {:ledger-facts (count (store/ledger db))})))
    (let [f (java.io.File. ^String out)]
      (when-let [p (.getParentFile f)] (.mkdirs p))
      (spit f (render result)))
    (println "wrote" out
             (str "(" (count (store/ledger db)) " ledger facts, "
                  (count hs) " HARD holds ["
                  (str/join " " (map #(nm (-> % :violations first :rule)) hs))
                  "], " (count (store/referral-history db)) " referral drafts, "
                  (count (store/disclosure-history db)) " disclosure drafts)"))))
