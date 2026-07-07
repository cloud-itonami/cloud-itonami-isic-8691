(ns navigator.navigatoradvisor
  "NavigatorOps-LLM client -- the *contained intelligence node* for the
  navigator actor (README: \"Care Navigator\").

  It normalizes seeker-intake, drafts a per-jurisdiction health-
  access-navigation evidence checklist, screens seekers for an
  unresolved urgent health risk, drafts the referral-finalization
  action, and drafts the health-information-disclosure action.
  CRITICAL: it is a smart-but-untrusted advisor. It returns a
  *proposal* (with a rationale + the fields it cited), never a
  committed record or a real referral finalization/health-information
  disclosure. Every output is censored downstream by `navigator.
  governor` before anything touches the SSoT, and `:actuation/
  finalize-referral`/`:actuation/disclose-health-information`
  proposals NEVER auto-commit at any phase -- see README `Actuation`.

  Like every sibling actor's advisor, this is a deterministic mock so
  the actor graph runs offline and the governor contract is exercised
  end-to-end. In production this calls a real LLM (kotoba-llm or
  equivalent) with the same proposal shape.

  Proposal shape (all kinds):
    {:summary    str            ; human-facing draft / finding
     :rationale  str            ; why -- SCANNED by the spec-basis gate
     :cites      [kw|str ..]    ; facts/sources the LLM used -- SCANNED too
     :effect     kw             ; how a commit would mutate the SSoT
     :stake      kw|nil         ; :actuation/finalize-referral | :actuation/disclose-health-information | nil
     :confidence 0..1}"
  (:require #?(:clj  [clojure.edn :as edn]
               :cljs [cljs.reader :as edn])
            [clojure.string :as str]
            [navigator.facts :as facts]
            [navigator.registry :as registry]
            [navigator.store :as store]
            [langchain.model :as model]))

(defn- normalize-intake
  "Directory upsert -- the LLM only normalizes/validates the patch; it
  does not invent the seeker, jurisdiction or eligibility status. High
  confidence, low stakes."
  [_db {:keys [patch]}]
  {:summary    (str "対象者記録更新: " (pr-str (keys patch)))
   :rationale  "入力 patch の正規化のみ。新規事実の生成なし。"
   :cites      (vec (keys patch))
   :effect     :seeker/upsert
   :value      patch
   :stake      nil
   :confidence 0.97})

