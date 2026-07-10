(ns housewaretrade.facts-test
  (:require [clojure.test :refer [deftest is]]
            [housewaretrade.facts :as facts]))

(deftest usa-has-a-spec-basis
  (is (some? (facts/spec-basis "USA")))
  (is (string? (:provenance (facts/spec-basis "USA")))))

(deftest both-seeded-jurisdictions-have-required-evidence
  ;; every seeded household-goods-wholesale jurisdiction actually has a
  ;; real required-evidence set reported honestly here
  (doseq [iso3 ["USA" "JPN"]]
    (is (seq (facts/evidence-checklist iso3)) (str iso3 " required-evidence"))))

(deftest unknown-jurisdiction-has-no-fabricated-spec-basis
  (is (nil? (facts/spec-basis "ATL"))))

(deftest coverage-never-reports-a-missing-jurisdiction-as-covered
  (let [report (facts/coverage ["USA" "ATL" "JPN"])]
    (is (= 2 (:covered report)))
    (is (= ["ATL"] (:missing-jurisdictions report)))
    (is (= ["JPN" "USA"] (:covered-jurisdictions report)))))

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
  (doseq [iso3 ["USA" "JPN"]]
    (is (= 3 (count (facts/evidence-checklist iso3))) (str iso3 " checklist length"))))
