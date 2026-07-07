(ns navigator.facts
  "Per-jurisdiction health-access-navigation/eligibility-determination
  regulatory catalog -- the G2-style spec-basis table the Health
  Access Governor checks every `:eligibility/verify` proposal against
  ('did the advisor cite an OFFICIAL public source for this
  jurisdiction's health-access-navigation and eligibility-
  determination framework, or did it invent one?').

  Coverage is reported HONESTLY (see `coverage`), the same discipline
  every sibling actor's `facts` namespace uses: a jurisdiction not in
  this table has NO spec-basis, full stop -- the advisor must not
  fabricate one, and the governor holds if it tries.

  Seed values are drawn from each jurisdiction's official public-
  health/health-benefits authority and health-information-disclosure
  law (see `:provenance`); they are a STARTING catalog, not a from-
  scratch survey of all ~194 jurisdictions. Extending coverage is
  additive: add one map to `catalog`, cite a real source, done --
  never invent a jurisdiction's requirements to make coverage look
  bigger.")

(def catalog
  "iso3 -> requirement map. `:required-evidence` mirrors the generic
  consent-record/eligibility-determination-record/provider-directory-
  verification-record/purpose-limitation-record evidence set every
  prior sibling's evidence checklist submits in some form;
  `:legal-basis` / `:owner-authority` / `:provenance` are the G2
  citation the governor requires before any `:actuation/finalize-
  referral`/`:actuation/disclose-health-information` proposal can
  commit."
  {"JPN" {:name "Japan"
          :owner-authority "厚生労働省 (Ministry of Health, Labour and Welfare) -- 医療費助成・受療権擁護"
          :legal-basis "生活保護法 (Public Assistance Act, medical assistance) / 個人情報の保護に関する法律 (APPI, 要配慮個人情報)"
          :national-spec "医療アクセス・ナビゲーション事業者の受給資格判定要件および医療情報開示の同意要件"
          :provenance "https://www.mhlw.go.jp/stf/seisakunitsuite/bunya/hukushi_kaigo/seikatsuhogo/index.html"
          :required-evidence ["同意記録 (consent-record)"
                              "受給資格判定記録 (eligibility-determination-record)"
                              "提供者名簿確認記録 (provider-directory-verification-record)"
                              "目的制限記録 (purpose-limitation-record)"]}
   "USA" {:name "United States"
          :owner-authority "Health Resources and Services Administration (HRSA) / HHS Office for Civil Rights (HIPAA)"
          :legal-basis "Public Health Service Act, 42 U.S.C. §254b et seq. / Health Insurance Portability and Accountability Act (HIPAA), 45 C.F.R. Parts 160/164"
          :national-spec "Community health-navigation program eligibility and protected-health-information disclosure requirements"
          :provenance "https://www.hrsa.gov/about/what-we-do"
          :required-evidence ["Consent record"
                              "Eligibility-determination record"
                              "Provider-directory verification record"
                              "Purpose-limitation record"]}
   "GBR" {:name "United Kingdom"
          :owner-authority "NHS England / Information Commissioner's Office (ICO)"
          :legal-basis "National Health Service Act 2006 / UK GDPR + Data Protection Act 2018 (special category health data)"
          :national-spec "NHS-referral navigation eligibility and health-data disclosure requirements"
          :provenance "https://www.england.nhs.uk/2021/03/health-and-care-bill/"
          :required-evidence ["Consent record"
                              "Eligibility-determination record"
                              "Provider-directory verification record"
                              "Purpose-limitation record"]}
   "DEU" {:name "Germany"
          :owner-authority "Bundesministerium für Gesundheit (BMG) / Landesdatenschutzbehörden"
          :legal-basis "Fünftes Buch Sozialgesetzbuch (SGB V, gesetzliche Krankenversicherung) / Bundesdatenschutzgesetz (BDSG, besondere Kategorien Gesundheitsdaten)"
          :national-spec "Registrierung von Gesundheitszugangs-Navigationsdiensten und Offenlegungsanforderungen für Gesundheitsdaten"
          :provenance "https://www.bundesgesundheitsministerium.de/themen/krankenversicherung.html"
          :required-evidence ["Einwilligungsprotokoll (consent-record)"
                              "Anspruchsberechtigungsnachweis (eligibility-determination-record)"
                              "Leistungserbringerverzeichnisnachweis (provider-directory-verification-record)"
                              "Zweckbindungsprotokoll (purpose-limitation-record)"]}})

(defn spec-basis
  "The jurisdiction's requirement map, or nil -- nil means NO spec-basis,
  and the governor must hold any proposal that tries to finalize a
  referral or disclose health information on it."
  [iso3]
  (get catalog iso3))

(defn coverage
  "Honest coverage report: how many of the requested jurisdictions actually
  have a spec-basis entry. Never report a missing jurisdiction as covered."
  ([] (coverage (keys catalog)))
  ([iso3s]
   (let [have (filter catalog iso3s)
         missing (remove catalog iso3s)]
     {:requested (count iso3s)
      :covered (count have)
      :covered-jurisdictions (vec (sort have))
      :missing-jurisdictions (vec (sort missing))
      :note (str "cloud-itonami-isic-8691 R0: " (count catalog)
                 " jurisdictions seeded with an official spec-basis. "
                 "This is a starting catalog, not a survey of all ~194 "
                 "jurisdictions -- extend `navigator.facts/catalog`, "
                 "never fabricate a jurisdiction's requirements.")})))

(defn required-evidence-satisfied?
  "Does `submitted` (a set/coll of evidence keywords or strings) satisfy
  every evidence item listed for `iso3`? Missing spec-basis -> never
  satisfied."
  [iso3 submitted]
  (when-let [{:keys [required-evidence]} (spec-basis iso3)]
    (let [need (count required-evidence)
          have (count (filter (set submitted) required-evidence))]
      (= need have))))

(defn evidence-checklist [iso3]
  (:required-evidence (spec-basis iso3) []))