(defn- verify-eligibility
  "Per-jurisdiction health-access-navigation evidence checklist draft.
  `:no-spec?` injects the failure mode we must defend against:
  proposing a checklist for a jurisdiction with NO official spec-basis
  in `navigator.facts` -- the Health Access Governor must reject this
  (never invent a jurisdiction's requirements)."
  [db {:keys [subject no-spec?]}]
  (let [sk (store/seeker db subject)
        iso3 (if no-spec? "ATL" (:jurisdiction sk))
        sb (facts/spec-basis iso3)]
    (if (nil? sb)
      {:summary    (str iso3 " の公式spec-basisが見つかりません")
       :rationale  "navigator.facts に未登録の法域。要件を推測で作らない。"
       :cites      []
       :effect     :eligibility/set
       :value      {:jurisdiction iso3 :checklist [] :spec-basis nil}
       :stake      nil
       :confidence 0.9}
      {:summary    (str iso3 " (" (:owner-authority sb) ") 向け必要書類 "
                        (count (:required-evidence sb)) " 件を提案")
       :rationale  (str "公式ソース: " (:provenance sb) " / 法的根拠: " (:legal-basis sb))
       :cites      [(:legal-basis sb) (:provenance sb)]
       :effect     :eligibility/set
       :value      {:jurisdiction iso3
                    :checklist (:required-evidence sb)
                    :spec-basis (:provenance sb)
                    :legal-basis (:legal-basis sb)}
       :stake      nil
       :confidence 0.9})))

(defn- screen-urgent-risk
  "Urgent-health-risk screening draft. `:urgent-health-risk-
  unresolved?` on the seeker record injects the failure mode: the
  Health Access Governor must HOLD, un-overridably, on any unresolved
  risk."
  [db {:keys [subject]}]
  (let [sk (store/seeker db subject)]
    (cond
      (nil? sk)
      {:summary "対象者記録が見つかりません" :rationale "no seeker record"
       :cites [] :effect :risk-screen/set :value {:seeker-id subject :verdict :unknown}
       :stake nil :confidence 0.0}

      (true? (:urgent-health-risk-unresolved? sk))
      {:summary    (str (:seeker-name sk) ": 未解決の緊急健康リスクを検出")
       :rationale  "スクリーニングが未解決の緊急健康リスクを検出。人手確認とホールドが必須。"
       :cites      [:urgent-risk-check]
       :effect     :risk-screen/set
       :value      {:seeker-id subject :verdict :unresolved}
       :stake      nil
       :confidence 0.95}

      :else
      {:summary    (str (:seeker-name sk) ": 未解決の緊急健康リスクなし")
       :rationale  "緊急健康リスクスクリーニング完了。"
       :cites      [:urgent-risk-check]
       :effect     :risk-screen/set
       :value      {:seeker-id subject :verdict :resolved}
       :stake      nil
       :confidence 0.9})))

(defn- propose-referral
  "Draft the actual REFERRAL action -- finalizing a real referral
  routing a seeker to a care provider. ALWAYS `:stake :actuation/
  finalize-referral` -- this is a REAL-WORLD health-access act, never
  a draft the actor may auto-run. See README `Actuation`: no phase
  ever adds this op to a phase's `:auto` set (`navigator.phase`); the
  governor also always escalates on `:actuation/finalize-referral`.
  Two independent layers agree, deliberately."
  [db {:keys [subject]}]
  (let [sk (store/seeker db subject)]
    {:summary    (str subject " 向け紹介確定提案"
                      (when sk (str " (seeker=" (:seeker-name sk) ")")))
     :rationale  (if sk
                   (str "eligibility-elapsed-days=" (:eligibility-elapsed-days sk)
                        " eligibility-validity-window-days=" (:eligibility-validity-window-days sk))
                   "対象者記録が見つかりません")
     :cites      (if sk [subject] [])
     :effect     :seeker/mark-referred
     :value      {:seeker-id subject}
     :stake      :actuation/finalize-referral
     :confidence (if (and sk (not (registry/eligibility-window-elapsed-exceeds-validity? sk))) 0.9 0.3)}))

(defn- propose-disclosure
  "Draft the actual HEALTH-INFORMATION-DISCLOSURE action -- disclosing
  a real seeker's health information to a third party under consent.
  ALWAYS `:stake :actuation/disclose-health-information` -- this is a
  REAL-WORLD health-access act, never a draft the actor may auto-run.
  See README `Actuation`: no phase ever adds this op to a phase's
  `:auto` set (`navigator.phase`); the governor also always escalates
  on `:actuation/disclose-health-information`. Two independent layers
  agree, deliberately."
  [db {:keys [subject]}]
  (let [sk (store/seeker db subject)]
    {:summary    (str subject " 向け健康情報開示提案"
                      (when sk (str " (seeker=" (:seeker-name sk) ")")))
     :rationale  (if sk
                   "consent-record referenced"
                   "対象者記録が見つかりません")
     :cites      (if sk [subject] [])
     :effect     :seeker/mark-disclosed
     :value      {:seeker-id subject}
     :stake      :actuation/disclose-health-information
     :confidence (if sk 0.9 0.3)}))

(defn infer
  "Route a request to the right proposal generator.
  request: {:op kw :subject id ...op-specific...}"
  [db {:keys [op] :as request}]
  (case op
    :seeker/intake                          (normalize-intake db request)
    :eligibility/verify                     (verify-eligibility db request)
    :risk/screen                            (screen-urgent-risk db request)
    :actuation/finalize-referral             (propose-referral db request)
    :actuation/disclose-health-information   (propose-disclosure db request)
    {:summary "未対応の操作" :rationale (str op) :cites []
     :effect :noop :stake nil :confidence 0.0}))

;; ----------------------------- Advisor protocol -----------------------------

(defprotocol Advisor
  (-advise [advisor store request] "store + request -> proposal map"))

(defn mock-advisor
  "The deterministic advisor (the `infer` logic above). Default everywhere."
  [] (reify Advisor (-advise [_ st req] (infer st req))))

(def ^:private system-prompt
  (str "あなたは医療アクセス・ナビゲーション事業の紹介確定・健康情報開示エージェントの"
       "助言者です。与えられた事実のみに基づき、提案を1つだけEDNマップで返します。"
       "説明や前置きは一切書かず、EDNだけを出力します。\n"
       "キー: :summary(人向けドラフト) :rationale(根拠/必ず事実から) "
       ":cites(使った事実キーのベクタ) "
       ":effect(:seeker/upsert|:eligibility/set|:risk-screen/set|"
       ":seeker/mark-referred|:seeker/mark-disclosed) "
       ":stake(:actuation/finalize-referral か :actuation/disclose-health-information か nil) :confidence(0..1)。\n"
       "重要: 登録されていない法域の要件を絶対に創作してはいけません。"
       "spec-basisが無い場合は :cites を空にし confidence を上げないこと。診断や医学的判断を行わないこと。"))

(defn- facts-for [st {:keys [op subject]}]
  (case op
    :eligibility/verify                     {:seeker (store/seeker st subject)}
    :risk/screen                            {:seeker (store/seeker st subject)}
    :actuation/finalize-referral             {:seeker (store/seeker st subject)}
    :actuation/disclose-health-information   {:seeker (store/seeker st subject)}
    {:seeker (store/seeker st subject)}))

(defn- parse-proposal
  "Parse the model's EDN proposal defensively. Any parse/shape failure
  yields a safe low-confidence noop so the Health Access Governor
  escalates/holds -- an LLM hiccup can never auto-finalize a referral
  or auto-disclose health information."
  [content]
  (let [p (try (edn/read-string (str/trim (str content)))
               (catch #?(:clj Exception :cljs :default) _ nil))]
    (if (map? p)
      (-> p
          (update :cites #(vec (or % [])))
          (update :confidence #(if (number? %) (double %) 0.0))
          (update :effect #(or % :noop)))
      {:summary "LLM応答を解釈できませんでした" :rationale (str content)
       :cites [] :effect :noop :stake nil :confidence 0.0})))

(defn llm-advisor
  "An advisor backed by a `langchain.model/ChatModel` (real inference)."
  ([chat-model] (llm-advisor chat-model {}))
  ([chat-model gen-opts]
   (reify Advisor
     (-advise [_ st req]
       (let [msgs [{:role :system :content system-prompt}
                   {:role :user :content (str "操作: " (:op req)
                                              "\n対象: " (:subject req)
                                              "\n事実: " (pr-str (facts-for st req)))}]
             resp (model/-generate chat-model msgs gen-opts)]
         (parse-proposal (:content resp)))))))

(defn trace
  "Decision-grounded audit record -- persisted to the :audit channel."
  [request proposal]
  {:t          :navigatoradvisor-proposal
   :op         (:op request)
   :subject    (:subject request)
   :summary    (:summary proposal)
   :rationale  (:rationale proposal)
   :cites      (:cites proposal)
   :confidence (:confidence proposal)})
