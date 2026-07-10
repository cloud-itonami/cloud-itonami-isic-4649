(ns housewaretrade.facts
  "Per-jurisdiction downstream household-goods-wholesale regulatory
  catalog -- the G2-style spec-basis table the Consumer Product Safety
  Governor checks every `:safety/verify` proposal against ('did the
  advisor cite an OFFICIAL public source for this jurisdiction's
  consumer-product-safety / sanctions requirements, or did it invent
  one?').

  Like every principal-trading sibling in this fleet, this catalog
  stays deliberately GENERIC -- the same shape as the fuel-wholesale /
  ag-machinery-wholesale siblings' own catalogs (credit-clearance
  record, contract/PO, sanctions-screening record) -- and does NOT fold
  the children's-product-certificate or active-recall facts into a
  per-jurisdiction checklist item: those are `housewaretrade.governor`'s
  own two dedicated HARD checks
  (`childrens-product-certificate-missing-violations` /
  `active-recall-unresolved-violations`), not jurisdiction-catalog
  checklist entries. See `housewaretrade.governor` namespace docstring
  for the full reasoning on why these are two SEPARATE checks with two
  genuinely DIFFERENT temporal shapes (pre-shipment certification vs.
  post-hoc discovered-defect reporting), rather than either being folded
  into this catalog or into each other.

  Each entry below is a REAL jurisdiction with a REAL downstream
  consumer-product-safety regime, cited for the household-goods-specific
  regulatory framework specifically (not merely a generic customs
  statute):

  - USA (the PRIMARY regime for this vertical): the U.S. Consumer
    Product Safety Commission (CPSC) administers the Consumer Product
    Safety Improvement Act of 2008 (CPSIA, Pub. L. 110-314), which set
    the strict lead-content limit for children's products (100 ppm total
    lead in accessible substrate, 15 U.S.C. §1278a, phased in fully by
    August 2011) and the permanent phthalate restrictions for children's
    toys and child care articles (CPSIA §108, codified at 16 C.F.R. Part
    1307). Separately, 16 C.F.R. Part 1303 bans lead-containing paint
    (>= 90 ppm lead) on furniture, toys and other consumer products --
    an OLDER, narrower rule (paint surface coating specifically) than
    the CPSIA total-lead-content limit, and this build cites BOTH
    because a household-goods wholesaler's furniture/toy catalog can
    implicate either or both depending on construction. Consumer
    Product Safety Act (CPSA) §14, 15 U.S.C. §2063, requires a General
    Certificate of Conformity (GCC) for general consumer products and,
    for children's products specifically, a Children's Product
    Certificate (CPC) based on THIRD-PARTY testing at a CPSC-accepted
    lab -- the implementing regulations are 16 C.F.R. Part 1110
    (certificate content/recordkeeping) and 16 C.F.R. Part 1107
    (accreditation of third-party conformity-assessment bodies). CPSA
    §15(b), 15 U.S.C. §2064(b), separately requires a manufacturer,
    importer, distributor or retailer to report to CPSC within 24 HOURS
    of obtaining information reasonably supporting the conclusion a
    product contains a defect that could create a substantial risk of
    injury, is non-compliant with an applicable safety rule, or creates
    an unreasonable risk of serious injury or death -- this is a
    POST-HOC, discovered-defect reporting duty, structurally distinct
    from the CPC's PRE-shipment certification duty (see
    `housewaretrade.governor` for how this build models that
    difference). For household TEXTILES specifically -- upholstered
    furniture, mattresses, rugs -- this build cites carefully rather
    than reusing a textile sibling's citation verbatim: mattresses have
    their OWN dedicated CPSC flammability standards, 16 C.F.R. Part 1632
    (cigarette/smolder ignition resistance, 1973) and 16 C.F.R. Part
    1633 (open-flame ignition resistance, 2006) -- DISTINCT standards
    from the Flammable Fabrics Act's (FFA, 15 U.S.C. §1191 et seq.) own
    carpet/rug surface-flammability standards (16 C.F.R. Part 1630,
    large carpets and rugs; Part 1631, small carpets and rugs), which
    this build cites as the applicable FFA-derived standard for
    household rugs specifically (as opposed to wearing apparel, the FFA
    application a textile-wholesale sibling would more likely cite).
    This build's confidence that 16 C.F.R. Part 1633 was promulgated
    under CPSA authority (following the Consumer Product Safety
    Amendments of 1976 extending CPSC's mattress-flammability rulemaking
    beyond the FFA) rather than directly under the FFA itself is
    moderate, not high -- flagged in `docs/business-model.md`
    'Jurisdiction coverage (honest)' for independent verification. This
    build is NOT confident there is a current MANDATORY FEDERAL
    flammability standard for upholstered furniture generally (as
    opposed to mattresses) -- California's Bureau of Household Goods and
    Services Technical Bulletin 117-2013 is a STATE, not federal,
    standard, and is deliberately NOT cited here as if it were a
    national spec-basis. OFAC sanctions programs apply as in every
    sibling's own counterparty-diligence checklist.
  - JPN (the second seeded jurisdiction): 消費生活用製品安全法 (Consumer
    Product Safety Act, Act No. 31 of 1973), administered by 経済産業省
    (the Ministry of Economy, Trade and Industry, METI) with 消費者庁
    (the Consumer Affairs Agency, CAA) as the coordinating consumer-
    safety authority, is Japan's general consumer-product-safety statute
    -- it establishes the PSC mark conformity system (a mandatory
    marking regime for 特定製品 'specified products' and, for the
    highest-risk subset requiring third-party certification, 特別特定製品
    'specially specified products') and a 重重大製品事故報告制度 SERIOUS
    PRODUCT ACCIDENT reporting obligation requiring a business operator
    to report a 重大製品事故 to METI within (this build's understanding)
    10 DAYS of becoming aware of it -- the Japanese structural analog of
    the US CPSA §15(b) 24-hour report, though the exact deadline and
    scope this build cites is of MODERATE (not high) confidence and
    should be independently re-verified before this catalog entry is
    relied on operationally. OFAC等同等制裁プログラム (OFAC-equivalent
    sanctions programs) apply as in every sibling's own counterparty-
    diligence checklist.

  NOT seeded in this R0 (named here for honesty, not fabricated as a
  catalog entry): the EU's General Product Safety Regulation (EU)
  2023/988, which replaced the 2001/95/EC General Product Safety
  Directive and became applicable 13 December 2024, and the Toy Safety
  Directive 2009/48/EC (EN 71 test-standard series, CE marking) for the
  toy subset specifically -- this build has reasonable confidence these
  instruments exist and are named correctly, but is NOT confident enough
  in the precise GPSR/sector-directive scope boundary (which household-
  goods categories fall under the GPSR 'safety net' vs. a sector-specific
  directive) to seed a catalog entry without risking an overclaimed
  spec-basis; see `docs/business-model.md` 'Jurisdiction coverage
  (honest)'.

  The required-evidence set (credit-clearance record, contract/PO,
  sanctions-screening (OFAC/equivalent) record) mirrors the GENERIC
  counterparty-diligence evidence every principal-trading sibling's own
  catalog demands before ANY order proceeds -- it deliberately does NOT
  include a children's-product-certificate or active-recall-status item:
  unlike a jurisdiction-scoped checklist entry, THIS vertical's two
  domain-defining concerns are each evaluated by their own SEPARATE,
  dedicated governor check (see `housewaretrade.governor`), not a
  checklist item.

  Coverage is reported HONESTLY (see `coverage`), the same discipline
  every sibling actor's `facts` namespace uses: a jurisdiction not in
  this table has NO spec-basis, full stop -- the advisor must not
  fabricate one, and the governor holds if it tries.")

(def catalog
  "iso3 -> requirement map. `:required-evidence` is the GENERIC
  counterparty-diligence evidence set (credit-clearance record,
  contract/PO, sanctions-screening record); `:legal-basis` /
  `:owner-authority` / `:provenance` are the G2 citation the governor
  requires before any `:safety/verify` proposal can commit. Deliberately
  does NOT include a children's-product-certificate or active-recall
  checklist item -- those are `housewaretrade.governor`'s own two
  dedicated HARD checks."
  {"USA" {:name "USA"
          :owner-authority "U.S. Consumer Product Safety Commission (CPSC)"
          :legal-basis "Consumer Product Safety Improvement Act of 2008 (CPSIA, Pub. L. 110-314); 15 U.S.C. §1278a (children's product total-lead-content limit, 100 ppm); 16 C.F.R. Part 1307 (phthalates in children's toys and child care articles); 16 C.F.R. Part 1303 (ban of lead-containing paint, >= 90 ppm, on furniture/toys/consumer products); Consumer Product Safety Act §14, 15 U.S.C. §2063, and 16 C.F.R. Parts 1107/1110 (Children's Product Certificate / General Certificate of Conformity, third-party testing and recordkeeping); Consumer Product Safety Act §15(b), 15 U.S.C. §2064(b) (24-hour substantial-product-hazard report to CPSC); 16 C.F.R. Part 1632 and Part 1633 (mattress smolder and open-flame flammability standards); Flammable Fabrics Act, 15 U.S.C. §1191 et seq., 16 C.F.R. Parts 1630/1631 (carpet and rug surface-flammability standards); OFAC sanctions programs"
          :provenance "https://www.cpsc.gov/Regulations-Laws--Standards/Statutes"
          :required-evidence ["credit-clearance record"
                              "contract/PO"
                              "sanctions-screening (OFAC/equivalent) record"]}
   "JPN" {:name "JPN"
          :owner-authority "経済産業省 (METI) / 消費者庁 (Consumer Affairs Agency, CAA)"
          :legal-basis "消費生活用製品安全法 (Consumer Product Safety Act, Act No. 31 of 1973) -- PSCマーク制度(特定製品・特別特定製品の適合性表示); 重大製品事故報告制度(事業者が重大製品事故を知った時からの報告義務、METI); OFAC等同等制裁プログラム"
          :provenance "https://www.meti.go.jp/policy/consumer/seian/shouan/index.html"
          :required-evidence ["credit-clearance record"
                              "contract/PO"
                              "sanctions-screening (OFAC/equivalent) record"]}})

(defn spec-basis
  "The jurisdiction's requirement map, or nil -- nil means NO spec-basis,
  and the governor must hold any proposal that tries to dispatch
  household goods or settle an invoice on it."
  [iso3]
  (get catalog iso3))

(defn coverage
  "Honest coverage report: how many of the requested jurisdictions
  actually have a spec-basis entry. Never report a missing jurisdiction
  as covered."
  ([] (coverage (keys catalog)))
  ([iso3s]
   (let [have (filter catalog iso3s)
         missing (remove catalog iso3s)]
     {:requested (count iso3s)
      :covered (count have)
      :covered-jurisdictions (vec (sort have))
      :missing-jurisdictions (vec (sort missing))
      :note (str "cloud-itonami-isic-4649 R0: " (count catalog)
                 " jurisdictions seeded with an official spec-basis. "
                 "This is a starting catalog, not a survey of all ~194 "
                 "jurisdictions -- extend `housewaretrade.facts/catalog`, "
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
