(ns housewaretrade.facts-test
  (:require [clojure.test :refer [deftest is]]
            [housewaretrade.facts :as facts]))

(deftest usa-has-a-spec-basis
  (is (some? (facts/spec-basis "USA")))
  (is (string? (:provenance (facts/spec-basis "USA")))))

(deftest mex-has-a-spec-basis
  ;; MEX (Mexico) is the third seeded jurisdiction (added 2026-07) --
  ;; NOM-003-SCFI-2000 (electrical-product safety) and NOM-050-SCFI-2004
  ;; (general commercial-information labeling), both PROFECO-administered
  ;; and verified directly from PROFECO's own official "Marco Jurídico /
  ;; Normas Oficiales Mexicanas" pages via the Internet Archive Wayback
  ;; Machine (see namespace docstring for the full gap/confidence notes).
  (let [mex (facts/spec-basis "MEX")]
    (is (some? mex))
    (is (= "MEX" (:name mex)))
    (is (string? (:provenance mex)))
    (is (re-find #"NOM-003-SCFI-2000" (:legal-basis mex)))
    (is (re-find #"NOM-050-SCFI-2004" (:legal-basis mex)))
    (is (re-find #"PROFECO" (:owner-authority mex)))
    (is (= ["credit-clearance record" "contract/PO" "sanctions-screening (OFAC/equivalent) record"]
           (:required-evidence mex)))))

(deftest all-three-seeded-jurisdictions-have-required-evidence
  ;; every seeded household-goods-wholesale jurisdiction actually has a
  ;; real required-evidence set reported honestly here
  (doseq [iso3 ["USA" "JPN" "MEX"]]
    (is (seq (facts/evidence-checklist iso3)) (str iso3 " required-evidence"))))

(deftest unknown-jurisdiction-has-no-fabricated-spec-basis
  (is (nil? (facts/spec-basis "ATL"))))

(deftest coverage-never-reports-a-missing-jurisdiction-as-covered
  (let [report (facts/coverage ["USA" "ATL" "JPN" "MEX"])]
    (is (= 3 (:covered report)))
    (is (= ["ATL"] (:missing-jurisdictions report)))
    (is (= ["JPN" "MEX" "USA"] (:covered-jurisdictions report)))))

(deftest required-evidence-satisfied-needs-every-item
  (let [all (facts/evidence-checklist "USA")]
    (is (facts/required-evidence-satisfied? "USA" all))
    (is (not (facts/required-evidence-satisfied? "USA" (rest all))))
    (is (not (facts/required-evidence-satisfied? "ATL" all)) "no spec-basis -> never satisfied")))

(deftest catalog-does-not-fold-childrens-product-or-recall-into-the-checklist
  ;; the generic evidence checklist stays exactly 3 items (credit,
  ;; contract/PO, sanctions) for every seeded jurisdiction -- a
  ;; children's-product-certificate or active-recall status is never a
  ;; checklist entry, it is its own dedicated governor check (see
  ;; housewaretrade.governor namespace docstring)
  (doseq [iso3 ["USA" "JPN" "MEX"]]
    (is (= 3 (count (facts/evidence-checklist iso3))) (str iso3 " checklist length"))))
